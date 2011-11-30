package flow.operator
import flow.actor.Routers.handle
import flow.actor.Routers.oneOutputRouter
import flow.actor.Context
import flow.actor.InputBuilder
import flow.actor.InputPortId
import flow.actor.Operator
import flow.actor.OperatorBuilder
import flow.actor.OperatorId
import flow.actor.OperatorState
import flow.actor.OutputBuilder
import flow.actor.OutputPortId
import flow.actor.PortBinding
import flow.event.ProcessEndedEvent
import flow.event.ProcessStartedEvent
import flow.event.HourTimer
import flow.actor.OperatorInput

object InProcessCounterBuilder {

	def count( name : String, buckets:Int ) = new InProcessCounterStateBuilder( name,buckets )

}

case class InProcessCountChanged( newValue : List[Int] )

class InProcessCounterState( count : List[Int] ) extends OperatorState[Either[HourTimer, Either[ProcessStartedEvent, ProcessEndedEvent]], Option[InProcessCountChanged]] {

	def apply( input : Either[HourTimer, Either[ProcessStartedEvent, ProcessEndedEvent]] ) : ( Option[InProcessCountChanged], InProcessCounterState ) = {

		def newDay = ( None, new InProcessCounterState( 0 :: ( count.reverse.tail.reverse ) ) )
		def update( updatedCount : List[Int] ) = ( Some( InProcessCountChanged( updatedCount ) ), new InProcessCounterState( updatedCount ) )
		def increment = update( ( count.head + 1 ) :: count.tail )
		def decrement = update( ( count.head - 1 ) :: count.tail )
		input.fold( timer ⇒ newDay, either ⇒ either.fold( started ⇒ increment, ended ⇒ decrement ) )
	}

}

class InProcessCounterStateBuilder( id : String, bucketCount : Int ) extends OperatorBuilder {
	import flow.actor.Routers._

	val inputrouter = handle[Either[HourTimer, Either[ProcessStartedEvent, ProcessEndedEvent]]] {
		case OperatorInput( _, t@HourTimer( 1, time ) ) ⇒ Left( t )
		case OperatorInput( _, pse : ProcessStartedEvent ) ⇒ Right( Left[ProcessStartedEvent, ProcessEndedEvent]( pse ) )
		case OperatorInput( _, pee : ProcessEndedEvent ) ⇒ Right( Right[ProcessStartedEvent, ProcessEndedEvent]( pee ) )
	}

	lazy val operator =
		new Operator( id, inputrouter, optionOutputRouter[InProcessCountChanged]( id+".changed" ), new InProcessCounterState( List.fill( bucketCount )( 0 ) ) )
	val changed = OutputBuilder( this, OutputPortId( id+".changed" ) )
	val in = InputBuilder( this, InputPortId( id+".in" ) )
	def update( context : Context ) = context + PortBinding( InputPortId( id+".in" ), OperatorId( id ) ) + operator
}