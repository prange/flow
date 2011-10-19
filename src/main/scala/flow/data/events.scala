package flow.data
import org.joda.time.DateTime


case class Event( eventTime : DateTime, values : Map[String, String] ) {
	def withProperty( pname : String, pvalue : String ) = new Event( eventTime, values + (pname -> pvalue) )
}

object Event {
	def apply( eventtime : DateTime ) = new Event( eventtime, Map.empty )
}