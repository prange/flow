package flow.data
import scala.collection.mutable.ArrayBuffer
import scalaz.effects._
import scalaz._
import Scalaz._
import org.joda.time.DateTime
import org.joda.time.Interval
import org.joda.time.format.ISOPeriodFormat

class Processes {
	val processes = ArrayBuffer[Process]()

	def record( chains : Iterable[Process] ) = io[Unit] {
		for ( chain ← chains ) yield ( processes += chain )
	}

	def query( pred : Process ⇒ Boolean ) = io { processes.view.filter( pred ).toIterable }

	def clear : IO[Unit] = io { processes.clear }

}

case class Process( values : Map[String, String], eventChain : EventChain ) {
	def withProperty( pname : String, pvalue : String ) = new Process( values + ( pname -> pvalue ), eventChain )

	override def toString : String = {
		val sorted = values.toSeq.sortBy( t ⇒ t._1 )
		"Process{\n"+sorted.mkString( "", "\n", "" )+"\n}"

	}
}

object Process {

	def apply( eventChain : EventChain ) : Process = Process( Map.empty, eventChain )

	def flatter : Process ⇒ Process = FlattenProcess

	def elapsedTime(label:String, key : String, from : String, to : String ) : Process ⇒ Process = {

		val fromPred = ( e : Event ) ⇒ e.values.get( key ).map( _.contains( from ) ) getOrElse ( false )
		val toPred = ( e : Event ) ⇒ e.values.get( key ).map( _.contains( to ) ) getOrElse ( false )
		ExtractLongestTime( label, fromPred, toPred )
	}
	
}

object FlattenProcess extends ( Process ⇒ Process ) {

	def apply( in : Process ) = {
		val value = in.eventChain.events.foldLeft( ( 1, in.values ) )( ( state, event ) ⇒ {
			val count = state._1
			val vals = state._2
			( count + 1, appendTo( count, event, vals ) )
		} )
		Process( value._2, in.eventChain )
	}

	def appendTo( num : Int, event : Event, map : Map[String, String] ) = {
		event.values.foldLeft( map )( ( m, t ) ⇒ m + ( ( t._1+"."+num ) -> t._2 ) )
	}

}

case class ExtractLongestTime( label : String, fromPred : Event ⇒ Boolean, toPred : Event ⇒ Boolean ) extends ( Process ⇒ Process ) {

	def apply( in : Process ) = {
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
			case e @ TimeEnd( _, _ ) ⇒ in.withProperty( label, e.text ).withProperty( label+"(r)", ISOPeriodFormat.standard().print( e.span.toPeriod() ) )
			case _ ⇒ in
		}
	}

	case class ResultNotFound() extends TimeSearchResult {
	}

	case class TimeBegin( time : DateTime ) extends TimeSearchResult {
	}

	case class TimeEnd( beginTime : DateTime, endTime : DateTime ) extends TimeSearchResult {

		def text = (span.getEndMillis()-span.getStartMillis()).toString

		def span = new Interval( beginTime, endTime )

	}
}

trait TimeSearchResult {

}
