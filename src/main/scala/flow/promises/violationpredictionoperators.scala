package flow.promises
import scala.collection.JavaConversions._
import flow.actor.Routers._
import flow.actor.Context
import flow.actor.InputBuilder
import flow.actor.InputPortId
import flow.actor.Operator
import flow.actor.OperatorBuilder
import flow.actor.OperatorId
import flow.actor.OperatorInput
import flow.actor.OperatorOutput
import flow.actor.OperatorState
import flow.actor.OutputBuilder
import flow.actor.OutputPortId
import flow.actor.PortBinding
import flow.event.EventChain
import flow.event.ObservationEvent
import flow.event.PredictedViolationEvent
import flow.event.PredictedViolationEvent
import flow.event.ProcessAdvancedEvent
import flow.event.ProcessAdvancedEvent
import flow.event.ProcessAdvancedEvent
import flow.event.ProcessEndedEvent
import flow.event.ProcessEndedEvent
import flow.event.UpdatedTrainingsetEvent
import scalaz.Scalaz._
import scalaz._
import weka.core.Instance
import weka.core.Instances
import weka.classifiers.`lazy`.IBk

object ViolationPredictionBuilder {

  def predictor(id: String) = new ViolationPredictionBuilder(id)

}

class ViolationPredictionState(trainingsets: Map[Promise, (Instances, IBk)]) extends OperatorState[Either[UpdatedTrainingsetEvent, ProcessAdvancedEvent], Option[PredictedViolationEvent]] {

  def apply(e: Either[UpdatedTrainingsetEvent, ProcessAdvancedEvent]): (Option[PredictedViolationEvent], ViolationPredictionState) = {
    e match {
      case Left(datasetUpdatedEvent) ⇒ {
        val dataset = datasetUpdatedEvent.dataset
   		val classifier = DatasetUtils.createPredictionModel(dataset)
        (None, new ViolationPredictionState(trainingsets ++ Map(datasetUpdatedEvent.promise -> (dataset, classifier))))
      }
      case Right(processEvent) ⇒ {
        //Predict violations on each incoming process advanced event
        //TODO: Only predict warning for processes where the respective business rule is not broken already.
        (predictViolations(processEvent), new ViolationPredictionState(trainingsets))
      }
    }
  }

  def predictViolations(e: ProcessAdvancedEvent) = {


	 val violations = trainingsets.foldLeft(List[(String, Double)]()) {(list,tuple)=>
      //Extracting an array of class membership probabilities
          val classMembershipProbabilities = tuple._2._2.distributionForInstance(DatasetUtils.createInstance(e, tuple._2._1, tuple._1))

          //Creating a textual representation of the predictions.
          if (classMembershipProbabilities.length == 2 && classMembershipProbabilities(0)>0.5d ){
        	  println("We have an likely violation of promise: "+tuple._1.id)
        	  (tuple._1.id, classMembershipProbabilities(0)) :: list
          } else {
            list
          }
          
        }
    if (!violations.isEmpty)
      Some(PredictedViolationEvent(violations, e))
    else
      None
  }

  def getEventChain(e2eProcess: Either[ProcessAdvancedEvent, ProcessEndedEvent]): EventChain = {
    if (e2eProcess.isLeft)
      e2eProcess.left.get.eventchain
    else
      e2eProcess.right.get.eventchain
  }

  def extractViolations(e: ObservationEvent, e2eProcess: Either[ProcessAdvancedEvent, ProcessEndedEvent]) = {
    val eventchain = getEventChain(e2eProcess)

    eventchain.events.list.foldRight(List[String]()) { (e, violationsFound) ⇒
      PromiseRepository.promises.foreach { promise ⇒
        if (!violationsFound.contains(promise.id) && e.values.containsKey("violation|" + promise.id)) {
          promise.id :: violationsFound
        }
      }
      violationsFound
    }
  }

}

class ViolationPredictionBuilder(id: String) extends OperatorBuilder {

  def extractAttributeMetaData(attributeName: String) = {
    var attNameProcessed = false;
    attributeName.split("|").foldLeft((List[String]())) { (dataList, s) ⇒
      if (attNameProcessed) {
        s :: dataList
      } else {
        attNameProcessed = true
        dataList
      }
    }
  }

  lazy val operator = {

    val inputRouter = handle[Either[UpdatedTrainingsetEvent, ProcessAdvancedEvent]]({
      case OperatorInput(_, e: UpdatedTrainingsetEvent) ⇒ Left(e)
      case OperatorInput(_, t: ProcessAdvancedEvent) ⇒ Right(t)
    })

    val outputRouter: Option[PredictedViolationEvent] ⇒ List[OperatorOutput] = { o ⇒
      o.fold(e ⇒ List(OperatorOutput(id + ".updated", e)), List())
    }

    val map = Map[Promise, (Instances, IBk)]()
    new Operator[Either[UpdatedTrainingsetEvent, ProcessAdvancedEvent], Option[PredictedViolationEvent]](id, inputRouter, outputRouter, new ViolationPredictionState(map))
  }

  val update = OutputBuilder(this, OutputPortId(id + ".updated"))
  val in = InputBuilder(this, InputPortId(id + ".in"))

  def update(context: Context) = context + PortBinding(InputPortId(id + ".in"), OperatorId(id)) + operator


}
