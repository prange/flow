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
import flow._
import epcis._
import operator._
import AssembleBuilder._

class RouteBuilderSpec extends Specification {

	val filename = "xservice 01.11.2011.xml"
	val observations = parseObservations

	val repairProcessDefinition = ProcessDefinition( "repair", whereField( "bizStep" ).contains( "commissioning" ), whereField( "bizStep" ).contains( "from_workshop" ) )
	val customerPickup = ProcessDefinition( "pickup", whereField( "bizStep" ).contains( "from_workshop" ), whereField( "bizStep" ).contains( "decommissioning" ) )
	"Building a route" should {
		"Create Connectors" in {
			val builder = {
				val s = source( "test" )
				val weekdayEnricher = transform( "week", WeekdayEnricher() )
				val assembler = assemble( "assembler", List( repairProcessDefinition, customerPickup ), idExtractor )
				val print = sink( "sink", e ⇒ println( e ) )

				s.out --> weekdayEnricher.in &
					weekdayEnricher.out --> assembler.in &
					assembler.started --> print.in &
					assembler.advanced --> print.in &
					assembler.ended --> print.in

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