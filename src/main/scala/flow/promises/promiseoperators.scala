package flow.promises
import scala.collection.JavaConversions._
import flow.event.ProcessAdvancedEvent
import flow.actor.OutputBuilder
import flow.event.PromiseViolatedEvent
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
import flow.event.PromiseViolatedEvent
import flow.event.ValuedEvent
import flow.event.ProcessEvent

object PromiseViolationsBuilder {

  def validator(id: String) = new PromiseViolationsBuilder(id)

}

case class Promise(id: String, description: String, appliesToPred: ProcessEvent => Boolean, promisePred: ProcessEvent ⇒ Boolean){
  def isViolated(e: ProcessEvent)={
    println("IS VIOLATED["+id+"]: "+(appliesToPred(e)&&(!promisePred(e))))
    appliesToPred(e)&&(!promisePred(e))
  }
}

class PromiseViolationsState(promises: List[Promise]) extends OperatorState[ProcessAdvancedEvent, (List[PromiseViolatedEvent], ProcessAdvancedEvent)] {

  def apply(e: ProcessAdvancedEvent): ((List[PromiseViolatedEvent], ProcessAdvancedEvent), PromiseViolationsState) = {

    val violationEvents = getViolations(e);
    (violationEvents, new PromiseViolationsState(promises))
  }

  def getViolations(e: ProcessAdvancedEvent) = {

    //Adds the violation to the event
    def updateWithViolation(pae: ProcessAdvancedEvent, promise: Promise) = {
      var eventchainData = pae.eventchain.data
      eventchainData += "voilation|" + promise.id -> "true"
      ProcessAdvancedEvent(pae.timestamp, EventChain(pae.eventchain.id, pae.eventchain.events, eventchainData))
    }

    //Iterates over all defined promises to see which ones that are violated by this event. The result is a 
    //list of violation events
    promises.foldLeft((List[PromiseViolatedEvent](), e)) { (t, promise) ⇒
      if (promise.appliesToPred(e) && !promise.promisePred(e)) {
        (PromiseViolatedEvent(promise, updateWithViolation(e, promise)) :: t._1, t._2)
      } else t
    }

  }

}

object PromiseRepository {
  def appliesToPred(e: ProcessEvent) = true
  def promisePred(e: ProcessEvent) = e.eventchain.interval.toDurationMillis() < 12 * 3600 * 1000
  val promises: List[Promise] = List[Promise](Promise("B1", "Prosesser skal ikke ta mer enn 12 timer", appliesToPred, promisePred))
}

class PromiseViolationsBuilder(id: String) extends OperatorBuilder {

  lazy val operator = {
	val inputRouter = handle[ProcessAdvancedEvent] {
		case OperatorInput(_, msg: ProcessAdvancedEvent) ⇒ msg 
	}

    val outputRouter: Tuple2[List[PromiseViolatedEvent], ProcessAdvancedEvent] ⇒ List[OperatorOutput] = { (t) ⇒
      OperatorOutput(id + ".out", t._2) :: t._1.map(v ⇒ OperatorOutput(id + ".violations", v))
    }

    new Operator(id, inputRouter, outputRouter, new PromiseViolationsState(PromiseRepository.promises))
  }

  val update = OutputBuilder(this, OutputPortId(id + ".updated"))
  val in = InputBuilder(this, InputPortId(id + ".in"))

  def update(context: Context) = context + PortBinding(InputPortId(id + ".in"), OperatorId(id)) + operator

}
