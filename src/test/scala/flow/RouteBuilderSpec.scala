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

class RouteBuilderSpec extends Specification {

	import Predicates._
	val filename = "sykkelmeldinger.xml"
	val observations = parseObservations.reverse

	val repairProcessDefinition = ProcessDefinition( "repair", where field "disposition" contains "active", where field "disposition" contains "from_workshop" )
	val customerPickup = ProcessDefinition( "pickup", where field "disposition" contains "from_workshop", where field "disposition" contains "inactive" )

	"When building routes one" should {

		"Create Connectors" in {
			val builder = {
				val s = source( "test" )
				val epcisTransformer = transform("xserviceToObservations",EpcisTransform() andThen ObservationTransform() andThen ProductEnricher())
				val assembler = assemble( "assembler", List( repairProcessDefinition, customerPickup ), _.get("id") )
				val print = sink( "sink", e ⇒ println( e ) )

				s.out --> assembler.in &
					assembler.started --> print.in &
					assembler.ended --> print.in

			}

			val context = builder.update( Context() )

			println( context )

			val engine = new ReadyEngine( context ).start().unsafePerformIO

			val submit = observations.map( o ⇒ engine ! ( "test", Parser.toEvent( o ) ) )

			val result = submit.foldLeft( List[Throwable]() ) { ( list, eff ) ⇒
				val res = eff.catchLeft.unsafePerformIO
				res.fold( l ⇒ l :: list, r ⇒ list )
			}

			engine.stop.unsafePerformIO

			if ( result.size == 0 ) success else { println( result ); failure }

		}

	}

	def parseObservations = Parser.parse( _.fromSource( Source.fromFile( filename ) ) )
}