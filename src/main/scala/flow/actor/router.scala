package flow.actor
import flow.event.XmlEvent
import akka.actor.Actor
import akka.actor.ActorRef
import akka.event.EventHandler
import scalaz.Scalaz._
import scalaz.effects.io
import flow.Time



class ReadyEngine( context : Context ) {

	def start() = io {
		val routes = context.connections.toList
		val operatorRefs = context.operators.map( o ⇒ ( o.id, Actor.actorOf( new OperatorActor(o.id,  o ) ).start() ) ).toMap //SIDEEFFECTS!!!!
		val bindings = context.bindings.map( b ⇒ ( b.port, operatorRefs( b.operator.id ) ) ).toMap

		val router = Actor.actorOf( new Router( routes, bindings ) ).start()
		new RunningEngine( bindings.values, router, this )
	}

}

class OperatorActor(val ident:String, o:Operator[_,_,_]) extends Actor{
	val id = ident
	def receive = {
		case m @ _ =>  o.handle(m)(self).unsafePerformIO
	}
}

class RunningEngine( operators : Iterable[ActorRef], router : ActorRef, state : ReadyEngine ) {

	def !( to : String, event : XmlEvent ) = io{ router ! OperatorOutput( to,  event ) }

	def stop = io {
		for ( operator ← operators ) {
			operator.stop()
		}
		router.stop()
		state
	}

}

class Router( routes : List[Wire], bindings : Map[InputPortId, ActorRef] ) extends Actor {

	def receive = {
		case o @ OperatorOutput( from, event ) ⇒ {
			val actors = routes.filter( w ⇒ w.from.id === from ).map( w ⇒ (w.to.id,bindings( w.to ) ) )
			if ( actors.size > 0 ) actors.foreach( t=> t._2 ! o.toInput(t._1) )
			else EventHandler.warning( this, "Route for %s not found".format( from ) )
		}
		case i @ OperatorInput( to, event ) ⇒ {
			bindings.get( InputPortId( to ) ).map( _ ! i ) getOrElse EventHandler.warning( this, "Target port %s not found".format( to ) )
		}
		case _ ⇒ EventHandler.warning( this, "Unknown message type received at router" )
	}
}


object Context {
	def apply() = new Context( Set(), Set(), Set() )
}

case class Context( operators : Set[Operator[_,_,_]], bindings : Set[PortBinding], connections : Set[Wire] ) {

	def +( op : Operator[_,_,_] ) = copy( operators = operators + op )
	def +( binding : PortBinding ) = copy( bindings = bindings + binding )
	def +( bindingI : Iterable[PortBinding] ) = copy( bindings = bindings ++ bindingI )
	def +( wire : Wire ) = copy( connections = connections + wire )
	
override def toString = {
		"Context[\n"+
		operators.mkString("Operators {\n","\n","\n}\n")+
		bindings.mkString("Bindings {\n","\n","\n}\n")+
		connections.mkString("Wires {\n","\n","\n}")+
		"]"
	}
}