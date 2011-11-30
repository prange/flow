package flow
import scala.io.Source
import org.specs2.mutable.Specification
import event._
import flow.actor.OperatorBuilder._
import flow.data.Parser
import flow._
import operator.AssembleBuilder._
import operator._
import scalaz.Scalaz._
import flow.epcis.EpcisTransform
import flow.epcis.ObservationTransform
import flow.epcis.ProductEnricher
import flow.actor.Context
import flow.actor.ReadyEngine
import flow.actor.Routers._

class RouteBuilderSpec extends Specification {

	import Predicates._
	val filename = "sykkelmeldinger.xml"
	val observations = parseObservations.reverse

	val repairProcessDefinition = ProcessDefinition( "repair", where field "disposition" contains "active", where field "disposition" contains "from_workshop" )
	val customerPickup = ProcessDefinition( "pickup", where field "disposition" contains "from_workshop", where field "disposition" contains "inactive" )

//	"When building routes one" should {

//		"Create Connectors" in {
//			val engine = createEngine
//
//			val exec = for {
//				e ← createEngine.start();
//				_ ← e ! ( "test", Parser.toEvent( observations.head ) );
//				_ ← Threads.sleep( 3000 );
//				s ← e.stop
//			} yield ( s )
//			exec.unsafePerformIO
//			success
//
//		}
//
//		"Handle simulated events" in {
//			val engine = createEngine
//
//			val exec = for {
//				e ← createEngine.start();
//				sim ← new DirectTest( 20000, "test", e ).run;
//				_ ← Threads.sleep( 10000 );
//				_ ← sim.stop
//				s ← e.stop
//			} yield ( s )
//			exec.unsafePerformIO
//			success
//		}
//
//	}
	
	"Using RabbitMQ" should {
			"Exchange events with server" in {

				val engine = createEngine

				val exec = for {
					e ← createEngine.start();
					sim ← new RabbitMQTest(EventSimulator.domain, 100000, "test", e ).run;
					_ ← Threads.sleep( 20000 );
					_ ← sim.stop
					s ← e.stop
				} yield ( s )
				exec.unsafePerformIO
				success
			}
		}

	def parseObservations = Parser.parse( _.fromSource( Source.fromFile( filename ) ) )

	def createEngine = {
		val builder = {
			val s = source( "test" )
			val epcisTransformer = multtransform( "xserviceToObservations", EpcisTransform() andThen ObservationTransform() andThen ProductEnricher(), oneXmlInputHandler )
			val assembler = assemble( "assembler", List( repairProcessDefinition, customerPickup ), _.get( "id" ) )
			val print = sink( "sink", e ⇒ println( "Sink:"+e ) )

			s.out --> epcisTransformer.in &
				epcisTransformer.out --> assembler.in &
				assembler.started --> print.in &
				assembler.ended --> print.in

		}

		val context = builder.update( Context() )

		new ReadyEngine( context )
	}
}