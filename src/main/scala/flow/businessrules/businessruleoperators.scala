package flow.businessrules

import flow.event.ProcessAdvancedEvent

//object BusinessRuleViolationsBuilder {
//
//  def validator(id: String) = new BusinessRuleViolationsBuilder(id)
//
//}

case class BusinessRule(id: String, description: String, pred: ProcessAdvancedEvent ⇒ Boolean)
//
//class BusinessRuleViolationsState(businessRules: List[BusinessRule]) extends OperatorState[ProcessAdvancedEvent, (List[BusinessRuleViolatedEvent], ProcessAdvancedEvent)] {
//
//  def apply(e: ProcessAdvancedEvent): ((List[BusinessRuleViolatedEvent], ProcessAdvancedEvent), BusinessRuleViolationsState) = {
//
//    val violationEvents = getViolations(e);
//    (violationEvents, new BusinessRuleViolationsState(businessRules))
//  }
//
//  def getViolations(e: ProcessAdvancedEvent) = {
//    def updateWithViolation(pae: ProcessAdvancedEvent, rule: BusinessRule) = {
//    	val g = pae.eventchain.data.toZipper.+:(elem("violation",rule.id))
//    	ProcessAdvancedEvent(pae.timestamp, EventChain(pae.eventchain.id,pae.eventchain.events,g))
//    }
//
//    businessRules.foldLeft((List[BusinessRuleViolatedEvent](), e)) { (t, rule) ⇒
//      if (!rule.pred(e)) {
//    	e.eventchain.events.head.values.put("voilation|"+br.id, "true")
//        (BusinessRuleViolatedEvent(rule, e) :: t._1, t._2)
//      } else t
//    }
//
//  }
//
//}

object BusinessRuleContainer {
	def pred(e: ProcessAdvancedEvent) = e.eventchain.interval.toDurationMillis() < 12 * 3600 * 1000
    val rules:List[BusinessRule] = List[BusinessRule](BusinessRule("B1", "Prosesser skal ikke ta mer enn 12 timer", pred))
}

//class BusinessRuleViolationsBuilder(id: String) extends OperatorBuilder {
//
//    lazy val operator = {
//    val inputRouter: PartialFunction[Any, ProcessAdvancedEvent] = {
//      case OperatorInput(_, e: ProcessAdvancedEvent) ⇒ e
//    }
//
//    val outputRouter:Tuple2[List[BusinessRuleViolatedEvent], ProcessAdvancedEvent]=>List[OperatorOutput] = {(t) ⇒ 
//      OperatorOutput(id+".out",t._2) :: t._1.map(v=>OperatorOutput(id+".violations",v))
//    }
//
//    new Operator(id, inputRouter, outputRouter, new BusinessRuleViolationsState(getBusinessRules()))
//  }
//
//  val update = OutputBuilder(this, OutputPortId(id + ".updated"))
//  val in = InputBuilder(this, InputPortId(id + ".in"))
//
//  def update(context: Context) = context + PortBinding(InputPortId(id + ".in"), OperatorId(id)) + operator
//}
