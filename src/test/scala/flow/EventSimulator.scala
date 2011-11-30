package flow
import scala.io.Source
import org.joda.time.DateTime
import org.joda.time.Duration
import com.codecommit.antixml.Elem
import com.codecommit.antixml.Group
import dispatch._
import flow.Time._
import flow.data.Parser
import flow.event.XmlEvent
import scalaz.Scalaz._
import scalaz.effects._
import scalaz._
import akka.actor.ActorRef
import flow.actor.FlowAmqp
import com.rabbitmq.client.Address
import flow.actor.RunningEngine
import flow.actor.ReadyAmqpSource
import flow.actor.ReadyAmqpSink

object EventSimulator {
	val filename = "sykkelmeldinger.xml"
		val protocol = "http://"
	val domain = "ec2-50-19-72-109.compute-1.amazonaws.com"
	val httpPort = "8080"
	val path = "/epcis/xservice"

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

	def start[T]( f : XmlEvent ⇒ Unit, endCallback : ⇒ T ) = io {

		val ff : XmlEvent ⇒ XmlEvent = e ⇒ { e }
		val latest = execs.map( _._1 ).max
		val timer = new JavaTimer( false )
		execs.foreach( t ⇒ timer.schedule( t._1 )( ( ff andThen f )( t._2 ) ) )
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
		sim.start( engine.!( sourceName, _ ).unsafePerformIO, println( "Simulation ended" ) )
	}
}

class RabbitMQTest( domainname : String, ratio : Int, sourceName : String, engine : RunningEngine ) {
	def run = {
		val conn = FlowAmqp.connector( new Address( domainname ) ).start().unsafePerformIO
		val sim = EventSimulator.init( Time.now.unsafePerformIO, ratio )

		val source = new ReadyAmqpSource( "test", engine ).start( conn.connx ).unsafePerformIO
		val client = new ReadyAmqpSink( "test", "test" ).start( conn.connx ).unsafePerformIO

		sim.start( client, println( "Simulation ended" ) )
	}
}

class HttpTest(){
	
}