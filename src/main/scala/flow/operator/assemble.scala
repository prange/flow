package flow.operator
import flow._
import flow.event._
import flow.service.Store
import flow.service.Datastore
import flow.event.EventChain
import flow.event.EventChain
import flow.actor.OperatorState
import scalaz._
import Scalaz._
import flow.actor.OutputBuilder
import flow.actor.OperatorBuilder
import flow.actor.InputBuilder
import flow.actor.OperatorOutput
import flow.actor.OperatorInput
import flow.actor.Operator
import flow.actor.Context
import flow.actor.InputPortId
import flow.actor.PortBinding
import flow.actor.OutputPortId
import flow.actor.FilterState
import flow.actor.OperatorId

object AssembleBuilder {

	def assemble( id : String, definitions : List[ProcessDefinition], idExtractor : XmlEvent ⇒ String ) = new InMemoryAssembleStateBuilder( id, definitions, idExtractor )

}

case class ProcessDefinition( name : String, from : XmlEvent ⇒ Boolean, to : XmlEvent ⇒ Boolean )

case class InMemoryAssembleState( definitions : List[ProcessDefinition], activeProcesses : Map[String, EventChain], idExtractor : XmlEvent ⇒ String ) extends OperatorState[XmlEvent, List[ProcessEvent]] {

	val addToList : ProcessEvent ⇒ List[ProcessEvent] ⇒ List[ProcessEvent] = t ⇒ list ⇒ t :: list
	val addToMap : ( String, EventChain ) ⇒ Map[String, EventChain] ⇒ Map[String, EventChain] = ( k, v ) ⇒ m ⇒ m + ( k -> v )
	val removeFromMap : String ⇒ Map[String, EventChain] ⇒ Map[String, EventChain] = k ⇒ m ⇒ m - k

	def update( newActiceProcesses : Map[String, EventChain] ) = InMemoryAssembleState( definitions, newActiceProcesses, idExtractor )

	def apply( event : XmlEvent ) = {

		def createOnly( id : String, event : XmlEvent ) = {
			val chain = EventChain.from( id, event )
			( addToList( ProcessStartedEvent( event.eventTime, chain ) ), addToMap( id, chain ) )
		}

		def advanceOnly( id : String, event : XmlEvent, currentChain : EventChain ) = {
			val newChain = event :: currentChain
			( addToList( ProcessAdvancedEvent( event.eventTime, newChain ) ), addToMap( id, newChain ) )
		}

		def advanceAndEnd( id : String, event : XmlEvent, currentChain : EventChain ) = {
			val newChain = event :: currentChain
			val time = event.eventTime
			( addToList( ProcessAdvancedEvent( time, newChain ) ) andThen addToList( ProcessEndedEvent( time, newChain ) ), removeFromMap( id ) )
		}

		val ( events, newActiveProcesses ) = definitions.foldLeft( ( List[ProcessEvent](), activeProcesses ) ) { ( prev, definition ) ⇒
			val id = definition.name+"."+idExtractor( event )
			val currentProcess = activeProcesses.get( id )
			val doStart = definition.from( event )
			val doEnd = definition.to( event )

			val ( addList, addMap ) = ( doStart, doEnd, currentProcess ) match {
				case ( true, _, None ) ⇒ createOnly( id, event )
				case ( false, false, Some( chain ) ) ⇒ advanceOnly( id, event, chain )
				case ( _, true, Some( chain ) ) ⇒ advanceAndEnd( id, event, chain )
				case _ ⇒ { val l = ( identity[List[ProcessEvent]] _, identity[Map[String, EventChain]] _ ); l }
			}

			( addList( prev._1 ), addMap( prev._2 ) )

		}

		( events, update( newActiveProcesses ) )
	}

}

class InMemoryAssembleStateBuilder( id : String, definitions : List[ProcessDefinition], idExtractor : XmlEvent ⇒ String ) extends OperatorBuilder {
	lazy val operator = {
		val inputRouter : PartialFunction[Any, XmlEvent] = {
			case OperatorInput( _, e : XmlEvent ) ⇒ e
		}

		val outputRouter : List[ProcessEvent] ⇒ List[OperatorOutput[ProcessEvent]] = l ⇒ l.foldLeft( List[OperatorOutput[ProcessEvent]]() ) { ( list, event ) ⇒
			event match {
				case ps : ProcessStartedEvent ⇒ OperatorOutput( id+".started", ps ) :: list
				case pa : ProcessAdvancedEvent ⇒ OperatorOutput( id+".advanced", pa ) :: list
				case pe : ProcessEndedEvent ⇒ OperatorOutput( id+".ended", pe ) :: list
			}
		}
		new Operator( id, inputRouter, outputRouter, new InMemoryAssembleState( definitions, Map.empty, idExtractor ) )
	}
	val started = OutputBuilder( this, OutputPortId( id+".started" ) )
	val advanced = OutputBuilder( this, OutputPortId( id+".advanced" ) )
	val ended = OutputBuilder( this, OutputPortId( id+".ended" ) )
	val in = InputBuilder( this, InputPortId( id+".in" ) )
	def update( context : Context ) = context + PortBinding( InputPortId( id+".in" ), OperatorId( id ) ) + operator
}


