package flow.dataprovider

import org.joda.time.Interval
import org.joda.time.PeriodType
import org.joda.time.Period
import org.joda.time.DateTime
import akka.actor.Actor
import scalaz.Scalaz._
import scalaz.effects._
import flow.event.ProcessEndedEvent
import flow.statistics.Bucket
import flow.event.TimerEvent
import org.joda.time.Days
import flow.actor.OperatorState
import flow.event.UpdatedHistogramEvent
import org.joda.time.Duration
import flow.statistics.BucketCount
import akka.actor.ActorRef
import flow.statistics.Bucket
import flow.Time

case class WidgetId( value : String )
case class Message( time : DateTime, id : String, text : String )
case class WidgetDataUpdate( id : String, action : String, data : Map[String, String] )

trait WidgetData {
	def widgetState[T](id:WidgetId,initState:T) = new WidgetState(id,initState)
	def setSeriesUpdate(map:Map[String,String]) : WidgetId=>WidgetDataUpdate = id=>WidgetDataUpdate( id.value, "setseries", map )
	def setValueUpdate[T]( value : T ) : WidgetId ⇒ WidgetDataUpdate = id ⇒ WidgetDataUpdate( id.value, "setvalue", Map( "value" -> ( value ).toString ) )
	def addMessageUpdate( msg : Message ) : WidgetId ⇒ WidgetDataUpdate = id ⇒ WidgetDataUpdate( id.value, "addmessage", Map( "id" -> msg.id, "text" -> msg.text, "time" -> msg.time.getMillis().toString ) )
	def removeMessageUpdate( msgId : String ) : WidgetId ⇒ WidgetDataUpdate = id ⇒ WidgetDataUpdate( id.value, "removemessage", Map( "id" -> msgId ) )
}
object WidgetDatas extends WidgetData

trait WidgetDashboard extends WidgetData with ( Any ⇒ IO[List[String]] ) {
	import flow.dataprovider.WidgetDataToJason._
	
	def apply( incoming : Any ) : IO[List[String]] = {
			update( incoming ).map(convert(_))
	}
	
		def update( incoming : Any ) : IO[List[WidgetDataUpdate]] 
}


class WidgetState[T]( id : WidgetId, initState : T ) {

	var state = initState

	def update(f : T ⇒ T, g : T ⇒ WidgetId ⇒ WidgetDataUpdate ) = io {
		val newstate = f( state )
		val result = g( newstate )
		state = newstate
		List(result( id ))
	}

}

case class HistogramData( time : DateTime, duration : Duration )

case class HistogramState( windowLength : Duration, history : List[HistogramData] ) {
	def ::( data : HistogramData ) = HistogramState( windowLength, data :: history )
}

case class HistogramAppend( id : String, data : HistogramData ) extends ( HistogramState ⇒ HistogramState ) {
	def apply( state : HistogramState ) : HistogramState = {
		data :: state
	}
}

case class HistogramExpose( time : DateTime ) extends ( HistogramState ⇒ WidgetId ⇒ WidgetDataUpdate ) {
	def apply( state : HistogramState ) : WidgetId ⇒ WidgetDataUpdate = {
		val getDurationInDays : HistogramData ⇒ Days = e ⇒ e.duration.toStandardSeconds().toStandardDays()

		val updateHistory = state.history.filter( e ⇒ e.time.isAfter( time.minus( state.windowLength ) ) )
		val days = updateHistory.map( getDurationInDays )
		val max = ( Days.days( 10 ) :: days ).maxBy( _.getDays() )

		val count = days.foldLeft( Map[Int, Int]() ) { ( map, days ) ⇒
			map + ( days.getDays() -> ( map.getOrElse( days.getDays(), 0 ) + 1 ) )
		}

		val buckets = ( 0 to max.getDays() ).map( day ⇒ (day.toString,count.getOrElse( day, 0 ).toString) ).toMap
		
		WidgetDatas.setSeriesUpdate(buckets)
	}
}

case class HistogramAdvance( id : String, time : DateTime ) extends ( HistogramState ⇒ HistogramState ) {
	def apply( state : HistogramState ) : HistogramState = {
		val getDurationInDays : HistogramData ⇒ Days = e ⇒ e.duration.toStandardSeconds().toStandardDays()
		val updateHistory = state.history.filter( e ⇒ e.time.isAfter( time.minus( state.windowLength ) ) )

		HistogramState( state.windowLength, updateHistory )
	}
}


case class MessageAdd(msg : Message ) extends (List[Message]=>List[Message]) {
	def apply( state : List[Message] ):List[Message] = {
		msg :: state
	}
}
case class MessageRemove( msgId : String )  extends (List[Message]=>List[Message]) {
	def apply( state : List[Message] ) = {
		state.filterNot( _.id === msgId )
	}
}
	

