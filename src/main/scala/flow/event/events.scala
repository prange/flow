package flow.event
import org.joda.time.DateTime
import org.joda.time.Interval
import com.codecommit.antixml._
import flow.businessrules._
import flow.statistics.Bucket
import flow._
import scalaz.Scalaz._
import scalaz._
import com.codecommit.antixml.Zipper

object Events {

}

case class XmlEvent( eventTime : DateTime, eventType : String, data : Elem ) {
	def select( select : Elem ⇒ Group[Elem] ) = select( data ) \ text headOption

	override def toString = "XmlEvent time:%s, type:%s" format ( eventTime, eventType )
}

trait ValuedEvent {
	val values : Map[String, String]
	def get( property : String ) = values.getOrElse( property, "<unknown>" )
}

case class EPC( value : String )

trait EpcisEvent extends ValuedEvent
case class ObjectEvent( eventTime : DateTime, epcList : List[EPC], values : Map[String, String] ) extends EpcisEvent
case class AggregationEvent( eventTime : DateTime, parent : EPC, childEPCs : List[EPC], values : Map[String, String] ) extends EpcisEvent

case class ObservationEvent( eventTime : DateTime, values : Map[String, String] ) extends ValuedEvent

object ObservationEvent {

	def apply( eventTime : DateTime ):ObservationEvent = ObservationEvent( eventTime, Map() )

}

case class EventChain( id : String, events : NonEmptyList[ObservationEvent], data : Map[String, String] ) {
	def ::( event : ObservationEvent ) = EventChain( id, event <:: events, data )
	def select( property : String ) = data.get( property )

	def update( f : Map[String, String] ⇒ Map[String, String] ) = new EventChain( id, events, f( data ) )

	def interval = if ( events.tail.size == 0 ) new Interval( events.head.eventTime.getMillis(), events.head.eventTime.getMillis() + 1 ) else new Interval( events.head.eventTime, events.tail.last.eventTime )

	override def toString = "EventChain id: %s" format ( id )
}

object EventChain {

	def from( id : String, event : ObservationEvent ) = EventChain( id, event.wrapNel, Map.empty )
}

trait TimerEvent {
	val time : DateTime
}

case class SecondTimer( time : DateTime ) extends TimerEvent
case class MinuteTimer( time : DateTime ) extends TimerEvent
case class HourTimer( time : DateTime ) extends TimerEvent
case class DayTimer( time : DateTime ) extends TimerEvent

trait ProcessEvent
case class ProcessStartedEvent( timstamp : DateTime, eventchain : EventChain ) extends ProcessEvent
case class ProcessAdvancedEvent( timestamp : DateTime, eventchain : EventChain ) extends ProcessEvent
case class ProcessEndedEvent( timestamp : DateTime, eventchain : EventChain ) extends ProcessEvent

case class UpdatedHistogramEvent( histogram : List[Bucket] )
case class BusinessRuleViolatedEvent( rule : BusinessRule, process : ProcessAdvancedEvent )
case class PredictedViolationEvent( violations : List[( String, Double )], process : ProcessAdvancedEvent )


