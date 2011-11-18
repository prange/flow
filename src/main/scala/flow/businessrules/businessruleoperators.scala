package flow.businessrules

import flow.operator.Transform._
import flow.actor.Context
import flow.actor.InputBuilder
import flow.actor.InputPortId
import flow.actor.Operator
import flow.actor.OperatorBuilder
import flow.actor.OperatorId
import flow.actor.OperatorInput
import flow.actor.OperatorState
import flow.actor.OutputBuilder
import flow.actor.OutputPortId
import flow.actor.PortBinding
import flow.event.ProcessAdvancedEvent
import flow.event.ProcessAdvancedEvent
import scalaz.Scalaz._
import flow.actor.OperatorOutput
import flow.event.BusinessRuleViolatedEvent
import flow.event.ProcessAdvancedEvent
import flow.event.EventChain

object BusinessRuleViolationsBuilder {

  def validator(id: String) = new BusinessRuleViolationsBuilder(id)

}

case class BusinessRule(id: String, description: String, pred: ProcessAdvancedEvent ⇒ Boolean)

class BusinessRuleViolationsState(businessRules: List[BusinessRule]) extends OperatorState[ProcessAdvancedEvent, (List[BusinessRuleViolatedEvent], ProcessAdvancedEvent)] {

  def apply(e: ProcessAdvancedEvent): ((List[BusinessRuleViolatedEvent], ProcessAdvancedEvent), BusinessRuleViolationsState) = {

    val violationEvents = getViolations(e);
    (violationEvents, new BusinessRuleViolationsState(businessRules))
  }

  def getViolations(e: ProcessAdvancedEvent) = {
    def updateWithViolation(pae: ProcessAdvancedEvent, rule: BusinessRule) = {
    	val g = pae.eventchain.data.toZipper.+:(elem("violation",rule.id))
    	ProcessAdvancedEvent(pae.timestamp, EventChain(pae.eventchain.id,pae.eventchain.events,g))
    }

    businessRules.foldLeft((List[BusinessRuleViolatedEvent](), e)) { (t, rule) ⇒
      if (!rule.pred(e))
    	e.eventchain.update()  
        (BusinessRuleViolatedEvent(rule, e) :: t._1, t._2)
      else (t._1, t._2)
    }

  }

}

class BusinessRuleViolationsBuilder(id: String) extends OperatorBuilder {
  lazy val operator = {
    val inputRouter: PartialFunction[Any, ProcessAdvancedEvent] = {
      case OperatorInput(_, e: ProcessAdvancedEvent) ⇒ e
    }

    val outputRouter: Tuple2[List[BusinessRuleViolatedEvent], ProcessAdvancedEvent] ⇒ List[OperatorOutput[_]] = { (t) ⇒
      OperatorOutput(id + ".out", t._2) :: t._1.map(v ⇒ OperatorOutput(id + ".violations", v))
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
