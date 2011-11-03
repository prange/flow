package flow.report
import flow.data.Event
import flow.data.Process
import org.joda.time.format.ISOPeriodFormat
import org.joda.time.Interval
import org.joda.time.DateTime
import org.joda.time.Hours
import org.joda.time.Duration
import org.joda.time.Days

object Timereport {

	def pred( key : String ) : String ⇒ Event ⇒ Boolean = s ⇒ e ⇒ { if ( e.values.getOrElse( key, "<unknown>" ) contains s ) true else false }

	def toPareto( list : List[HistogramEntry] ) = list.foldLeft( List( ( HistogramEntry( 0, "" ), CumulativeEntry( 0, "" ) ) ) ) { ( list, entry ) ⇒
		( entry, CumulativeEntry( list.head._2.total + entry.value, entry.label ) ) :: list
	}.tail.reverse

	def daysHistogram( fromPred : Event ⇒ Boolean, toPred : Event ⇒ Boolean, processes : Iterable[Process] ) = {

		val f = procToDuration( fromPred, toPred ) andThen durationToDays
		val hours = f( processes )
		val b = bounds( hours.map( _.getDays() ) )
		val c = count( hours.map( _.getDays() ) )

		( b.min to b.max ).map( value ⇒ HistogramEntry( c.getOrElse( value, 0 ), value.toString ) ).toList
	}

	def count : List[Int] ⇒ Map[Int, Int] = hs ⇒ hs.groupBy( identity ).mapValues( _.size )

	def bounds( values : List[Int] ) : Bounds = values.foldLeft( Bounds( 0, 0 ) ) { ( b, value ) ⇒
		b.update( value )
	}

	val durationToDays : List[Duration] ⇒ List[Days] = _.map( ( d : Duration ) ⇒ Days.standardDaysIn( d.toPeriod() ) )

	def procToDuration( fromPred : Event ⇒ Boolean, toPred : Event ⇒ Boolean ) = ( processes : Iterable[Process] ) ⇒ processes.foldLeft( List.empty[Duration] ) { ( xs, p ) ⇒

		trait TimeSearchResult

		case class ResultNotFound() extends TimeSearchResult

		case class TimeBegin( time : DateTime ) extends TimeSearchResult

		case class TimeEnd( beginTime : DateTime, endTime : DateTime ) extends TimeSearchResult {

			def text = span.getEndMillis().toString

			def span = new Interval( beginTime, endTime )

		}

		def findLongestTime( in : Process ) : TimeSearchResult = {
			in.eventChain.events.foldLeft[TimeSearchResult]( ResultNotFound() ) { ( result, event ) ⇒
				val matchesFrom = fromPred( event )
				val matchesTo = toPred( event )
				( result, matchesFrom, matchesTo ) match {
					case ( ResultNotFound(), true, _ ) ⇒ TimeBegin( event.eventTime )
					case ( TimeBegin( startTime ), _, true ) ⇒ TimeEnd( startTime, event.eventTime )
					case ( TimeEnd( startTime, _ ), _, true ) ⇒ TimeEnd( startTime, event.eventTime )
					case ( e, _, _ ) ⇒ e
				}
			}
		}

		val d = findLongestTime( p )

		d match {
			case e @ TimeEnd( _, _ ) ⇒ e.span.toDuration() :: xs
			case _ ⇒ xs
		}

	}

}

case class Bounds( min : Int, max : Int ) {
	def update( value : Int ) = {
		val newMin = if ( value < min ) value else min
		val newMax = if ( value > max ) value else max
		Bounds( newMin, newMax )
	}
}

case class HistogramEntry( value : Int, label : String )
case class CumulativeEntry( total : Int, label : String )
