package flow.operator
import flow._
import akka.actor.Actor
import akka.actor.ActorRef
import org.joda.time.DateTime
import akka.event.EventHandler
import scalaz._
import Scalaz._
import effects._
import javax.swing.InputMap
import flow.event.XmlEvent
import scala.xml.persistent.SetStorage
import flow.event.EventChain
import flow.event.ObservationEvent
import flow.event.ProcessAdvancedEvent


//Mix in this trait for easy operator creation
trait BaseOperatorBuilder {
	def source[I](id:String)(implicit m:Manifest[I]) = new Source[I,I](id)
	def filter[I, O]( id : String, f : I ⇒ Boolean )( implicit m : Manifest[I] ) = new Filter( id, f )
	def transformer[I,O]( id : String, f : I ⇒ List[O] )( implicit m : Manifest[I] ) = new Transformer( id, f )
	def publisher[I]( id : String, p : Publisher[I] )( implicit m : Manifest[I] ) = new PublisherOperator( id, p )
	def printer[I](id:String,p:I=>String)(implicit m:Manifest[I]) = publisher(id,new Publisher[I]{def publish(data:I) = io{data.toString}})
}

class Source[I,O]( id : String )( implicit m : Manifest[I] ) extends StatelessOperator[I, I] {
	val out = output[O]( id.toOpId, "out".toPortId )
	val * = statelessInput[I, I]( id.toOpId, "in".toPortId, identity ) ~> out
}

class Filter[I]( id : String, predicate : I ⇒ Boolean )( implicit m : Manifest[I] ) extends StatelessOperator[I, Either[I, I]] {
	val g : I ⇒ Either[I, I] = i ⇒ if ( predicate( i ) ) Left( i ) else Right( i )
	val in = statelessInput[I, Either[I, I]]( id.toOpId, "in".toPortId, g )
	val filtered = output[I]( id.toOpId, "filtered".toPortId )
	val unfiltered = output[I]( id.toOpId, "unfiltered".toPortId )
	val * = in ~> ( filtered or unfiltered )
}

class Transformer[I,O]( id : String, f : I ⇒ List[O] )( implicit m : Manifest[I] ) extends StatelessOperator[I, List[O]] {
	val in = statelessInput[I, List[O]]( id.toOpId, "in".toPortId, f )
	val out = output[O]( id.toOpId, "out".toPortId )
	val * = in ~> out.list
}

trait Publisher[I] {
	def publish( data : I ) : IO[Unit]
}

class PublisherOperator[I]( id : String, state : Publisher[I] )( implicit m : Manifest[I] ) extends SinkOperator[I, Publisher[I]] {
	val f : I ⇒ Publisher[I] ⇒ IO[Unit] = input ⇒ publisher ⇒ publisher.publish( input )
	val in = effectInput[I, Unit, Publisher[I]]( id.toOpId, "in".toPortId, f )
	val * = in
}

