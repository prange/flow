package flow.data
import flow.data._
import scalaz._
import Scalaz._

trait Message

trait ProcessQuery extends Message

trait EventQuery extends Message

trait Control extends Message

case class EventObservation( e : Event ) extends Control

case class AddEnrichment( e : Enricher ) extends Control

case class BuildChain( pred : Event ⇒ Boolean, id : Event ⇒ String ) extends Control

case class BuildProcess( query : EventChain ⇒ Boolean, cutpoint : List[CutPoint], enricher: Process=>Process ) extends Control

case class PredicateProcessQuery( pred : Process ⇒ Boolean ) extends ProcessQuery

case class PredicateEventQuery( pred : Event ⇒ Boolean ) extends EventQuery



