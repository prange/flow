package flow.data

import org.specs2.mutable.Specification
import flow.data._
import scalaz._
import Scalaz._
import Cut._
import scala.io.Source

class FlowSpec extends Specification {

	val filename = "xservice 01.11.2011.xml"

	"Parsing the gsport_epcis_events file" should {
		"Result in events" in {
			val observations = parseObservations()

			observations.size must be > 0
		}

		"Contain ids" in {
			val observations = parseObservations()

			val a = observations.map( _.values.get( "epc" ) )

			a.filter( _.isEmpty ).size mustEqual 0
		}

		"Store events in eventStore" in {
			val observations = parseObservations()
			val flow = new Data()
			flow.handle( EventObservation( observations ) ).unsafePerformIO

			flow.eventlog.list.size mustEqual observations.size
		}

		"Chain events" in {

			val observations = parseObservations()
			val flow = new Data()
			flow.handle( EventObservation( observations ) ).unsafePerformIO

			flow.handle( BuildChain( ( e : Event ) ⇒ true, e ⇒ e.values.getOrElse( "epc", "<unknown>" ) ) ).unsafePerformIO

			flow.chains.chains.size must be > 0

		}

		"Build processes" in {

			val observations = parseObservations()
			val flow = new Data()
			flow.handle( EventObservation( observations ) ).unsafePerformIO

			flow.handle( BuildChain( ( e : Event ) ⇒ true, e ⇒ e.values.getOrElse( "epc", "<unknown>" ) ) ).unsafePerformIO

			flow.handle( BuildProcess( c ⇒ true, List( cutAfter( pred( "disposition", "finished" ) ) ), p ⇒ p ) ).unsafePerformIO

			flow.processes.processes.size must be > 0
		}

		"Be queryable" in {

			val observations = parseObservations()

			val flow = new Data()

			flow.handle( AddEnrichment( WeekdayEnricher() ) ).unsafePerformIO

			flow.handle( EventObservation( observations ) ).unsafePerformIO

			flow.handle( BuildChain( ( e : Event ) ⇒ true, e ⇒ e.values.getOrElse( "epc", {println("Unknown epc: "+e);"<unknown>"} ) ) ).unsafePerformIO

			flow.handle( BuildProcess( c ⇒ true, List( cutAfter( pred( "disposition", "finished" ) ) ), p ⇒ p ) ).unsafePerformIO

			val result = flow.queryProcess( PredicateProcessQuery( e ⇒ true ) ).unsafePerformIO

			result.size should be > 0
		}

		"Enrich processes" in {
			val observations = parseObservations()

			val flow = new Data()

			flow.handle( AddEnrichment( WeekdayEnricher() ) ).unsafePerformIO
			flow.handle( AddEnrichment( CombinationEnricher( "activity", "bizLocation", "bizStep" ) ) )
			flow.handle( EventObservation( observations ) ).unsafePerformIO

			flow.handle( BuildChain( ( e : Event ) ⇒ true, e ⇒ e.values( "epc" ) ) ).unsafePerformIO

			flow.handle( BuildProcess( c ⇒ true, List(), p ⇒ p ) ).unsafePerformIO

			val result = flow.queryProcess( PredicateProcessQuery( e ⇒ true ) ).unsafePerformIO

			val enriched = result.map( Process.flatter ).map( Process.elapsedTime( "disposition", "received_store", "inactive" ) )

			enriched.size should be > 46
		}
	}

	def parseObservations() = {
		val events = Parser.parse( _.fromSource( Source.fromFile( filename ) ) )

		events
	}

}