package flow.actor
import flow.event.XmlEvent
import akka.actor.Actor
import akka.actor.ActorRef
import akka.event.EventHandler
import scalaz.Scalaz._
import scalaz.effects.io
import flow.Time
import org.joda.time.Duration
import org.joda.time.DateTime
import org.joda.time.Seconds
import flow.event.SecondTimer
import org.joda.time.Minutes
import flow.event.MinuteTimer
import org.joda.time.Hours
import flow.event.HourTimer
import org.joda.time.Days
import flow.event.DayTimer

class ReadyEngine( context : Context ) {

	def start() = io {
		val routes = context.connections.toList
		val operatorRefs = context.operators.map( o ⇒ ( o.id, Actor.actorOf( new OperatorActor( o.id, o ) ).start() ) ).toMap
		val bindings = context.bindings.map( b ⇒ ( b.port, operatorRefs( b.operator.id ) ) ).toMap

		val router = Actor.actorOf( new Router( routes, bindings ) ).start()
		val timerSource = new TimerEventSource(router).start().unsafePerformIO
		new RunningEngine( bindings.values, router, this,timerSource )
	}

}

class OperatorActor( val ident : String, o : Operator[_, _, _] ) extends Actor {
	val id = ident
	def receive = {
		case m @ _ ⇒ o.handle( m )( self ).unsafePerformIO
	}
}

class RunningEngine( operators : Iterable[ActorRef], router : ActorRef, state : ReadyEngine,timerSource:RunningTimerEventSource ) {

	def !( to : String, event : XmlEvent ) = io { router ! OperatorOutput( to, event ) }

	def stop = io {
		for ( operator ← operators ) {
			operator.stop()
		}
		router.stop()
		timerSource.stop()
		state
	}

}

class Router( routes : List[Wire], bindings : Map[InputPortId, ActorRef] ) extends Actor {
	import flow.event.TimerEvent
	def receive = {
		case o @ OperatorOutput( from, event ) ⇒ {
			val actors = routes.filter( w ⇒ w.from.id === from ).map( w ⇒ ( w.to.id, bindings( w.to ) ) )
			actors.foreach( t ⇒ t._2 ! o.toInput( t._1 ) )
		}
		case t @ OperatorInput( _, te : TimerEvent ) ⇒ {
			bindings.values.toSet[ActorRef].foreach( ref ⇒ ref ! t )
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

case class Context( operators : Set[Operator[_, _, _]], bindings : Set[PortBinding], connections : Set[Wire] ) {

	def +( op : Operator[_, _, _] ) = copy( operators = operators + op )
	def +( binding : PortBinding ) = copy( bindings = bindings + binding )
	def +( bindingI : Iterable[PortBinding] ) = copy( bindings = bindings ++ bindingI )
	def +( wire : Wire ) = copy( connections = connections + wire )

	override def toString = {
		"Context[\n"+
			operators.mkString( "Operators {\n", "\n", "\n}\n" ) +
			bindings.mkString( "Bindings {\n", "\n", "\n}\n" ) +
			connections.mkString( "Wires {\n", "\n", "\n}" )+
			"]"
	}
}

class TimerEventSource( callback : ActorRef ) {

	def start() = io {
		def time = Time.now
		def sec = Time.nextSecondAfter( time )
		def min = Time.nextMinuteAfter( time )
		def hour = Time.nextHourAfter( time )
		def midnight = Time.nextMidnightAfter( time )
		val timer = new JavaTimer( true )
		timer.schedule( sec, Seconds.seconds( 1 ).toStandardDuration() ) { callback ! new OperatorInput( "*", SecondTimer( Time.now ) ) }
		timer.schedule( min, Minutes.minutes( 1 ).toStandardDuration() ) { callback ! new OperatorInput( "*", MinuteTimer( Time.now ) ) }
		timer.schedule( min, Hours.hours( 1 ).toStandardDuration() ) { callback ! new OperatorInput( "*", HourTimer( Time.now ) ) }
		timer.schedule( midnight.toInterval().getStart(), Days.days( 1 ).toStandardDuration() ) { callback ! new OperatorInput( "*", DayTimer( Time.now ) ) }
		new RunningTimerEventSource(timer,callback)
	}
}

class RunningTimerEventSource( timer : JavaTimer, callback : ActorRef ) {

	def stop() = io {
		timer.stop();
		new TimerEventSource( callback )
	}

}

trait TimerTask {
	def cancel()
}

class JavaTimer( isDaemon : Boolean ) {

	private[this] val underlying = new java.util.Timer( isDaemon )

	def schedule( when : DateTime )( f : ⇒ Unit ) = {
		val task = toJavaTimerTask( f )
		underlying.schedule( task, when.toDate )
		toTimerTask( task )
	}

	def schedule( when : DateTime, period : Duration )( f : ⇒ Unit ) = {
		val task = toJavaTimerTask( f )
		underlying.schedule( task, when.toDate, period.getMillis() )
		toTimerTask( task )
	}

	def stop() = underlying.cancel()

	private[this] def toJavaTimerTask( f : ⇒ Unit ) = new java.util.TimerTask {
		def run { f }
	}

	private[this] def toTimerTask( task : java.util.TimerTask ) = new TimerTask {
		def cancel() { task.cancel() }
	}
}



