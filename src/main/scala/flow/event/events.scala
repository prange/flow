package flow.event
import flow._
import org.joda.time.DateTime
import com.codecommit.antixml._
import scalaz._
import Scalaz._
import org.joda.time.Interval

case class XmlEvent( eventTime : DateTime, eventType : String, data : Group[Elem] ) {
	def select( select :Group[Elem] =>  Group[Elem] ) = select(data) \ text headOption
	
	override def toString = "XmlEvent time:%s, type:%s" format( eventTime, eventType)
}

case class EventChain( id : String, events : NonEmptyList[XmlEvent], data : Group[Elem] ) {
	def ::( event : XmlEvent ) = EventChain( id, event <:: events, data )
	def select( property : Selector[Elem] ) = data \ property \ text headOption
	def interval = if ( events.tail.size == 0 ) new Interval( events.head.eventTime.getMillis(), events.head.eventTime.getMillis() + 1 ) else ( events.head.eventTime, events.tail.last.eventTime )
	
	override def toString = "EventChain id: %s" format( id)
}

object EventChain {

	def from( id: String, event : XmlEvent ) = EventChain( id, event.wrapNel, Group() )
}

trait TimerEvent {
	val time : DateTime
}

case class SecondTimer( time : DateTime ) extends TimerEvent
case class MinuteTimer( time : DateTime ) extends TimerEvent
case class HourTimer( time : DateTime ) extends TimerEvent
case class DayTimer( time : DateTime ) extends TimerEvent
case class WeekTimer( time : DateTime ) extends TimerEvent
case class MonthTimer( time : DateTime ) extends TimerEvent

trait ProcessEvent
case class ProcessStartedEvent( timstamp : DateTime, event : EventChain ) extends ProcessEvent
case class ProcessAdvancedEvent( timestamp : DateTime, event : EventChain ) extends ProcessEvent
case class ProcessEndedEvent( timestamp : DateTime, event : EventChain ) extends ProcessEvent



