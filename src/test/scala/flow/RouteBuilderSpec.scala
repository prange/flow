package flow
import org.specs2.mutable.Specification
import flow.actor.OperatorBuilder._
import scalaz._
import Scalaz._
import flow.data.Parser
import scala.io.Source
import flow.actor.Context
import flow.actor.ReadyEngine
import flow._
import epcis._
import operator._
import AssembleBuilder._
import flow.operator.WeekdayEnricher

class RouteBuilderSpec extends Specification {

	val filename = "sykkelmeldinger.xml"
	val observations = parseObservations.reverse

	val repairProcessDefinition = ProcessDefinition( "repair", whereField( "disposition" ).contains( "active" ), whereField( "disposition" ).contains( "from_workshop" ) )
	val customerPickup = ProcessDefinition( "pickup", whereField( "disposition" ).contains( "from_workshop" ), whereField( "disposition" ).contains( "inactive" ) )

	"When building routes one" should {

		"Create Connectors" in {
			val builder = {
				val s = source( "test" )
				val weekdayEnricher = transform( "week", WeekdayEnricher() )
				val assembler = assemble( "assembler", List( repairProcessDefinition, customerPickup ), idExtractor )
				val print = sink( "sink", e ⇒ println(e) )

				s.out --> weekdayEnricher.in &
					weekdayEnricher.out --> assembler.in &
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