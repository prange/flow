package flow.actor
import flow._
import flow.event.XmlEvent
import akka.actor.ActorRef
import akka.actor.Actor
import scalaz._
import Scalaz._

object OperatorBuilder {

	implicit def ConnectorBuilderSemiGroup : Semigroup[ConnectorBuilder] = semigroup( ( c1, c2 ) ⇒ new ComposedConnectorBuilder( c1, c2 ) )

	def source( id : String ) = new SourceBuilder( id )

	def transform( name : String, f : XmlEvent ⇒ XmlEvent ) : TransformerBuilder = new TransformerBuilder( name, f )

	def filter( name : String, predicate : XmlEvent ⇒ Boolean ) : FilterBuilder = new FilterBuilder( name, predicate )

	def sink( name : String, f : XmlEvent ⇒ Unit ) : SinkBuilder = new SinkBuilder( name, f )
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
	lazy val operator = {
		val inputRouter : PartialFunction[Any, XmlEvent] = {
			case OperatorInput( _, e : XmlEvent ) ⇒ e
		}

		val outputRouter : XmlEvent ⇒ List[OperatorOutput[XmlEvent]] = e ⇒ List( OperatorOutput( id+".out", e ) )
		new Operator( id, inputRouter, outputRouter, new TransformerState( f ) )
	}
	val out = OutputBuilder( this, OutputPortId( id+".out" ) )
	val in = InputBuilder( this, InputPortId( id+".in" ) )
	def update( context : Context ) = context + PortBinding( InputPortId( id+".in" ), OperatorId( id ) ) + operator
}

class FilterBuilder( id : String, pred : XmlEvent ⇒ Boolean ) extends OperatorBuilder {
	lazy val operator = {
		val inputRouter : PartialFunction[Any, XmlEvent] = {
			case OperatorInput( _, e : XmlEvent ) ⇒ e
		}

		val outputRouter : Either[XmlEvent,XmlEvent] ⇒ List[OperatorOutput[XmlEvent]] = e ⇒ e.fold(filtered=>OperatorOutput(id+".filtered",filtered),unfiltered=>OperatorOutput(id+".unfiltered",unfiltered)).pure[List]
		new Operator( id, inputRouter, outputRouter, new FilterState( pred ) )
	}
	val filtered = OutputBuilder( this, OutputPortId( id+".filtered" ) )
	val unfiltered = OutputBuilder( this, OutputPortId( id+".unfiltered" ) )
	val in = InputBuilder( this, InputPortId( id+".in" ) )
	def update( context : Context ) = context + PortBinding( InputPortId( id+".in" ), OperatorId( id ) ) + operator
}

class SinkBuilder( id : String, f : XmlEvent ⇒ Unit ) extends OperatorBuilder {
		lazy val operator = {
		val inputRouter : PartialFunction[Any, XmlEvent] = {
			case OperatorInput( _, e : XmlEvent ) ⇒ e
		}

		val outputRouter : Unit ⇒ List[OperatorOutput[Unit]] = e ⇒ List()
		new Operator( id, inputRouter, outputRouter, new SinkState( f ) )
	}
	val out = OutputBuilder( this, OutputPortId( id+".out" ) )
	val in = InputBuilder( this, InputPortId( id+".in" ) )
	def update( context : Context ) = context + PortBinding( InputPortId( id+".in" ), OperatorId( id ) ) + operator
}


