package flow.actor
import flow._
import flow.event.XmlEvent
import akka.actor.ActorRef
import akka.actor.Actor
import scalaz._
import Scalaz._
import Routers._
import flow.dataprovider.WidgetDashboard
import scalaz.effects.IO

object OperatorBuilder {

	implicit def ConnectorBuilderSemiGroup : Semigroup[ConnectorBuilder] = semigroup( ( c1, c2 ) ⇒ new ComposedConnectorBuilder( c1, c2 ) )

	def source( id : String ) = new SourceBuilder( id )

	def transform[I, O]( name : String, f : I ⇒ O, h : InputHandler[I] ) : TransformerBuilder[I, O] = new TransformerBuilder( name, f, h )

	def multtransform[I, O]( name : String, f : I ⇒ List[O], h : InputHandler[I] ) : MultTransformerBuilder[I, O] = new MultTransformerBuilder( name, f, h )

	def filter[T]( name : String, filter : T ⇒ Either[T, T], h : InputHandler[T] ) : FilterBuilder[T] = new FilterBuilder( name, filter, h )

	def sink( name : String, f : Any ⇒ Unit ) : SinkBuilder = new SinkBuilder( name, f )
	
	def publishingSink(name:String, f:Any=>IO[List[String]])  = new PublishingSinkBuilder(name,f)
}

trait ConnectorBuilder {
	def update( context : Context ) : Context
	def &( other : ConnectorBuilder ) = new ComposedConnectorBuilder( this, other )
}

class LeafConnectorBuilder( input : InputBuilder, output : OutputBuilder ) extends ConnectorBuilder {
	def update( context : Context ) : Context = {
		( input.update _ andThen output.update _ )( context ) + ( output.output --> input.input )
	}
}

class ComposedConnectorBuilder( one : ConnectorBuilder, other : ConnectorBuilder ) extends ConnectorBuilder {
	def update( context : Context ) : Context = {
		( one.update _ andThen other.update _ )( context )
	}
}

case class InputBuilder( operatorBuilder : OperatorBuilder, input : InputPortId ) {
	def update( context : Context ) : Context = operatorBuilder.update( context )
}

case class OutputBuilder( operatorBuilder : OperatorBuilder, output : OutputPortId ) {
	def update( context : Context ) : Context = operatorBuilder.update( context )
	def -->( input : InputBuilder ) : ConnectorBuilder = new LeafConnectorBuilder( input, this )
	def --[I, O]( transformer : TransformerBuilder[I, O] ) = new {
		def -->( input : InputBuilder ) : ConnectorBuilder = -->( transformer.in ) & ( transformer.out --> input )
	}
}

trait OperatorBuilder {
	def update( context : Context ) : Context
}

class SourceBuilder( id : String ) extends OperatorBuilder {
	val out = OutputBuilder( this, OutputPortId( id ) )
	def update( context : Context ) : Context = context

}

class TransformerBuilder[I, O]( id : String, f : I ⇒ O, h : InputHandler[I] ) extends OperatorBuilder {

	lazy val operator =
		new Operator( id, h, oneOutputRouter( ( id+".out" ) ), new TransformerState( f ) )

	val out = OutputBuilder( this, OutputPortId( id+".out" ) )
	val in = InputBuilder( this, InputPortId( id+".in" ) )
	def update( context : Context ) = context + PortBinding( InputPortId( id+".in" ), OperatorId( id ) ) + operator
}

class MultTransformerBuilder[I, O]( id : String, f : I ⇒ List[O], h : InputHandler[I] ) extends OperatorBuilder {

	lazy val operator =
		new Operator( id, h, listOneOutputRouter( ( id+".out" ) ), new TransformerState( f ) )

	val out = OutputBuilder( this, OutputPortId( id+".out" ) )
	val in = InputBuilder( this, InputPortId( id+".in" ) )
	def update( context : Context ) = context + PortBinding( InputPortId( id+".in" ), OperatorId( id ) ) + operator
}

class FilterBuilder[T]( id : String, filter : T ⇒ Either[T, T], h : InputHandler[T] ) extends OperatorBuilder {
	lazy val operator =
		new Operator( id, h, eitherOutputRouter( id+".filtered", id+".unfiltered" ), new FilterState( filter ) )

	val filtered = OutputBuilder( this, OutputPortId( id+".filtered" ) )
	val unfiltered = OutputBuilder( this, OutputPortId( id+".unfiltered" ) )
	val in = InputBuilder( this, InputPortId( id+".in" ) )
	def update( context : Context ) = context + PortBinding( InputPortId( id+".in" ), OperatorId( id ) ) + operator
}

class SinkBuilder( id : String, f : Any ⇒ Unit ) extends OperatorBuilder {
	lazy val operator = {
		import Routers._
		val inputRouter = handle( {
			case OperatorInput( _, e : Any ) ⇒ e
		} )

		val outputRouter : Unit ⇒ List[OperatorOutput] = e ⇒ List()
		new Operator( id, inputRouter, outputRouter, new SinkState( f ) )
	}
	val in = InputBuilder( this, InputPortId( id+".in" ) )
	def update( context : Context ) = context + PortBinding( InputPortId( id+".in" ), OperatorId( id ) ) + operator
}

class PublishingSinkBuilder( id : String, f : Any ⇒ IO[List[String]] ) extends OperatorBuilder {
	lazy val operatorbuilder = {
		import Routers._
		val inputRouter = handle( {
			case OperatorInput( _, e : Any ) ⇒ e
		} )

		val outputRouter : Unit ⇒ List[OperatorOutput] = e ⇒ List()
		new Builder(){
			def apply(context:Context)= new Operator( id, inputRouter, outputRouter, new PublisherState( f ,context.publishers(id)) )
		}
	}
	val in = InputBuilder( this, InputPortId( id+".in" ) )
	def update( context : Context ) = context + PortBinding( InputPortId( id+".in" ), OperatorId( id ) ) + operatorbuilder
}

case class PublisherState( f : Any ⇒ IO[List[String]], publisher : Publisher ) extends OperatorState[Any, Unit] {
	def apply( e : Any ) = {
		val eff = for{
			data<-f(e);
			_<-publisher.publish(data)
		}yield()
		( eff.unsafePerformIO, this )
	}
}


