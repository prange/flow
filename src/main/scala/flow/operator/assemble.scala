package flow.operator
import flow._
import flow.event._
import flow.service.Store
import flow.service.Datastore
import flow.event.EventChain
import flow.event.EventChain

object Assemble {

}

trait EventChainCut {
	def apply( event : XmlEvent, eventChain : EventChain ) : ( Option[ProcessStartedEvent], Option[ProcessAdvancedEvent], Option[ProcessEndedEvent] )
}

case class CutBefore( predicate : Predicate[XmlEvent] ) extends EventChainCut {

	def apply( event : XmlEvent, eventChain : EventChain ) : ( Option[ProcessStartedEvent], Option[ProcessAdvancedEvent], Option[ProcessEndedEvent] ) = ( eventChain.events, predicate( event ) ) match {
		case ( Nil, _ ) ⇒ ( Some( ProcessStartedEvent( EventChain.from( event ) ) ), Some( ProcessAdvancedEvent( EventChain.from( event ) ) ), None )
		case ( xs, false ) ⇒ ( None, Some( ProcessAdvancedEvent( event :: eventChain ) ), None )
		case ( xs, true ) ⇒ ( Some( ProcessStartedEvent( EventChain.from( event ) ) ), Some( ProcessAdvancedEvent( EventChain.from( event ) ) ), Some( ProcessEndedEvent( eventChain ) ) )
	}

}

case class CutAfter( predicate : Predicate[XmlEvent] ) extends EventChainCut {

	def apply( event : XmlEvent, eventChain : EventChain ) : ( Option[ProcessStartedEvent], Option[ProcessAdvancedEvent], Option[ProcessEndedEvent] ) = ( eventChain.events, predicate( event ) ) match {
		case ( Nil, _ ) ⇒ ( Some( ProcessStartedEvent( EventChain.from( event ) ) ), Some( ProcessAdvancedEvent( EventChain.from( event ) ) ), None )
		case ( xs, false ) ⇒ ( None, Some( ProcessAdvancedEvent( event :: eventChain ) ), None )
		case ( xs, true ) ⇒ ( None, Some( ProcessAdvancedEvent( event :: eventChain ) ), Some( ProcessEndedEvent( event :: eventChain ) ) )
	}

}

case class ProcessStartedEvent( event : EventChain )
case class ProcessAdvancedEvent( event : EventChain )
case class ProcessEndedEvent( event : EventChain )