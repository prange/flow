package flow.actor

import scalaz.Scalaz._
import scalaz.effects._
import akka.event.EventHandler
import scala.io.Source
import flow.event.XmlEvent
import com.rabbitmq._
import client._

case class Username( name : String )
case class Password( pw : String )
case class Address( host : String, port : Int )

object Address {
	def apply( host : String ) : Address = Address( host, 5672 )
}

object FlowAmqp {
	type AmqpConfig = Channel ⇒ Channel
	def cfg( f : Channel ⇒ Unit ) = new AmqpConfig {
		def apply( c : Channel ) : Channel = { f( c ); c }
	}
	def connector( username : Username, password : Password, address : Address ) = ReadyAmqpConnector( username, password, address )
}

object ReadyAmqpConnector {
	def apply( username : Username, password : Password, address : Address ) = new ReadyAmqpConnector( username, password, address, List() )

}
import FlowAmqp._
class ReadyAmqpConnector( username : Username, password : Password, address : Address, configurations : List[AmqpConfig] ) {

	def cfg( f : AmqpConfig ) = new ReadyAmqpConnector( username, password, address, f :: configurations )

	def start() = io {
		val factory = new ConnectionFactory()
		factory.setUsername( username.name )
		factory.setPassword( password.pw )
		factory.setHost( address.host )
		factory.setPort( address.port )
		val connection = factory.newConnection()

		val channel = connection.createChannel()
		configurations.foreach( _.apply( channel ) )
		channel.close()
		new RunningAmqpConnector( connection, this )
	}
}

class RunningAmqpConnector( connection : Connection, state : ReadyAmqpConnector ) {

	def connx = connection

	def stop() = io {
		connection.close()
		state
	}
}

class ReadyAmqpProducer( id : String, exchangeName : String ) {

	def start( connection : Connection ) = io {
		val channel = connection.createChannel()
		new RunningAmqpProducer( channel, exchangeName, this )
	}
}

class RunningAmqpProducer( channel : Channel, exchangeName : String, state : ReadyAmqpProducer ) extends ( String ⇒ IO[Unit] ) {

	def apply( message : String ) : IO[Unit] = io {
		val messageBodyBytes = message.getBytes()
		channel.basicPublish( exchangeName, "somekey", null, messageBodyBytes )
	}

	def stop() = io {
		channel.close()
		state
	}
}

class ReadyAmqpConsumer( id : String, queueName : String, f : String ⇒ IO[Unit] ) {

	import flow.data.Parser._

	def start( connection : Connection ) = io {
		def byteToString( bytes : Array[Byte] ) = new String( bytes )

		val autoAck = false
		val channel = connection.createChannel()

		val consumer = new DefaultConsumer( channel ) {
			override def handleDelivery( consumerTag : String, envelope : Envelope, properties : AMQP.BasicProperties, body : Array[Byte] ) {
				val routingKey = envelope.getRoutingKey
				val contentType = properties.getContentType
				val deliveryTag = envelope.getDeliveryTag
				f( new String( body ) ).unsafePerformIO
				channel.basicAck( deliveryTag, false );
			}
		}
		val cid = channel.basicConsume( queueName, autoAck, id, consumer )
		new RunningAmqpConsumer( consumer, this )
	}
}

class RunningAmqpConsumer( consumer : DefaultConsumer, state : ReadyAmqpConsumer ) {
	def stop() = io {
		consumer.getChannel().close()
		state
	}

}

