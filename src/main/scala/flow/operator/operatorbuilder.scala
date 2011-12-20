package flow.operator
import scalaz._
import Scalaz._
import effects._
import akka.actor.Actor

trait OperatorBuilder {
	implicit def toOutput[O]( port : OutputPort[O] ) = new SingleOutput[O]( port )
	implicit def OpId( id : String ) = new { def toOpId = OperatorId( id ) }
	implicit def toPortId( id : String ) = new { def toPortId = PortId( id ) }
	def statelessInput[I, O]( operatorId : OperatorId, portId : PortId, f : I ⇒ O )( implicit m : Manifest[I] ) = new StatelessInputPort[I, O]( operatorId, portId, f, m.erasure.asInstanceOf[Class[I]] )
	def mealyInput[I, O, S]( operatorId : OperatorId, portId : PortId, f : I ⇒ S ⇒ ( O, S ) )( implicit m : Manifest[I] ) = new MealyInputPort( operatorId, portId, f, m.erasure.asInstanceOf[Class[I]] )
	def effectInput[I, O, S]( operatorId : OperatorId, portId : PortId, f : I ⇒ S ⇒ IO[O] )( implicit m : Manifest[I] ) = new EffectInputPort( operatorId, portId, f, m.erasure.asInstanceOf[Class[I]] )
	def output[O]( operatorId : OperatorId, portId : PortId ) = new OutputPort[O]( operatorId, portId )
}

case class OperatorDefinition[F, O]( input : InputFunction[F], output : Output[O] )

case class OperatorInput( to : OperatorId, port : PortId, msg : Any )
case class OperatorOutput( from : OperatorId, port : PortId, msg : Any ) {
	def toInput( operatorId : OperatorId, portId : PortId ) = OperatorOutput( operatorId, portId, msg )
}
case class PortId( id : String )
case class OperatorId( id : String )
case class PortWire( fromOperator : OperatorId, fromPort : PortId, toOperator : OperatorId, toPort : PortId )

trait InputFunction[F] { self ⇒
	val func : PartialFunction[Any, F]
	def &( other : InputFunction[F] ) = new InputFunction[F] {
		val func = self.func.orElse( other.func )
	}

	def ~>[O]( output : Output[O] ) = OperatorDefinition[F, O]( self, output )
}

trait InputPort[-I, O, F] extends InputFunction[F] {
	val operatorId : OperatorId
	val portId : PortId
}

case class StatelessInputPort[I, O]( operatorId : OperatorId, portId : PortId, f : I ⇒ O, classOfInput : Class[I] ) extends InputPort[I, O, O] {
	val func : PartialFunction[Any, O] = {
		case OperatorInput( _, port, msg ) if ( port == portId && classOfInput.isInstance( msg ) ) ⇒ f( msg.asInstanceOf[I] )
	}

}

case class MealyInputPort[I, O, S]( operatorId : OperatorId, portId : PortId, f : I ⇒ S ⇒ ( O, S ), classOfInput : Class[I] ) extends InputPort[I, O, S ⇒ ( O, S )] {
	val func : PartialFunction[Any, S ⇒ ( O, S )] = {
		case OperatorInput( _, port, msg ) if ( port == portId && classOfInput.isInstance( msg ) ) ⇒ f( msg.asInstanceOf[I] )
	}
}

case class EffectInputPort[I, O, S]( operatorId : OperatorId, portId : PortId, f : I ⇒ S ⇒ IO[O], classOfInput : Class[I] ) extends InputPort[I, O, S ⇒ IO[O]] {
	val func : PartialFunction[Any, S ⇒ IO[O]] = {
		case OperatorInput( _, port, msg ) if ( port == portId && classOfInput.isInstance( msg ) ) ⇒ f( msg.asInstanceOf[I] )
	}
}

case class OutputPort[-O]( operatorId : OperatorId, portId : PortId ) extends ( O ⇒ OperatorOutput ) {
	def apply( o : O ) = OperatorOutput( operatorId, portId, o )

	def -->( port : InputPort[O, _, _] ) : PortWire = PortWire( this.operatorId, this.portId, port.operatorId, port.portId )
}

trait Output[T] extends ( T ⇒ List[OperatorOutput] ) {
	def apply( t : T ) : List[OperatorOutput]
}

class SingleOutput[A]( out : OutputPort[A] ) extends Output[A] {
	def apply( t : A ) = List( out( t ) )

	def or[B]( other : OutputPort[B] ) = new EitherOutput[A, B]( out, other )

	def and[B]( other : OutputPort[B] ) = new TwoListOutput[A, B]( out, other )

	def list = new OneListOutput[A]( out )

}

class EitherOutput[A, B]( left : OutputPort[A], right : OutputPort[B] ) extends Output[Either[A, B]] {

	def apply( e : Either[A, B] ) = List( e.fold( left, right ) )

}

class OneListOutput[A]( out : OutputPort[A] ) extends Output[( List[A] )] {
	def apply( o : ( List[A] ) ) = {
		o.map( out )
	}
	
	def and[B]( other : OutputPort[B] ) = new TwoListOutput[A, B]( out, other )
}

class TwoListOutput[A, B]( one : OutputPort[A], two : OutputPort[B] ) extends Output[( List[A], List[B] )] {
	def apply( o : ( List[A], List[B] ) ) = {
		o._1.map( one ) ::: o._2.map( two )
	}

	def and[C]( other : OutputPort[C] ) = new ThreeListOutput[A, B, C]( one, two, other )
}

class ThreeListOutput[A, B, C]( one : OutputPort[A], two : OutputPort[B], three : OutputPort[C] ) extends Output[( List[A], List[B], List[C] )] {

	def apply( o : ( List[A], List[B], List[C] ) ) = {
		o._1.map( one ) ::: o._2.map( two ) ::: o._3.map( three )
	}

}

trait Operator extends OperatorBuilder {
	val id : String
	def handle( event : Any ) : Option[IO[List[OperatorOutput]]]
}

trait StatelessOperator[I, O] extends Operator {

	def handle( event : Any ) : Option[IO[List[OperatorOutput]]] = {
		if ( *.input.func.isDefinedAt( event ) )
			Some( io { *.output( *.input.func( event ) ) } )
		else
			None
	}

	val * : OperatorDefinition[O, O]
}

trait MealyOperator[O, S] extends Operator {

	var state = initState

	val initState : S
	val setState : S ⇒ IO[Unit] = newState ⇒ io { state = newState }

	def handle( event : Any ) : Option[IO[List[OperatorOutput]]] = {
		if ( *.input.func.isDefinedAt( event ) ) {
			val ( o, newState ) = *.input.func( event )( state )
			val eff = for {
				_ ← setState( newState )
			} yield ( *.output( o ) )
			Some( eff )
		} else
			None
	}
	val * : OperatorDefinition[S ⇒ ( O, S ), O]
}

trait EffectOperator[O, S] extends Operator {
	val state : S
	def handle( event : Any ) : Option[IO[List[OperatorOutput]]] = {
		if ( *.input.func.isDefinedAt( event ) ) {
			val eff = *.input.func( event )( state ).map( *.output )
			Some( eff )
		} else
			None
	}
	val * : OperatorDefinition[S ⇒ ( IO[O] ), O]
}

trait SinkOperator[I, S] extends Operator {
	val state : S
	def handle( event : Any ) : Option[IO[Unit]] = {
		if ( *.func.isDefinedAt( event ) ) {
			*.func( event )( state ).some
		} else {
			None
		}
	}
	val * : InputFunction[S ⇒ IO[Unit]]
}


