package flow.operator
import flow._
import flow.event._
import flow.service.Store
import flow.service.Datastore
import flow.event.EventChain
import flow.event.EventChain
import flow.actor.OperatorState
import scalaz._
import Scalaz._
import flow.actor.OutputBuilder
import flow.actor.OperatorBuilder
import flow.actor.InputBuilder
import flow.actor.OperatorOutput
import flow.actor.OperatorInput
import flow.actor.Operator
import flow.actor.Context
import flow.actor.InputPortId
import flow.actor.PortBinding
import flow.actor.OutputPortId
import flow.actor.FilterState
import flow.actor.OperatorId

object AssembleBuilder {

	def assemble( id : String, cut : EventChainCut, idExtractor : XmlEvent ⇒ String ) = {

	}

}

trait EventChainCut {
	type Exists[T] = Option[T]
	def test( event : XmlEvent ) : CutChoice
}

trait CutChoice {
	def fold[T]( none : ⇒ T, before : ⇒ T, after : ⇒ T ) : T
}

case class CutBefore( predicate : Predicate[XmlEvent] ) extends EventChainCut {

	def test( event : XmlEvent ) : CutChoice = new CutChoice {
		def fold[T]( none : ⇒ T, before : ⇒ T, after : ⇒ T ) : T = if ( predicate( event ) ) before else none
	}

}

case class CutAfter( predicate : Predicate[XmlEvent] ) extends EventChainCut {

	def test( event : XmlEvent ) : CutChoice = new CutChoice {
		def fold[T]( none : ⇒ T, before : ⇒ T, after : ⇒ T ) : T = if ( predicate( event ) ) after else none
	}

}

case class InMemoryAssembleState( cutter : EventChainCut, activeProcesses : Map[String, EventChain], idExtractor : XmlEvent ⇒ String ) extends OperatorState[XmlEvent, List[ProcessEvent]] {

	def update( newActiceProcesses : Map[String, EventChain] ) = InMemoryAssembleState( cutter, newActiceProcesses, idExtractor )

	def apply( event : XmlEvent ) = {
		def createOnly( id : String, event : XmlEvent ) = {
			val chain = EventChain.from( id, event )
			( ProcessStartedEvent( event.eventTime, chain ) :: Nil, update( activeProcesses + ( id -> chain ) ) )
		}

		def advanceOnly( id : String, event : XmlEvent ) = {
			val chain = activeProcesses( id )
			val newChain = event :: chain
			( ProcessAdvancedEvent( event.eventTime, newChain ) :: Nil, update( activeProcesses + ( id -> newChain ) ) )
		}

		def endOnly( id : String, event : XmlEvent ) = {
			val chain = activeProcesses( id )
			val newChain = event :: chain
			val time = event.eventTime
			( ProcessAdvancedEvent( time, newChain ) :: ProcessEndedEvent( time, newChain ) :: Nil, update( activeProcesses - id ) )
		}

		def endAndCreate( id : String, event : XmlEvent ) = {
			val chain = activeProcesses( id )
			val time = event.eventTime
			val newChain = EventChain.from( id, event )
			( ProcessEndedEvent( time, chain ) :: ProcessStartedEvent( time, newChain ) :: Nil, update( activeProcesses + ( id -> newChain ) ) )
		}

		val id = idExtractor( event )

		val chainO = activeProcesses.get( id )
		chainO.fold( chain ⇒ cutter.test( event ).fold( advanceOnly _, endAndCreate _, endOnly _ ), createOnly _ )( id, event )
	}

}

class AssembleBuilder( id : String, cut : EventChainCut, idExtractor : XmlEvent ⇒ String ) extends OperatorBuilder {
	lazy val operator = {
		val inputRouter : PartialFunction[Any, XmlEvent] = {
			case OperatorInput( _, e : XmlEvent ) ⇒ e
		}

		val outputRouter : List[ProcessEvent] ⇒ List[OperatorOutput[ProcessEvent]] = l ⇒ l.foldLeft( List[OperatorOutput[ProcessEvent]]() ) { ( list, event ) ⇒
			event match {
				case ps : ProcessStartedEvent ⇒ OperatorOutput( id+".started", ps ) :: list
				case pa : ProcessAdvancedEvent ⇒ OperatorOutput( id+".advanced", pa ) :: list
				case pe : ProcessEndedEvent ⇒ OperatorOutput( id+".ended", pe ) :: list
			}
		}
		new Operator( id, inputRouter, outputRouter, new InMemoryAssembleState( cut, Map.empty, idExtractor) )
	}
	val started = OutputBuilder( this, OutputPortId( id+".started" ) )
	val advanced = OutputBuilder( this, OutputPortId( id+".advanced" ) )
	val ended = OutputBuilder( this, OutputPortId( id+".ended" ) )
	val in = InputBuilder( this, InputPortId( id+".in" ) )
	def update( context : Context ) = context + PortBinding( InputPortId( id+".in" ), OperatorId( id ) ) + operator
}


