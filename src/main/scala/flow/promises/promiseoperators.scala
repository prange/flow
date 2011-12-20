package flow.promises
import scala.collection.JavaConversions._
import flow.event.ProcessAdvancedEvent
import flow.event.PromiseViolatedEvent
import flow.event.EventChain
import scalaz._
import Scalaz._
import flow.event.PromiseViolatedEvent
import flow.event.ValuedEvent
import flow.event.ProcessEvent
import flow.event.ProcessAdvancedEvent
import flow.operator.MealyOperator

object PromiseViolationsBuilder {

	def validator( id : String ) = new PromiseOperator( id )

}

object Promises {

	val promisesList : List[Promise] = List[Promise]( Promise( "B1", "Prosesser skal ikke ta mer enn 12 timer", appliesToPred, promisePred ) )

	def appliesToPred( e : ProcessEvent ) = true
	def promisePred( e : ProcessEvent ) = e.eventchain.interval.toDurationMillis() < 12 * 3600 * 1000
	val handler : ProcessAdvancedEvent ⇒ PromiseViolationsState ⇒ ( ( List[PromiseViolatedEvent], List[ProcessAdvancedEvent] ), PromiseViolationsState ) = e ⇒ state ⇒ {

		//Adds the violation to the event
		def updateWithViolation( pae : ProcessAdvancedEvent, promise : Promise ) = {
			var eventchainData = pae.eventchain.data
			eventchainData += "voilation|"+promise.id -> "true"
			ProcessAdvancedEvent( pae.timestamp, EventChain( pae.eventchain.id, pae.eventchain.processName, pae.eventchain.events, eventchainData ) )
		}

		//Iterates over all defined promises to see which ones that are violated by this event. The result is a 
		//list of violation events
		val output = state.promises.foldLeft( ( List[PromiseViolatedEvent](), List( e ) ) ) { ( t, promise ) ⇒
			if ( promise.appliesToPred( e ) && !promise.promisePred( e ) ) {
				( PromiseViolatedEvent( promise, updateWithViolation( e, promise ) ) :: t._1, t._2 )
			}
			else t
		}
		( output, state )
	}

}

case class Promise( id : String, description : String, appliesToPred : ProcessEvent ⇒ Boolean, promisePred : ProcessEvent ⇒ Boolean ) {
	def isViolated( e : ProcessEvent ) = {
		println( "IS VIOLATED["+id+"]: "+( appliesToPred( e ) && ( !promisePred( e ) ) ) )
		appliesToPred( e ) && ( !promisePred( e ) )
	}
}

case class PromiseViolationsState( promises : List[Promise] )

class PromiseOperator( id : String ) extends MealyOperator[( List[PromiseViolatedEvent], List[ProcessAdvancedEvent] ), PromiseViolationsState] {
	val in = mealyInput( id.toOpId, "in".toPortId, Promises.handler )
	val violations = output[PromiseViolatedEvent]( id.toOpId, "violations".toPortId )
	val enriched = output[ProcessAdvancedEvent]( id.toOpId, "enriched".toPortId )
	def * = in ~> ( violations and enriched )
}



