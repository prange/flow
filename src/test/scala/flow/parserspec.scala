package flow.data

import org.specs2.mutable.Specification

import flow.data._
import scalaz._
import Scalaz._
import Cut._

class FlowSpec extends Specification {

	val filename = "gsport_epcis_events2.xml"

	"Parsing the gsport_epcis_events file" should {
		"Result in events" in {
			val observations = parseObservations()

			observations.size must be > 0
		}

		"Contain ids" in {
			val observations = parseObservations()

			observations.map( _.e.values.get( "epc" ) ).filter( _.isEmpty ).size mustEqual 0
		}

		"Store events in eventStore" in {
			val observations = parseObservations()
			val flow = new Data()
			observations.map( flow.handle ).sequence.unsafePerformIO

			flow.eventlog.list.size mustEqual observations.size
		}

		"Chain events" in {

			val observations = parseObservations()
			val flow = new Data()
			observations.map( flow.handle ).sequence.unsafePerformIO

			flow.handle( BuildChain( e ⇒ true, e ⇒ e.values( "epc" ) ) ).unsafePerformIO

			flow.chains.chains.size must be > 0

		}

		"Build processes" in {

			val observations = parseObservations()
			val flow = new Data()
			observations.map( flow.handle ).sequence.unsafePerformIO

			flow.handle( BuildChain( e ⇒ true, e ⇒ e.values( "epc" ) ) ).unsafePerformIO

			flow.handle( BuildProcess( c ⇒ true, List( cutAfter( pred( "disposition", "finished" ) ) ) ) ).unsafePerformIO

			flow.processes.processes.size must be > 0
		}

		"Be queryable" in {

			val observations = parseObservations()
			
			val flow = new Data()
			
			flow.handle(AddEnrichment(WeekdayEnricher())).unsafePerformIO
			
			observations.map( flow.handle ).sequence.unsafePerformIO

			flow.handle( BuildChain( (e:Event) ⇒ true, e ⇒ e.values( "epc" ) ) ).unsafePerformIO

			flow.handle( BuildProcess( c ⇒ true, List( cutAfter( pred( "disposition", "finished" ) ) ) ) ).unsafePerformIO

			val result = flow.queryProcess( PredicateProcessQuery( e ⇒ true ) ).unsafePerformIO

			println( result.head )
			println( result.tail.head )
			
			result.size should be > 0
		}
	}

	def parseObservations() = {
		val events = Parser.parseFile( filename )

		val observations = events.map( Parser.createEventList ).flatten

		observations
	}

}