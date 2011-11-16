package flow.businessrules

import event.BusinessRuleViolatedEvent
import event.ProcessAdvancedEvent
import flow.actor.OperatorState
import flow.event.BusinessRuleViolatedEvent
import flow.event.ProcessAdvancedEvent
import flow.event.ProcessAdvancedEvent
import flow.event.ProcessAdvancedEvent
import flow.event.ProcessAdvancedEvent
import scalaz.Scalaz._

//object HistogramBuilder {
//
//  de
//object HistogramBuilder {
//
//  def histogram(id: String, windowLength: Duration) = new HistogramBuilder(id, windowLength)
//
//}

case class BusinessRule(id: String, description: String, pred: ProcessAdvancedEvent ⇒ Boolean)

class BusinessRuleViolationsState(businessRules: List[BusinessRule]) extends OperatorState[ProcessAdvancedEvent, (List[BusinessRuleViolatedEvent], ProcessAdvancedEvent)] {

  def apply(e: ProcessAdvancedEvent): ((List[BusinessRuleViolatedEvent], ProcessAdvancedEvent), BusinessRuleViolationsState) = {

    val violationEvents = getViolations(e);
    ((violationEvents, e), new BusinessRuleViolationsState(businessRules))
  }

  def getViolations(e: ProcessAdvancedEvent): List[BusinessRuleViolatedEvent] = {

    List[BusinessRuleViolatedEvent]()
//	  businessRules.foreach{ businessRule =>
//  println(businessRule)
}  


}

//class HistogramBuilder(id: String, windowLength: Duration) extends OperatorBuilder {
//  lazy val operator = {
//    val inputRouter: PartialFunction[Any, Either[ProcessEndedEvent, TimerEvent]] = {
//      case OperatorInput(_, e: ProcessEndedEvent) ⇒ Left(e)
//      case OperatorInput(_, t: TimerEvent) ⇒ Right(t)
//    }
//
//    val outputRouter: Option[UpdatedHistogramEvent] ⇒ List[OperatorOutput[UpdatedHistogramEvent]] = { o ⇒
//      o.fold(e ⇒ List(OperatorOutput(id + ".updated", e)), List())
//    }
//
//    new Operator(id, inputRouter, outputRouter, new HistogramState(windowLength, List()))
//  }
//
//  val update = OutputBuilder(this, OutputPortId(id + ".updated"))
//  val in = InputBuilder(this, InputPortId(id + ".in"))
//
//  def update(context: Context) = context + PortBinding(InputPortId(id + ".in"), OperatorId(id)) + operator
}
