package flow.actor
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

case class InputPortId( id : String )

case class OutputPortId( id : String ) {
	def -->( in : InputPortId ) = Wire( this, in )
}

case class OperatorId( id : String )

case class Wire( from : OutputPortId, to : InputPortId )

case class PortBinding( port : InputPortId, operator : OperatorId )

case class OperatorOutput[+M]( from : String, message : M ) {
	def toInput( to : String ) = OperatorInput( to, message )
}

case class OperatorInput[+M]( to : String, message : M )

trait OperatorState[I, O] extends ( I ⇒ ( O, OperatorState[I, O] ) )

class Operator[I, O, M]( ident : String, inputRouter : PartialFunction[Any,I], outputRouter : O ⇒ List[OperatorOutput[M]], s : OperatorState[I, O] ){self=>
	def id = ident
	type HandleReply = ActorRef => IO[Unit]
	private var state : OperatorState[I, O] = s

	private val updateState : OperatorState[I,O] => ActorRef => IO[Unit] = s =>a => io{state = s}
	private val sendReply : List[_] => ActorRef => IO[Unit] = l => ref =>  io{l.foreach(ref reply _)}
	private val update:(O,OperatorState[I,O])=>ActorRef => IO[Unit] = {(o,s)=> a =>
		 for{
			 _<- updateState(s)(a);
			 _<-sendReply(outputRouter(o)) (a)
			 } yield()
	}
	def handle : PartialFunction[Any, HandleReply] = {
		inputRouter andThen state andThen {t=> update(t._1,t._2)}  orElse {
			case u @ _ =>{ (a:ActorRef)=>io{EventHandler.warning(self,"No port found for %s at %s" format(u,id))}}
		}
	}
}

class FilterState( pred : XmlEvent ⇒ Boolean ) extends OperatorState[XmlEvent, Either[XmlEvent, XmlEvent]] {
	def apply( e : XmlEvent ) = {
		if ( pred( e ) )
			( Left( e ), this )
		else
			( Right( e ), this )
	}
}


case class TransformerState( f : XmlEvent ⇒ XmlEvent ) extends OperatorState[XmlEvent, XmlEvent] {
	def apply( e : XmlEvent ) = ( f( e ), this )
}


case class SinkState( f : Any ⇒ Unit ) extends OperatorState[Any, Unit] {
	def apply( e : Any ) = ( f( e ), this )
}

