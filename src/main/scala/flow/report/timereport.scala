package flow.report
import flow.data.Event
import flow.data.Process
import org.joda.time.format.ISOPeriodFormat
import org.joda.time.Interval
import org.joda.time.DateTime
import org.joda.time.Hours
import org.joda.time.Duration

object timereport {

	def durationToHours : List[Duration] ⇒ List[Hours] = _.map( ( d : Duration ) ⇒ Hours.standardHoursIn( d.toPeriod() ) )

	def procToDuration( fromPred : Event ⇒ Boolean, toPred : Event ⇒ Boolean ) = ( processes : Iterable[Process] ) ⇒ processes.foldLeft( List.empty[Duration] ) { ( xs, p ) ⇒

		trait TimeSearchResult

		case class ResultNotFound() extends TimeSearchResult

		case class TimeBegin( time : DateTime ) extends TimeSearchResult

		case class TimeEnd( beginTime : DateTime, endTime : DateTime ) extends TimeSearchResult {

			def text = span.getEndMillis().toString

			def span = new Interval( beginTime, endTime )

		}

		def findLongestTime( in : Process ) = {
			val res : TimeSearchResult = in.eventChain.events.foldLeft[TimeSearchResult]( ResultNotFound() ) { ( result, event ) ⇒
				val matchesFrom = fromPred( event )
				val matchesTo = toPred( event )
				( result, matchesFrom, matchesTo ) match {
					case ( ResultNotFound(), true, _ ) ⇒ TimeBegin( event.eventTime )
					case ( TimeBegin( startTime ), _, true ) ⇒ TimeEnd( startTime, event.eventTime )
					case ( TimeEnd( startTime, _ ), _, true ) ⇒ TimeEnd( startTime, event.eventTime )
					case ( e, _, _ ) ⇒ e
				}

			}
			res match {
				case e @ TimeEnd( _, _ ) ⇒ e.span.toDuration() :: xs
				case _ ⇒ xs
			}
		}
		
		findLongestTime( p )

	}

}

