package flow.actor
import akka.amqp.AMQP._
import akka.amqp._
import akka.actor._
import Actor._
import scalaz.Scalaz._
import scalaz.effects._
import akka.amqp.AMQP.ConsumerParameters
import akka.event.EventHandler
import com.rabbitmq.client.Address
import scala.io.Source
import flow.event.XmlEvent

object FlowAmqp {

	def connector(address:Address) = new ReadyAmqpConnector(address)
	
}
// new Address( "myhost.com", 5672 )
class ReadyAmqpConnector(address:Address, addresses:Address*) {

	def start() = io {
		val myAddresses = address +: addresses

		//Connection callback
		val connxCallback = actorOf( new Actor {
			def receive = {
				case Connected ⇒ EventHandler.info( this, "Connection callback: Connected!" )
				case Reconnecting ⇒ EventHandler.info( this, "Connection callback: Reconnecting!" )
				case Disconnected ⇒ EventHandler.info( this, "Connection callback: Disconnected!" )
			}
		} )
		val connectionParameters = ConnectionParameters( myAddresses.toArray, "guest", "askAninja", connectionCallback = Some( connxCallback ) )

		val connection = AMQP.newConnection( connectionParameters ).start()
		new RunningAmqpConnector(connection,this)
	}
}

class RunningAmqpConnector(connection:ActorRef,state:ReadyAmqpConnector){
	
	def connx = connection
	
	def stop() = io {
		connection.stop()
		state
	}
}


class ReadyAmqpSink( id : String, queueName : String ) {
	val exchangeParameters = ExchangeParameters( queueName, Direct )

	def start( connection : ActorRef ) = io {
		val producer = AMQP.newProducer( connection, ProducerParameters( Some( exchangeParameters ), producerId = Some( id ) ) ).start()
		new RunningAmqpSink( producer, this )
	}
}

class RunningAmqpSink( producer : ActorRef, state : ReadyAmqpSink ) extends ( XmlEvent ⇒ Unit ) {

	def apply( event : XmlEvent ) : Unit = producer ! Message( event.data.toString.getBytes, "some.routing.key" )

	def stop() = io {
		producer.stop()
		state
	}
}

class ReadyAmqpSource( queueName : String, engine : RunningEngine ) {

	import flow.data.Parser._

	val exchangeParameters = ExchangeParameters( queueName, Direct )

	def start( connection : ActorRef ) = io {
		def byteToString( bytes : Array[Byte] ) = new String( bytes )
		def parseString( string : String ) = parseEvent( _.fromSource( Source.fromString( string ) ) )
		def parseBytes( bytes : Array[Byte] ) = toEvent( parseString( byteToString( bytes ) ) )
		val myConsumer = AMQP.newConsumer( connection, ConsumerParameters( "some.routing.key", actorOf( new Actor {
			def receive = {
				case Delivery( payload, _, _, _, _, _ ) ⇒ engine.!( queueName, parseBytes( payload ) ).unsafePerformIO
			}
		} ), None, Some( exchangeParameters ) ) ).start()
		new RunningAmqpSource( myConsumer, this )
	}
}

class RunningAmqpSource( consumer : ActorRef, state : ReadyAmqpSource ) {
	def stop() = io {
		consumer.stop()
		state
	}

}

