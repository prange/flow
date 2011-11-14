package flow.operator
import flow._
import flow.event._
import flow.service.Store
import flow.service.Datastore
import flow.event.EventChain
import flow.event.EventChain
import flow.actor.OperatorState

object Assemble {

}

trait EventChainCut {
	type Exists[T] = Option[T]
	def apply( event : XmlEvent, eventChain : Exists[EventChain] ) : ( Option[ProcessStartedEvent], Option[ProcessAdvancedEvent], Option[ProcessEndedEvent] )
}

case class CutBefore( idExtractor : XmlEvent ⇒ String, predicate : Predicate[XmlEvent] ) extends EventChainCut {

	def apply( event : XmlEvent, eventChain : Exists[EventChain] ) : ( Option[ProcessStartedEvent], Option[ProcessAdvancedEvent], Option[ProcessEndedEvent] ) = ( eventChain, predicate( event ) ) match {
		case ( None, _ ) ⇒ ( Some( ProcessStartedEvent( EventChain.from(idExtractor, event ) ) ), Some( ProcessAdvancedEvent( EventChain.from( event ) ) ), None )
		case ( Some( chain ), false ) ⇒ ( None, Some( ProcessAdvancedEvent( event :: chain ) ), None )
		case ( Some( chain ), true ) ⇒ ( Some( ProcessStartedEvent( EventChain.from( idExtractor,event ) ) ), Some( ProcessAdvancedEvent( EventChain.from( event ) ) ), Some( ProcessEndedEvent( chain ) ) )
	}

}

case class CutAfter(idExtractor : XmlEvent ⇒ String, predicate : Predicate[XmlEvent] ) extends EventChainCut {

	def apply( event : XmlEvent, eventChain : Exists[EventChain] ) : ( Option[ProcessStartedEvent], Option[ProcessAdvancedEvent], Option[ProcessEndedEvent] ) = ( eventChain, predicate( event ) ) match {
		case ( None, _ ) ⇒ ( Some( ProcessStartedEvent( EventChain.from( event ) ) ), Some( ProcessAdvancedEvent( EventChain.from( event ) ) ), None )
		case ( Some( chain ), false ) ⇒ ( None, Some( ProcessAdvancedEvent( event :: chain ) ), None )
		case ( Some( chain ), true ) ⇒ ( None, Some( ProcessAdvancedEvent( event :: chain ) ), Some( ProcessEndedEvent( event :: chain ) ) )
	}

}

trait ProcessEvent
case class ProcessStartedEvent( event : EventChain ) extends ProcessEvent
case class ProcessAdvancedEvent( event : EventChain ) extends ProcessEvent
case class ProcessEndedEvent( event : EventChain ) extends ProcessEvent

case class AssembleOutput( newProcess : Option[ProcessStartedEvent], advanced : Option[ProcessAdvancedEvent], ended : Option[ProcessEndedEvent] ) {
	def get = ( newProcess, advanced, ended )
	def update( activeProcesses : Map[String, EventChain] ) : Map[String, EventChain] = {

	}
}

case class InMemoryAssembleState( cutter : EventChainCut, activeProcesses : Map[String, EventChain], idExtractor : XmlEvent ⇒ String ) extends OperatorState[XmlEvent, ( Option[ProcessStartedEvent], Option[ProcessAdvancedEvent], Option[ProcessEndedEvent] )] {

	def apply( event : XmlEvent ) = {

	}

}