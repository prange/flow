package flow
import flow.actor.FlowAmqp
import flow.actor.Password
import flow.actor.ReadyAmqpProducer
import flow.actor.ReadyOperatorEngine
import flow.actor.Username
import flow.data.Parser
import flow.epcis.XService
import flow.event.ObservationEvent
import flow.event.Predicates
import flow.event.XmlEvent
import flow.operator.BaseOperatorBuilder
import flow.operator.InMemoryAssemblerBuilder
import flow.operator.ProcessDefinition
import flow.operator.RouteBuilder
import scalaz.Scalaz._
import scalaz.effects._
import scalaz._
import flow.actor.ReadyAmqpConsumer
import flow.actor.Address

object EventSimulationApp extends App {
	//	val domainname = "ec2-50-19-72-109.compute-1.amazonaws.com"
	import FlowAmqp._
	val filename = "sykkelmeldinger.xml"
	val protocol = "http://"
	val domain = "localhost" //"ec2-50-19-72-109.compute-1.amazonaws.com"
	val httpPort = "8080"
	val path = "/epcis/xservice"
	val username = Username( "guest" )
	val password = Password( "guest" )
	val address = Address( "localhost" )
	val testQueue = cfg( _.exchangeDeclare( "test", "direct", true ) )
	val testExchange = cfg( _.queueDeclare( "test", false, false, false, null ) )
	val testBinding = cfg( _.queueBind( "test", "test", "somekey" ) )
	val testSetup = testQueue andThen testExchange andThen testBinding
	val widgetQueue = cfg( _.exchangeDeclare( "widget", "direct", true ) )
	val widgetExchange = cfg( _.queueDeclare( "widgetq", false, false, false, null ) )
	val widgetBinding = cfg( _.queueBind( "widgetq", "widget", "somekey" ) )
	val widgetSetup = widgetQueue andThen widgetExchange andThen widgetBinding
	val stringToXmlEvent : String ⇒ XmlEvent = s ⇒ Parser.toEvent( Parser.parseString( s ) )
	def eventToString( client : String ⇒ IO[Unit] ) : XmlEvent ⇒ IO[Unit] = e ⇒ { client.apply( e.data.toString ) }

	class EventSimulationRouteBuilder extends RouteBuilder with BaseOperatorBuilder with InMemoryAssemblerBuilder with Predicates {
		val repairProcessDefinition = ProcessDefinition( "repair", where field "disposition" contains "active", where field "disposition" contains "from_workshop" )
		val s = source[XmlEvent]( "test" )
		val customerPickup = ProcessDefinition( "pickup", where field "disposition" contains "from_workshop", where field "disposition" contains "inactive" )
		val epcisTransformer = transformer( "xserviceToObservations", XService.transform )
		val assemble = assembler( "assembler", List( repairProcessDefinition, customerPickup ), _.get( "id" ) )
		val print = printer[Any]( "sink", e ⇒ "Sink:"+e )

		val operators = s :: customerPickup :: epcisTransformer :: assemble :: print :: Nil

		val routes =
			s.out --> epcisTransformer.in ::
				epcisTransformer.out --> assemble.in ::
				assemble.started --> print.in ::
				assemble.ended --> print.in :: Nil

	}

	val exec = for {
		time ← Time.now;
		conn ← FlowAmqp.connector( username, password, address ).cfg( testSetup andThen widgetSetup ).start();
		publisher ← new ReadyAmqpProducer( "publisher", "widget" ).start( conn.connx );
		e ← new ReadyOperatorEngine( new EventSimulationRouteBuilder() ).start;
		source ← new ReadyAmqpConsumer( "engineConsumer", "test", s ⇒ e.handle( "test", stringToXmlEvent( s ) ) ).start( conn.connx );
		client ← new ReadyAmqpProducer( "engineProducer", "test" ).start( conn.connx );
		sim ← new RabbitMQTest( "test", e, EventSimulator.init( time, 100000 ), eventToString( client ) ).run;
		_ ← Threads.sleep( 60000 );
		_ ← sim.stop;
		s ← e.stop
	} yield ( s )
	
	
	exec.unsafePerformIO

}


