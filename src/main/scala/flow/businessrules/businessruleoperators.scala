package flow.businessrules

import flow.actor.OperatorState
import flow.event.BusinessRuleViolatedEvent
import flow.event.ProcessAdvancedEvent
import scalaz.Scalaz._
import flow.event.BusinessRuleViolatedEvent
import flow.event.ProcessEndedEvent
import flow.actor.OutputBuilder
import flow.event.TimerEvent
import flow.actor.InputBuilder
import flow.actor.OperatorOutput
import flow.event.UpdatedHistogramEvent
import flow.actor.OperatorInput
import flow.actor.Operator
import flow.actor.OperatorBuilder
import flow.actor.Context
import flow.actor.InputPortId
import flow.statistics.HistogramState
import flow.actor.PortBinding
import flow.actor.OutputPortId
import flow.event.ProcessAdvancedEvent
import flow.actor.OperatorId

object BusinessRuleViolationsBuilder {

  def validator(id: String) = new BusinessRuleViolationsBuilder(id)

}

case class BusinessRule(id: String, description: String, pred: ProcessAdvancedEvent ⇒ Boolean)

class BusinessRuleViolationsState(businessRules: List[BusinessRule]) extends OperatorState[ProcessAdvancedEvent, (List[BusinessRuleViolatedEvent], ProcessAdvancedEvent)] {

  def apply(e: ProcessAdvancedEvent): ((List[BusinessRuleViolatedEvent], ProcessAdvancedEvent), BusinessRuleViolationsState) = {

    val violationEvents = getViolations(e);
    ((violationEvents, e), new BusinessRuleViolationsState(businessRules))
  }

  def getViolations(e: ProcessAdvancedEvent): List[BusinessRuleViolatedEvent] = {

    businessRules.foldLeft(List[BusinessRuleViolatedEvent]()) { (list, rule) ⇒
      if (!rule.pred(e))
        BusinessRuleViolatedEvent(rule, e) :: list
      else list
    }

  }

}

class BusinessRuleViolationsBuilder(id: String) extends OperatorBuilder {
  lazy val operator = {
    val inputRouter: PartialFunction[Any, ProcessAdvancedEvent] = {
      case OperatorInput(_, e: ProcessAdvancedEvent) ⇒ e
    }

    val outputRouter:Tuple2[List[BusinessRuleViolatedEvent], ProcessAdvancedEvent]=>List[OperatorOutput[_]] = {(t) ⇒ 
      OperatorOutput(id+".out",t._2) :: t._1.map(v=>OperatorOutput(id+".violations",v))
    }

    def getBusinessRules() = {
      val rules = List[BusinessRule]()
      def pred(e: ProcessAdvancedEvent) = e.eventchain.interval.toDurationMillis() < 12 * 3600 * 1000
      BusinessRule("B1", "Prosesser skal ikke ta mer enn 12 timer", pred) :: rules
    }
    new Operator(id, inputRouter, outputRouter, new BusinessRuleViolationsState(getBusinessRules()))
  }

  
  val update = OutputBuilder(this, OutputPortId(id + ".updated"))
  val in = InputBuilder(this, InputPortId(id + ".in"))

  
  def update(context: Context) = context + PortBinding(InputPortId(id + ".in"), OperatorId(id)) + operator
}
