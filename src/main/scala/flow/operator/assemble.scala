package flow.operator
import flow.actor.Context
import flow.actor.Either3
import flow.actor.InputBuilder
import flow.actor.InputPortId
import flow.actor.OneOfThree
import flow.actor.OneOfThree
import flow.actor.Operator
import flow.actor.OperatorBuilder
import flow.actor.OperatorId
import flow.actor.OperatorInput
import flow.actor.OperatorOutput
import flow.actor.OperatorState
import flow.actor.OutputBuilder
import flow.actor.OutputPortId
import flow.actor.PortBinding
import flow.actor.TwoOfThree
import flow.event._
import flow.event.EventChain
import flow.event.EventChain
import flow._
import scalaz.Scalaz._
import flow.actor.TwoOfThree
import flow.actor.ThreeOfThree

object AssembleBuilder {

	def assemble( id : String, definitions : List[ProcessDefinition], idExtractor : ObservationEvent ⇒ String ) = new InMemoryAssembleStateBuilder( id, definitions, idExtractor )

}

case class ProcessDefinition( name : String, from : ObservationEvent ⇒ Boolean, to : ObservationEvent ⇒ Boolean )

case class InMemoryAssembleState( definitions : List[ProcessDefinition], activeProcesses : Map[String, EventChain], idExtractor : ObservationEvent ⇒ String ) extends OperatorState[ObservationEvent, List[Either3[ProcessStartedEvent,ProcessAdvancedEvent,ProcessEndedEvent]]] {

	type E3 = Either3[ProcessStartedEvent,ProcessAdvancedEvent,ProcessEndedEvent]
	
	val addToList : E3 ⇒ List[E3] ⇒ List[E3] = t ⇒ list ⇒ t :: list
	val addToMap : ( String, EventChain ) ⇒ Map[String, EventChain] ⇒ Map[String, EventChain] = ( k, v ) ⇒ m ⇒ m + ( k -> v )
	val removeFromMap : String ⇒ Map[String, EventChain] ⇒ Map[String, EventChain] = k ⇒ m ⇒ m - k

	def update( newActiceProcesses : Map[String, EventChain] ) = InMemoryAssembleState( definitions, newActiceProcesses, idExtractor )

	def apply( event : ObservationEvent ) = {
		
		def createOnly( id : String, event : ObservationEvent ) = {
			val chain = EventChain.from( id, event )
			( addToList( OneOfThree(ProcessStartedEvent( event.eventTime, chain )) ), addToMap( id, chain ) )
		}

		def advanceOnly( id : String, event : ObservationEvent, currentChain : EventChain ) = {
			val newChain = event :: currentChain
			( addToList(TwoOfThree( ProcessAdvancedEvent( event.eventTime, newChain ) )), addToMap( id, newChain ) )
		}

		def advanceAndEnd( id : String, event : ObservationEvent, currentChain : EventChain ) = {
			val newChain = event :: currentChain
			val time = event.eventTime
			( addToList( TwoOfThree(ProcessAdvancedEvent( time, newChain )) ) andThen addToList( ThreeOfThree(ProcessEndedEvent( time, newChain )) ), removeFromMap( id ) )
		}

		val ( events, newActiveProcesses ) = definitions.foldLeft( ( List[E3](), activeProcesses ) ) { ( prev, definition ) ⇒
			val id = definition.name+"."+idExtractor( event )
			val currentProcess = activeProcesses.get( id )
			val doStart = definition.from( event )
			val doEnd = definition.to( event )

			val ( addList, addMap ) = ( doStart, doEnd, currentProcess ) match {
				case ( true, _, None ) ⇒ createOnly( id, event )
				case ( false, false, Some( chain ) ) ⇒ advanceOnly( id, event, chain )
				case ( _, true, Some( chain ) ) ⇒ advanceAndEnd( id, event, chain )
				case _ ⇒  ( identity[List[E3]] _, identity[Map[String, EventChain]] _ )
			}

			( addList( prev._1 ), addMap( prev._2 ) )

		}

		val r =( events, update( newActiveProcesses ) )
		r
	}

}

class InMemoryAssembleStateBuilder( id : String, definitions : List[ProcessDefinition], idExtractor : ObservationEvent ⇒ String ) extends OperatorBuilder {
	import flow.actor.Routers._
	
	lazy val operator = 
		new Operator( id, oneObservationEventInputRouter, listEither3OutputRouter[ProcessStartedEvent,ProcessAdvancedEvent,ProcessEndedEvent](id+".started",id+".advanced",id+".ended"), new InMemoryAssembleState( definitions, Map.empty, idExtractor ) )
	val started = OutputBuilder( this, OutputPortId( id+".started" ) )
	val advanced = OutputBuilder( this, OutputPortId( id+".advanced" ) )
	val ended = OutputBuilder( this, OutputPortId( id+".ended" ) )
	val in = InputBuilder( this, InputPortId( id+".in" ) )
	def update( context : Context ) = context + PortBinding( InputPortId( id+".in" ), OperatorId( id ) ) + operator
}


