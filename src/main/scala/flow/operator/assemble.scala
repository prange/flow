package flow.operator
import flow._
import scalaz._
import Scalaz._
import event._
import flow.actor._
import InMemoryAssembler._

case class ProcessDefinition( name : String, from : ObservationEvent ⇒ Boolean, to : ObservationEvent ⇒ Boolean )
case class InMemoryAssemblerState( activeProcesses : Map[String, EventChain], definitions : List[ProcessDefinition], idExtractor : ObservationEvent ⇒ String )

object InMemoryAssembler {
	type E3 = Either[ProcessStartedEvent, Either[ProcessAdvancedEvent, ProcessEndedEvent]]

	def convert( e3s : List[E3] ) : ( List[ProcessStartedEvent], List[ProcessAdvancedEvent], List[ProcessEndedEvent] ) = {
		e3s.foldLeft( ( List[ProcessStartedEvent](), List[ProcessAdvancedEvent](), List[ProcessEndedEvent]() ) ) { ( triple, e3 ) ⇒
			e3.fold(
				first ⇒ ( first :: triple._1, triple._2, triple._3 ),
				either ⇒ either.fold(
					second ⇒ ( triple._1, second :: triple._2, triple._3 ),
					third ⇒ ( triple._1, triple._2, third :: triple._3 ) ) )
		}
	}

	def f : ObservationEvent ⇒ InMemoryAssemblerState ⇒ ( ( List[ProcessStartedEvent], List[ProcessAdvancedEvent], List[ProcessEndedEvent] ), InMemoryAssemblerState ) = event ⇒ state ⇒ {
		val addToList : E3 ⇒ List[E3] ⇒ List[E3] = t ⇒ list ⇒ t :: list
		val addToMap : ( String, EventChain ) ⇒ Map[String, EventChain] ⇒ Map[String, EventChain] = ( k, v ) ⇒ m ⇒ m + ( k -> v )
		val removeFromMap : String ⇒ Map[String, EventChain] ⇒ Map[String, EventChain] = k ⇒ m ⇒ m - k

		def update( newActiceProcesses : Map[String, EventChain] ) = InMemoryAssemblerState( newActiceProcesses, state.definitions, state.idExtractor )

		def createOnly( id : String, name : String, event : ObservationEvent ) = {
			val chain = EventChain.from( id, name, event )
			( addToList( Left( ProcessStartedEvent( event.eventTime, chain ) ) ), addToMap( id, chain ) )
		}

		def advanceOnly( id : String, event : ObservationEvent, currentChain : EventChain ) = {
			val newChain = event :: currentChain
			( addToList( Right( Left( ProcessAdvancedEvent( event.eventTime, newChain ) ) ) ), addToMap( id, newChain ) )
		}

		def advanceAndEnd( id : String, event : ObservationEvent, currentChain : EventChain ) = {
			val newChain = event :: currentChain
			val time = event.eventTime
			( addToList( Right( Left( ProcessAdvancedEvent( time, newChain ) ) ) ) andThen addToList( Right( Right( ProcessEndedEvent( time, newChain ) ) ) ), removeFromMap( id ) )
		}

		val ( events, newActiveProcesses ) = state.definitions.foldLeft( ( List[E3](), state.activeProcesses ) ) { ( prev, definition ) ⇒
			val id = definition.name+"."+state.idExtractor( event )
			val currentProcess = state.activeProcesses.get( id )
			val doStart = definition.from( event )
			val doEnd = definition.to( event )

			val ( addList, addMap ) = ( doStart, doEnd, currentProcess ) match {
				case ( true, _, None ) ⇒ createOnly( id, definition.name, event )
				case ( false, false, Some( chain ) ) ⇒ advanceOnly( id, event, chain )
				case ( _, true, Some( chain ) ) ⇒ advanceAndEnd( id, event, chain )
				case _ ⇒ ( identity[List[E3]] _, identity[Map[String, EventChain]] _ )
			}

			( addList( prev._1 ), addMap( prev._2 ) )

		}

		val r = ( convert( events ), update( newActiveProcesses ) )
		r
	}

}

class InMemoryAssembler( id : String, definitions : List[ProcessDefinition], idExtractor : ObservationEvent ⇒ String ) extends MealyOperator[( List[ProcessStartedEvent], List[ProcessAdvancedEvent], List[ProcessEndedEvent] ), InMemoryAssemblerState] {
	val in = mealyInput(id.toOpId,"in".toPortId, f )
	val started = output[ProcessStartedEvent](id.toOpId,"started".toPortId)
	val advanced = output[ProcessAdvancedEvent](id.toOpId,"advanced".toPortId)
	val ended = output[ProcessEndedEvent](id.toOpId,"ended".toPortId)
	val * = in ~> (started and advanced and ended)
}

trait InMemoryAssemblerBuilder{
	def assembler(id:String,definitions : List[ProcessDefinition], idExtractor : ObservationEvent ⇒ String ) = new InMemoryAssembler(id,definitions,idExtractor)
}




