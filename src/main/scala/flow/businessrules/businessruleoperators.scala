package flow.businessrules
import scala.collection.JavaConversions._
import flow.event.ProcessAdvancedEvent
import flow.actor.OutputBuilder
import flow.event.BusinessRuleViolatedEvent
import flow.event.EventChain
import flow.actor.InputBuilder
import flow.actor.OperatorState
import flow.actor.OperatorOutput
import flow.actor.OperatorInput
import flow.actor.Operator
import flow.actor.OperatorBuilder
import flow.actor.Context
import flow.actor.InputPortId
import flow.actor.PortBinding
import flow.actor.OutputPortId
import flow.actor.OperatorId
import flow.actor.Routers._
import scalaz._
import Scalaz._

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

//    //Adds the violation to the event
//    def updateWithViolation(pae: ProcessAdvancedEvent, rule: BusinessRule) = {
//      var eventchainData = pae.eventchain.data
//      eventchainData += "voilation|" + rule.id -> "true"
//      ProcessAdvancedEvent(pae.timestamp, EventChain(pae.eventchain.id, pae.eventchain.events, eventchainData))
//    }

    //Iterates over all defined business rules to see which ones that are violated by this event. The result is a 
    //list of violation events
    businessRules.foldLeft((List[BusinessRuleViolatedEvent](), e)) { (t, rule) ⇒
      if (!rule.pred(e)) {
        (BusinessRuleViolatedEvent(rule, e) :: t._1, t._2)
      } else t
    }

  }

}

object BusinessRuleContainer {
  def pred(e: ProcessAdvancedEvent) = e.eventchain.interval.toDurationMillis() < 12 * 3600 * 1000
  val rules: List[BusinessRule] = List[BusinessRule](BusinessRule("B1", "Prosesser skal ikke ta mer enn 12 timer", pred))
}

class BusinessRuleViolationsBuilder(id: String) extends OperatorBuilder {

  lazy val operator = {
	val inputRouter = handle[ProcessAdvancedEvent] {
		case OperatorInput(_, msg: ProcessAdvancedEvent) ⇒ msg 
	}

    val outputRouter: Tuple2[List[BusinessRuleViolatedEvent], ProcessAdvancedEvent] ⇒ List[OperatorOutput] = { (t) ⇒
      OperatorOutput(id + ".out", t._2) :: t._1.map(v ⇒ OperatorOutput(id + ".violations", v))
    }

    new Operator(id, inputRouter, outputRouter, new BusinessRuleViolationsState(BusinessRuleContainer.rules))
  }

  val update = OutputBuilder(this, OutputPortId(id + ".updated"))
  val in = InputBuilder(this, InputPortId(id + ".in"))

  def update(context: Context) = context + PortBinding(InputPortId(id + ".in"), OperatorId(id)) + operator

}
