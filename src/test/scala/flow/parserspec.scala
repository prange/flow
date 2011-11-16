package flow.data

import org.specs2.mutable.Specification
import flow.data._
import flow.statistics._
import scalaz._
import Scalaz._
import scala.io.Source
import org.joda.time.Hours
import org.joda.time.Duration

class FlowSpec extends Specification {

	val filename = "xservice 01.11.2011.xml"

	"Parsing the gsport_epcis_events file" should {
		"Result in events" in {
			val observations = parseObservations

			observations.size must be > 0
		}
	}
		
	def parseObservations = Parser.parse( _.fromSource( Source.fromFile( filename ) ) )
}