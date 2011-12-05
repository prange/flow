package flow.dataprovider
import akka.actor.ActorRef
import scalaz.effects._
import flow.actor.ReadyAmqpProducer
import flow.actor.RunningAmqpProducer
import flow.actor.Publisher

object WidgetDataToJason {
	import net.liftweb.json._
	import net.liftweb.json.Serialization._
	implicit val formats = DefaultFormats // Brings in default date formats etc.

	def convert( data : List[WidgetDataUpdate] ) :List[String] = data.map(write( _ ))

}

class RabbitMQPublisher( actor : RunningAmqpProducer ) extends Publisher {

	def publish( data : List[String] ) = io {
		data.foreach( d ⇒ {actor( d ).unsafePerformIO} )
	}

}