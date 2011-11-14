package flow.event
import flow._
import org.joda.time.DateTime
import com.codecommit.antixml._

case class XmlEvent( eventTime : DateTime, eventType : String, data : Group[Elem] ) {
	def select( property : Selector[Elem] ) = data \ property \ text headOption
}

case class EventChain( events : List[XmlEvent], data : Group[Elem] ) {
	def ::( event : XmlEvent ) = EventChain( event :: events, data )
	def select( property : Selector[Elem] ) = data \ property \ text headOption
}

trait TimerEvent{
	val time:DateTime
}



case class SecondTimer(time:DateTime) extends TimerEvent
case class MinuteTimer(time:DateTime) extends TimerEvent
case class HourTimer(time:DateTime) extends TimerEvent
case class DayTimer(time:DateTime) extends TimerEvent
case class WeekTimer(time:DateTime) extends TimerEvent
case class MonthTimer(time:DateTime) extends TimerEvent

object EventChain {

	def from( event : XmlEvent ) = EventChain( event :: Nil, Group() )
}



