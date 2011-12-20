package flow.actor
import org.joda.time.DateTime
import org.joda.time.Days
import org.joda.time.Duration
import org.joda.time.Hours
import org.joda.time.Minutes
import org.joda.time.Seconds

import akka.actor.Actor
import akka.actor.ActorRef
import akka.event.EventHandler
import flow.event.DayTimer
import flow.event.HourTimer
import flow.event.MinuteTimer
import flow.event.SecondTimer
import flow.operator.Operator
import flow.operator.OperatorId
import flow.operator.OperatorInput
import flow.operator.OperatorOutput
import flow.operator.PortId
import flow.operator.PortWire
import flow.operator.RouteBuilder
import flow.JavaTimer
import flow.Time
import scalaz.Scalaz._
import scalaz.effects.IO
import scalaz.effects.io

class ReadyOperatorEngine( routes : RouteBuilder ) {

	def start = {
		for {
			runningLifecycleManager ← new ReadyOperatorLifecycleManager( routes.operators ).start;
			runningRouter ← Router.start( routes.routes, runningLifecycleManager )
		} yield ( new RunningOperatorEngine( routes, runningRouter, runningLifecycleManager ) )
	}

}

class RunningOperatorEngine( routes : RouteBuilder, router : ActorRef, lifecycleManager : RunningOperatorLifecycleManager ) {

	def handle( id : String, msg : Any ) = io {
		router ! OperatorInput( OperatorId(id), PortId("*"), msg )
	}

	def stop = for ( _ ← lifecycleManager.stop ) yield ( new ReadyOperatorEngine( routes ) )

}

class OperatorActor( o : Operator ) extends Actor {
	def receive = {
		case msg @ _ ⇒ o.handle( msg ).foreach( eff ⇒ self.reply( eff.unsafePerformIO ) )
	}
}

class ReadyOperatorLifecycleManager( operators : List[Operator] ) {

	def start : IO[RunningOperatorLifecycleManager] = io {
		val actors = operators.map( o ⇒ Actor.actorOf( new OperatorActor( o ) ) )
		actors.foreach( ref ⇒ ref.start() )
		new RunningOperatorLifecycleManager( actors, this )
	}

}

class RunningOperatorLifecycleManager( val actors : List[ActorRef], state : ReadyOperatorLifecycleManager ) {
	def stop = io {
		actors.foreach( ref ⇒ ref.stop() )
		state
	}
}

object Router {

	def start( routes : List[PortWire], operators : RunningOperatorLifecycleManager ) = io {
		Actor.actorOf( new Router( routes, operators ) )
	}

}

class Router( routes : List[PortWire], operators : RunningOperatorLifecycleManager ) extends Actor {
	import flow.event.TimerEvent

	val bindings = operators.actors.map( op ⇒ ( op.id, op ) ).toMap

	def receive = {
		case o @ OperatorOutput( from, port, event ) ⇒ {
			val targets = routes.filter( w ⇒ ( w.fromOperator == from && w.fromPort == port ) ).map( w ⇒ ( w, bindings( w.toOperator.id ) ) )
			targets.foreach( t ⇒ if ( t._2.isRunning ) { t._2 ! o.toInput( t._1.toOperator, t._1.toPort ) } )
		}
		case t @ OperatorInput( _, _, te : TimerEvent ) ⇒ {
			bindings.values.toSet[ActorRef].foreach( ref ⇒ ref ! t )
		}
		case i @ OperatorInput( to, _, event ) ⇒ {
			bindings.get( to.id ).map( _ ! i ) getOrElse EventHandler.warning( this, "Target port %s not found".format( to ) )
		}
		case _ ⇒ EventHandler.warning( this, "Unknown message type received at router" )
	}
}

class TimerEventSource( callback : ActorRef ) {

	def start() = io {
		def time = Time.now.unsafePerformIO
		def sec = Time.nextSecondAfter( time )
		def min = Time.nextMinuteAfter( time )
		def hour = Time.nextHourAfter( time )
		def midnight = Time.nextMidnightAfter( time )
		val timer = new JavaTimer( true )
		timer.schedule( sec, Seconds.seconds( 1 ).toStandardDuration() ) { if ( callback.isRunning ) callback ! new OperatorInput( OperatorId( "*" ), PortId( "*" ), SecondTimer( Time.now.unsafePerformIO ) ) }
		timer.schedule( min, Minutes.minutes( 1 ).toStandardDuration() ) { if ( callback.isRunning ) callback ! new OperatorInput( OperatorId( "*" ), PortId( "*" ), MinuteTimer( Time.now.unsafePerformIO ) ) }
		timer.schedule( min, Hours.hours( 1 ).toStandardDuration() ) { if ( callback.isRunning ) callback ! new OperatorInput( OperatorId( "*" ), PortId( "*" ), HourTimer( Time.now.unsafePerformIO.getHourOfDay(), Time.now.unsafePerformIO ) ) }
		timer.schedule( midnight.toInterval().getStart(), Days.days( 1 ).toStandardDuration() ) { if ( callback.isRunning ) callback ! new OperatorInput( OperatorId( "*" ), PortId( "*" ), DayTimer( Time.now.unsafePerformIO ) ) }
		new RunningTimerEventSource( timer, callback )
	}
}

class RunningTimerEventSource( timer : JavaTimer, callback : ActorRef ) {

	def stop() = io {
		timer.stop()
		println( "RunningTimerEventSource: Stopped" )
		new TimerEventSource( callback )
	}

}





