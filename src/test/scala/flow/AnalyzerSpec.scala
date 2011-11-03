package flow

import scala.io.Source

import org.specs2.mutable.Specification

import flow.analyzer.FrequentPatternAnalyzer
import flow.data.Cut._
import flow.data._
import scalaz.Scalaz._
import scalaz._

class AnalyzerSpec extends Specification {

	import flow.analyzer.ProcessPredicate._

	val filename = "xservice 01.11.2011.xml"

	"Parsing the gsport_epcis_events file" should {

		"predicate should filter" in {
			val a = buildProcess
			val b = a.filter( valueLessThan( "time", Time.hours( 20 ) ) )

			success
		}

		"Result in events" in {

			val analyzer = new FrequentPatternAnalyzer()

			val days = 20

			val p = buildProcess.filter( finishedProcessPredicate ).map( priorityEnricher )
			val over = p.filter( notPred( valueLessThan( "time", Time.days( days ) ) ) )
			println( "**** Antall over :"+over.size )
			println( "**** Antall totalt :"+p.size )
			val output = analyzer.generateFrequentPatterns( p, notPred( valueLessThan( "time", Time.days( days ) ) ) )
			println(output)
			
			
			val dataset = analyzer.createPredictionModelInstances(p, "time" )
			val predictionModel = analyzer.createPredictionModel(dataset)

			val indexOfPredictionInstance = 15
			val instance = dataset.instance(indexOfPredictionInstance)
			println("Predicting instance: "+instance)
			println("PREDICTING DURATION: "+predictionModel.classifyInstance(instance))
			
			

			success
		}
	}

	val notTestProcessPredicate : Process ⇒ Boolean = p ⇒ p.eventChain.events.filter( testEventPredicate ).size == 0

	val finishedProcessPredicate : Process ⇒ Boolean = p ⇒ p.eventChain.events.last.values.getOrElse( "action", "" ) contains "DELETE"

	val testEventPredicate : Event ⇒ Boolean = e ⇒ e.values.getOrElse( "bizLocation", "<unknown>" ).contains( "test" )

	val notTestDataEventPredicate : Event ⇒ Boolean = e ⇒ e.values.getOrElse( "epc", "0" ).split( '.' ).last.toInt > 100000

	val priorityEnricher : Process ⇒ Process = p ⇒ if ( p.eventChain.events.head.values.getOrElse( "services", "" ) contains "Spesial1" ) p.withProperty( "priority", "true" ) else p.withProperty( "priority", "false" )

	def buildProcess = {
		val observations = parseObservations()
		val flow = new Data()
		flow.handle( EventObservation( observations ) ).unsafePerformIO
		flow.handle( BuildChain( notTestDataEventPredicate, e ⇒ e.values.getOrElse( "epc", "<unknown>" ) ) ).unsafePerformIO

		flow.handle( BuildProcess( c ⇒ true, List(), p ⇒ p ) ).unsafePerformIO

		val result = flow.queryProcess( PredicateProcessQuery( e ⇒ true ) ).unsafePerformIO

		val enriched = result.map( Process.flatter andThen Process.elapsedTime( "time", "disposition", "from_workshop", "inactive" ) )

		enriched
	}

	def parseObservations() = {
		Parser.parse( _.fromSource( Source.fromFile( filename ) ) )
	}

}