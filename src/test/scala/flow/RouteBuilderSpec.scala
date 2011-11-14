package flow
import org.specs2.mutable.Specification
import flow.actor.OperatorBuilder._
import scalaz._
import Scalaz._
import flow.data.Parser
import scala.io.Source
import flow.actor.Context
import flow.actor.ReadyEngine
import flow.operator.WeekdayEnricher
class RouteBuilderSpec extends Specification {

	val filename = "xservice 01.11.2011.xml"
	val observations = parseObservations
	"Building a route" should {
		"Create Connectors" in {
			val builder = {
				val s = source( "test" )
				val f1 = filter( "f1", { e ⇒ e.eventType == "AggregationEvent" } )
				val weekdayEnricher = transform( "week", WeekdayEnricher() )
				val sink1 = sink( "sink", e ⇒ println( e ) )

				s.out --> f1.in 														|+|
					f1.unfiltered --> weekdayEnricher.in 					|+|
					f1.filtered --> sink1.in 										|+|
					weekdayEnricher.out --> sink1.in

			}

			val context = builder.update( Context() )

			println( context )

			val engine = new ReadyEngine( context ).start().unsafePerformIO

			val e1 = observations.head
			val e2 = observations.tail.head

			val io1 = engine ! ( "test", Parser.toEvent( e1 ) )
			val io2 = engine ! ( "test", Parser.toEvent( e2 ) )
			io1.unsafePerformIO
			io2.unsafePerformIO

			success
		}

	}

	def parseObservations = Parser.parse( _.fromSource( Source.fromFile( filename ) ) )
}