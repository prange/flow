package flow

import org.specs2.mutable.Specification
import flow.data._
import scalaz._
import Scalaz._
import Cut._
import flow.analyzer.FrequentPatternAnalyzer
import scala.io.Source
import java.io.File

class AnalyzerSpec extends Specification {

	import flow.analyzer.ProcessPredicate._

	val filename = "xservice 01.11.2011.xml"

	"Parsing the gsport_epcis_events file" should {

    "predicate should filter" in {
      val a = buildProcess
      val b = a.filter(valueLessThanPred("disposition:{ received_store-->inactive }", Time.hours(20)))
      println(b.size)

			success
		}

		"Result in events" in {

			val analyzer = new FrequentPatternAnalyzer()

			val output = analyzer.generateFrequentPatterns( buildProcess, valueLessThanPred( "disposition:{ received_store-->inactive }", Time.hours( 10 ) ) )

			println( output )

			success
		}
	}

	def buildProcess = {
		val observations = parseObservations()
		val flow = new Data()
		flow.handle( EventObservation( observations ) ).unsafePerformIO
    flow.handle(BuildChain(e ⇒ true, e ⇒ e.values.getOrElse("epc","<unknown>"))).unsafePerformIO

		flow.handle( BuildProcess( c ⇒ true, List( cutAfter( pred( "disposition", "finished" ) ) ), p ⇒ p ) ).unsafePerformIO

		val result = flow.queryProcess( PredicateProcessQuery( e ⇒ true ) ).unsafePerformIO

		val enriched = result.map( Process.flatter ).map( Process.elapsedTime( "time", "disposition", "received_store", "inactive" ) )

		enriched
	}

  def parseObservations() = {
    Parser.parse(_.fromSource(Source.fromFile(filename)))
  }

}