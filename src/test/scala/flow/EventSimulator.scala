package flow

import scala.io.Source
import org.joda.time.DateTime
import org.joda.time.Duration
import com.codecommit.antixml._
import akka.actor.ActorRef
import dispatch._
import flow.Time._
import flow.actor._
import flow.data.Parser
import flow.event.XmlEvent
import scalaz.Scalaz._
import scalaz.effects._
import scalaz._

object EventSimulator {
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
	/*
 * channel.exchangeDeclare( exchangeName, "direct", true )
		channel.queueDeclare( queueName, false, false, false, null )
		channel.queueBind( queueName, exchangeName, routingKey )
 */

	def init( now : DateTime, ratio : Int ) = {
		def toRealTime( now : DateTime, simulatedStartTime : DateTime, ratio : Int ) = ( simulatedEventTime : DateTime ) ⇒ {
			val difference = new Duration( ( simulatedEventTime.getMillis() - simulatedStartTime.getMillis() ) / ratio )
			now.plus( difference )
		}

		val observations = Parser.parse( _.fromSource( Source.fromFile( filename ) ) ).map( Parser.toEvent( _ ) )
		val earliest = observations.map( _.eventTime ).min
		val realTime = toRealTime( now, earliest, ratio )
		val execTimes = observations.map( e ⇒ ( realTime( e.eventTime ), e ) )

		new EventSimulator( execTimes )
	}

}

class EventSimulator( execs : Iterable[( DateTime, XmlEvent )] ) {

	def times = execs

	def start[T]( f : XmlEvent ⇒ IO[Unit], endCallback : ⇒ T ) = io {

		val ff : XmlEvent ⇒ XmlEvent = e ⇒ { e }
		val latest = execs.map( _._1 ).max
		val timer = new JavaTimer( false )
		execs.foreach( t ⇒ timer.schedule( t._1 )( ( ff andThen f )( t._2 ).unsafePerformIO ) )
		timer.schedule( latest.plus( 1000 ) )( endCallback )
		timer.schedule( latest.plus( 2000 ) )( timer.stop )
		new RunningEventSimulator( execs, timer )
	}

}

class RunningEventSimulator( execs : Iterable[( DateTime, XmlEvent )], timer : JavaTimer ) {

	def stop() = io {
		timer.stop
		new EventSimulator( execs )
	}
}

class HttpClient( domain : String, url : String, port : Int ) {
	def send( body : String ) = {
		var uri = :/( domain+":"+port ) / url
		var req = uri << body
		Http( req as_str )
	}
}

class DirectTest( ratio : Int, sourceName : String, engine : RunningEngine ) {
	def run = {
		val sim = EventSimulator.init( Time.now.unsafePerformIO, ratio )
		sim.start( engine.!( sourceName, _ ), println( "Simulation ended" ) )
	}
}

class RabbitMQTest( sourceName : String, engine : RunningEngine, sim : EventSimulator, publisher : XmlEvent ⇒ IO[Unit] ) {
	def run = {
		import EventSimulator._
		val conn = FlowAmqp.connector( username, password, address ).cfg( testSetup andThen widgetSetup ).start().unsafePerformIO

		val source = new ReadyAmqpConsumer( "engineConsumer", "test", s ⇒ engine.!( "test", stringToXmlEvent( s ) ) ).start( conn.connx ).unsafePerformIO
		val client = new ReadyAmqpProducer( "test", "test" ).start( conn.connx ).unsafePerformIO

		sim.start( publisher, println( "Simulation ended" ) )
	}
}

class HttpTest() {

}