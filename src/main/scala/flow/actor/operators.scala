package flow.actor
import flow._
import akka.actor.Actor
import akka.actor.ActorRef
import org.joda.time.DateTime
import akka.event.EventHandler
import scalaz._
import Scalaz._
import effects._
import javax.swing.InputMap
import flow.event.XmlEvent
import scala.xml.persistent.SetStorage

case class InputPortId( id : String )

case class OutputPortId( id : String ) {
	def -->( in : InputPortId ) = Wire( this, in )
}

case class OperatorId( id : String )

case class Wire( from : OutputPortId, to : InputPortId )

case class PortBinding( port : InputPortId, operator : OperatorId )

trait Either3[A, B, C] {
	def fold[T]( one : A ⇒ T, two : B ⇒ T, three : C ⇒ T ) : T
}

case class OneOfThree[A, B, C]( a : A ) extends Either3[A, B, C] {
	def fold[T]( one : A ⇒ T, two : B ⇒ T, three : C ⇒ T ) : T = one( a )
}

case class TwoOfThree[A, B, C]( b : B ) extends Either3[A, B, C] {
	def fold[T]( one : A ⇒ T, two : B ⇒ T, three : C ⇒ T ) : T = two( b )
}

case class ThreeOfThree[A, B, C]( c : C ) extends Either3[A, B, C] {
	def fold[T]( one : A ⇒ T, two : B ⇒ T, three : C ⇒ T ) : T = three( c )
}

case class OperatorOutput( from : String, message : Any ) {
	def toInput( to : String ) = OperatorInput( to, message )
}

case class OperatorInput( to : String, message : Any )

trait OperatorState[I, O] extends ( I ⇒ ( O, OperatorState[I, O] ) )

object Routers {

	def oneInputRouter[I] : PartialFunction[Any, I] = {
		case OperatorInput( _, msg : I ) ⇒ msg
	}

	def twoInputRouter[L, R]( leftId : String, rightId : String ) : PartialFunction[Any, Either[L, R]] = {
		case OperatorInput( leftId, msg : L ) ⇒ Left( msg )
		case OperatorInput( rightId, msg : R ) ⇒ Right( msg )
	}

	def oneOutputRouter[O]( id : String ) : O ⇒ List[OperatorOutput] =
		o ⇒ List( OperatorOutput( id, o ) )

	def optionOutputRouter[O]( id : String ) : Option[O] ⇒ List[OperatorOutput] =
		opt ⇒ opt.map( OperatorOutput( id, _ ) ).toList

	def eitherOutputRouter[L, R]( leftId : String, rightId : String ) : Either[L, R] ⇒ List[OperatorOutput] =
		eith ⇒ List( eith.fold( l ⇒ OperatorOutput( leftId, l ), r ⇒ OperatorOutput( rightId, r ) ) )

	def listOneOutputRouter[O]( id : String ) : List[O] ⇒ List[OperatorOutput] =
		list ⇒ list.map( OperatorOutput( id, _ ) )

	def listEitherOutputRouter[L, R]( leftId : String, rightId : String ) : List[Either[L, R]] ⇒ List[OperatorOutput] =
		list ⇒ list.map( eith ⇒ eith.fold( l ⇒ OperatorOutput( leftId, l ), r ⇒ OperatorOutput( rightId, r ) ) )

	def listEither3OutputRouter[A, B, C]( oneId : String, twoId : String, threeId : String ) : List[Either3[A, B, C]] ⇒ List[OperatorOutput] =
		list ⇒ list.map( e3 ⇒ e3.fold( o ⇒ OperatorOutput( oneId, o ), t ⇒ OperatorOutput( twoId, t ), t ⇒ OperatorOutput( threeId, t ) ) )
}

class Operator[I, O]( ident : String, inputRouter : PartialFunction[OperatorInput, I], outputRouter : O ⇒ List[OperatorOutput], s : OperatorState[I, O] ) { self ⇒
	def id = ident
	type HandleReply = ActorRef ⇒ IO[Unit]
	private var state : OperatorState[I, O] = s

	private val updateState : OperatorState[I, O] ⇒ ActorRef ⇒ IO[Unit] = s ⇒ a ⇒ io { state = s }
	private val sendReply : List[_] ⇒ ActorRef ⇒ IO[Unit] = l ⇒ ref ⇒ io { l.foreach( ref reply _ ) }
	private val update : ( O, OperatorState[I, O] ) ⇒ ActorRef ⇒ IO[Unit] = { ( o, s ) ⇒
		a ⇒
			for {
				_ ← updateState( s )( a );
				_ ← sendReply( outputRouter( o ) )( a )
			} yield ()
	}

	def handle : PartialFunction[OperatorInput, HandleReply] = {
		inputRouter andThen state andThen { t ⇒ update( t._1, t._2 ) } orElse {
			case u @ _ ⇒ { ( a : ActorRef ) ⇒ io {} }
		}
	}
}

class FilterState( pred : XmlEvent ⇒ Boolean ) extends OperatorState[XmlEvent, Either[XmlEvent, XmlEvent]] {
	def apply( e : XmlEvent ) = {
		if ( pred( e ) )
			( Left( e ), this )
		else
			( Right( e ), this )
	}
}

case class TransformerState( f : XmlEvent ⇒ XmlEvent ) extends OperatorState[XmlEvent, XmlEvent] {
	def apply( e : XmlEvent ) = ( f( e ), this )
}

case class SinkState( f : Any ⇒ Unit ) extends OperatorState[Any, Unit] {
	def apply( e : Any ) = ( f( e ), this )
}

