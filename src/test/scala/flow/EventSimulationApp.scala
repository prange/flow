package flow
import com.rabbitmq.client.Address

import EventSimulator._
import actor.ReadyAmqpProducer
import epcis._
import flow.actor.OperatorBuilder._
import flow.actor.Routers._
import flow.actor._
import flow.dataprovider._
import flow.epcis._
import flow.event.Predicates._
import flow.event._
import operator.AssembleBuilder._
import operator._
import scalaz.Scalaz._
import scalaz.effects._
import scalaz._

object EventSimulationApp extends App {
	def widgetSink( name : String, f : Any=>IO[Unit] ) = sink( name, f(_).unsafePerformIO) 
	//	val domainname = "ec2-50-19-72-109.compute-1.amazonaws.com"

	val repairProcessDefinition = ProcessDefinition( "repair", where field "disposition" contains "active", where field "disposition" contains "from_workshop" )
	val customerPickup = ProcessDefinition( "pickup", where field "disposition" contains "from_workshop", where field "disposition" contains "inactive" )
	val epcisTransformer = multtransform( "xserviceToObservations", XService.transform, oneXmlInputHandler )
	val assembler = assemble( "assembler", List( repairProcessDefinition, customerPickup ), _.get( "id" ) )

	val s = source( "test" )
	val print = sink( "sink", e ⇒ println( "Sink:"+e ) )
val dash = publishingSink("xservicedashboard",new XServiceDashboard())
	//routes
	val builder = {
		s.out --> epcisTransformer.in &
			epcisTransformer.out --> assembler.in &
			assembler.started --> print.in &
			assembler.ended --> print.in &
			assembler.started --> dash.in &
			assembler.ended --> dash.in
	}

	val context = builder.update( Context() )

	val engine = new ReadyEngine( )

	def eventToString( client : String ⇒ IO[Unit] ) : XmlEvent ⇒ IO[Unit] = e ⇒ {
		client.apply( e.data.toString )
	}

	val exec = for {
		time ← Time.now;
		conn ← FlowAmqp.connector( username, password, address ).cfg( testSetup andThen widgetSetup ).start();
		publisher ← new ReadyAmqpProducer( "publisher", "widget" ).start( conn.connx );
		e ← engine.start(context.publisher( ("xservicedashboard",new RabbitMQPublisher(publisher) )));
		source ← new ReadyAmqpConsumer( "engineConsumer", "test", s ⇒ e.!( "test", stringToXmlEvent( s ) ) ).start( conn.connx );
		client ← new ReadyAmqpProducer( "engineProducer", "test" ).start( conn.connx );
		sim ← new RabbitMQTest( "test", e, EventSimulator.init( time, 100000 ), eventToString( client ) ).run;
		_ ← Threads.sleep( 60000 );
		_ ← sim.stop;
		s ← e.stop
	} yield ( s )
	exec.unsafePerformIO

}

class XServiceDashboard(  ) extends WidgetDashboard{
	val inProcessCounter = widgetState( WidgetId( "inProcessCounter" ), 0 )
	val awaitingPickupCounter = widgetState( WidgetId( "pickupCounter" ), 0 )
	val messageList = widgetState( WidgetId( "tasklist" ), List[Message]() )


	def update( incoming : Any ) = incoming match {
		case msg@ProcessStartedEvent( _, EventChain( _, "pickup", _, _ ) ) ⇒ awaitingPickupCounter.update( ( _ + 1 ), setValueUpdate( _ ) )  |+| messageList.update( (toMessage(msg)  :: _ ), s=>addMessageUpdate( toMessage(msg) ) )
		case msg@ProcessEndedEvent( _, EventChain( msgId, "pickup", _, _ ) ) ⇒ awaitingPickupCounter.update( ( _ - 1 ), setValueUpdate( _ ) ) |+| messageList.update( (_.filterNot( _.id === msgId ) ), s=>removeMessageUpdate(msg.eventchain.id) )
		case ProcessStartedEvent( _, EventChain( _, "repair", _, _ ) ) ⇒ inProcessCounter.update( ( _ + 1 ), setValueUpdate( _ ) )
		case ProcessEndedEvent( _, EventChain( _, "repair", _, _ ) ) ⇒ inProcessCounter.update( ( _ - 1 ), setValueUpdate( _ ) )
		case _ ⇒ io { List() }
	}

	def toMessage(msg:ProcessStartedEvent):Message = {
		def getMsg(key:String):ObservationEvent=>String = e=>e.get(key)
		def getEvent(event:ProcessStartedEvent):String = {
			val r =event.eventchain.events.reverse.head
			getMsg("hrafnxservice:customerBrand")(r)+" - "+getMsg("hrafnxservice:customerModel")(r)
		}
		println(msg.eventchain.events)
		Message( Time.now.unsafePerformIO, msg.eventchain.id,getEvent(msg) )
	}
	
}

