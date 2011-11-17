package flow.actor
import flow._
import flow.event.XmlEvent
import akka.actor.ActorRef
import akka.actor.Actor
import scalaz._
import Scalaz._
import Routers._

object OperatorBuilder {

	implicit def ConnectorBuilderSemiGroup : Semigroup[ConnectorBuilder] = semigroup( ( c1, c2 ) ⇒ new ComposedConnectorBuilder( c1, c2 ) )

	def source( id : String ) = new SourceBuilder( id )

	def transform( name : String, f : XmlEvent ⇒ XmlEvent ) : TransformerBuilder = new TransformerBuilder( name, f )

	def filter( name : String, predicate : XmlEvent ⇒ Boolean ) : FilterBuilder = new FilterBuilder( name, predicate )

	def sink( name : String, f : Any ⇒ Unit ) : SinkBuilder = new SinkBuilder( name, f )
}

trait ConnectorBuilder {
	def update( context : Context ) : Context
	def &(other:ConnectorBuilder) = new ComposedConnectorBuilder(this,other)
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
}

trait OperatorBuilder {
	def update( context : Context ) : Context
}

class SourceBuilder( id : String ) extends OperatorBuilder {
	val out = OutputBuilder( this, OutputPortId( id ) )
	def update( context : Context ) : Context = context

}

class TransformerBuilder( id : String, f : XmlEvent ⇒ XmlEvent ) extends OperatorBuilder {
	
	lazy val operator = 
		new Operator( id, oneInputRouter, oneOutputRouter((id+".out")), new TransformerState( f ) )

	val out = OutputBuilder( this, OutputPortId( id+".out" ) )
	val in = InputBuilder( this, InputPortId( id+".in" ) )
	def update( context : Context ) = context + PortBinding( InputPortId( id+".in" ), OperatorId( id ) ) + operator
}

class FilterBuilder( id : String, pred : XmlEvent ⇒ Boolean ) extends OperatorBuilder {
	lazy val operator = 
		new Operator( id, oneInputRouter, eitherOutputRouter( id+".filtered", id+".unfiltered"), new FilterState( pred ) )
	
	val filtered = OutputBuilder( this, OutputPortId( id+".filtered" ) )
	val unfiltered = OutputBuilder( this, OutputPortId( id+".unfiltered" ) )
	val in = InputBuilder( this, InputPortId( id+".in" ) )
	def update( context : Context ) = context + PortBinding( InputPortId( id+".in" ), OperatorId( id ) ) + operator
}

class SinkBuilder( id : String, f : Any ⇒ Unit ) extends OperatorBuilder {
		lazy val operator = {
		val inputRouter : PartialFunction[Any, Any] = {
			case OperatorInput( _, e : Any ) ⇒ e
		}

		val outputRouter : Unit ⇒ List[OperatorOutput] = e ⇒ List()
		new Operator( id, inputRouter, outputRouter, new SinkState( f ) )
	}
	val out = OutputBuilder( this, OutputPortId( id+".out" ) )
	val in = InputBuilder( this, InputPortId( id+".in" ) )
	def update( context : Context ) = context + PortBinding( InputPortId( id+".in" ), OperatorId( id ) ) + operator
}


