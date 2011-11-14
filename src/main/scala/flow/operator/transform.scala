package flow.operator
import com.codecommit.antixml.Elem
import com.codecommit.antixml.Attributes
import com.codecommit.antixml.Group
import com.codecommit.antixml.Text
import Transform._
import flow.Time._
import flow.event.XmlEvent

object Transform {
	def elem( name : String, value : String ) : Elem = Elem( None, name, Attributes(), Map(), Group( Text( value ) ) )
}


case class WeekdayEnricher() extends ( XmlEvent ⇒ XmlEvent ) {

	def apply( event : XmlEvent ) = {
		XmlEvent( event.eventTime,event.eventType, event.data.toZipper.:+( elem( "weekday", event.eventTime.getDayOfWeek().toString ) ) )
	}
}

case class MonthEnricher() extends ( XmlEvent ⇒ XmlEvent ) {
	def apply( event : XmlEvent ) = {
		XmlEvent( event.eventTime,event.eventType, event.data.toZipper.:+( elem( "month", event.eventTime.getMonthOfYear().toString ) ) )
	}
}