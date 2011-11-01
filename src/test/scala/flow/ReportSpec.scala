package flow
import org.specs2.mutable.Specification
import flow._
import data._
import report._
import Cut._

object ReportSpec extends Specification {

	val filename = "gsport_epcis_events2.xml"

	"Reporting on data from the gsport_epcis_events file" should {
		"Yield bizloc transitions" in {
			val flow = loadData()

			val result = flow.queryProcess( PredicateProcessQuery( e ⇒ true ) ).unsafePerformIO

			val transitions =  ProcessMap.createBizLocationMap(result).unsafePerformIO
			
			val stripped = transitions.map(l=>l.map(t=>TransitionCount(ProcessMap.stripPrefix(t.transition),t.count)))
			
			
			result.size should be > 46
		}
		
		"Yield bizstep transitions" in {
			val flow = loadData()

			val result = flow.queryProcess( PredicateProcessQuery( e ⇒ true ) ).unsafePerformIO

			val transitions =  ProcessMap.createBizStepMap(result).unsafePerformIO
			
			val stripped = transitions.map(l=>l.map(t=>TransitionCount(ProcessMap.stripPrefix(t.transition),t.count)))
			
			
			result.size should be > 46
		}
		
	}

	def loadData() = {
		val events = Parser.parseFile( filename )

		val observations = events.map( Parser.createEventList ).flatten

		val flow = new Data()

		flow.handle( AddEnrichment( WeekdayEnricher() ) ).unsafePerformIO

		flow.handle( EventObservation( observations ) ).unsafePerformIO

		flow.handle( BuildChain( ( e : Event ) ⇒ true, e ⇒ e.values( "epc" ) ) ).unsafePerformIO

		flow.handle( BuildProcess( c ⇒ true, List(), p ⇒ p ) ).unsafePerformIO
		
		flow
	}

}