package flow.data
import org.joda.time.DateTime

case class Event( eventTime : DateTime, values : Map[String, String] ) extends Ordered[Event] {
	def withProperty( pname : String, pvalue : String ) = new Event( eventTime, values + ( pname -> pvalue ) )
	def compare( that : Event ) = this.eventTime.compareTo( that.eventTime )
}

object Event {
	def apply( eventtime : DateTime ) = new Event( eventtime, Map.empty )
}