package flow.data
import flow.data._
import scalaz._
import Scalaz._
import scalaz.effects.io
import scalaz.effects.IO

object Enricher {
	def enrich( id : String, key : String, value : String, addKey : String, addValue : String ) =
		new ConditionalEnricher( id : String, e ⇒ ( e.values( key ) === value ), e ⇒ e.withProperty( addKey, addValue ) )

}

class Enrichments {

	private val enrichments = Array[Enricher]()

	def add( enr : Enricher ) = io[Unit] {
		enrichments :+ enr
	}

	def remove( id : String ) = io[Unit] {
		enrichments.filterNot( e ⇒ e.id == id )
	}

	def get : IO[Event ⇒ Event] = io {
		( event : Event ) ⇒ enrichments.foldLeft( event )( ( e, enrich ) ⇒ enrich( e ) )
	}

}

trait Enricher extends ( Event ⇒ Event ) {

	val id : String

}

class ConditionalEnricher( val id : String, pred : Event ⇒ Boolean, f : Event ⇒ Event ) extends Enricher {

	def apply( event : Event ) = if ( pred( event ) ) f( event ) else event

}

case class WeekdayEnricher() extends Enricher {

	val id = "weekday"

	def apply( event : Event ) : Event = {
			val time = event.eventTime
			event.withProperty("dayOfWeek",time.getDayOfWeek().toString)
	}

}


