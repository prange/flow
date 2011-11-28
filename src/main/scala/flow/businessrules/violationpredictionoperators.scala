package flow.businessrules
import scala.collection.JavaConversions._

import org.joda.time.Duration

import flow.actor.OperatorState
import flow.event.EventChain
import flow.event.ObservationEvent
import flow.event.PredictedViolationEvent
import flow.event.PredictedViolationEvent
import flow.event.ProcessAdvancedEvent
import flow.event.ProcessAdvancedEvent
import flow.event.ProcessAdvancedEvent
import flow.event.ProcessEndedEvent
import flow.event.ProcessEndedEvent
import scalaz.Scalaz._
import weka.classifiers.bayes.AODE
import weka.core.Attribute
import weka.core.Instance
import weka.core.Instances

class ViolationPredictionState(dataset: Instances, classifier: AODE) extends OperatorState[Either[ProcessEndedEvent, ProcessAdvancedEvent], Option[PredictedViolationEvent]] {
  def brandAttName = "brand"
  def modelAttName = "model"
  def totalPriceAttName = "totalPrice"
  def timeElapsedAttName = "totalTime"
  def classAttName = "class"
  def lastWaitingTimeAttName = "lastWaitingTime"
  def dispositionAttName = "businessLocation|action"
  def actionPathAttName = "path|action"
  def readpointPathAttName = "readpoint|action"
  def businessLocationPathAttName = "businessLocation|action"

  def occurrencesAttName(propertyName: String, propertyValue: String) = {
    "occurrences|" + propertyName + "|" + propertyValue
  }

  def apply(e: Either[ProcessEndedEvent, ProcessAdvancedEvent]): (Option[PredictedViolationEvent], ViolationPredictionState) = {
    e match {
      case Left(ended) ⇒ {
        // Updating prediction models for each process ending event
        updatePredictionModel(ended, dataset)
        (None, new ViolationPredictionState(dataset, classifier))
      }
      case Right(event) ⇒ {
        //Predict violations on each incoming process advanced event
        //TODO: Only predict warning for processes where the respective business rule is not broken already.
        (predictViolations(event), new ViolationPredictionState(dataset, classifier))
      }
    }
  }

  def updatePredictionModel(e: ProcessEndedEvent, dataset: Instances) = {
    
    //Update the prediction model by adding the respective instance
    val instance = createInstance(e.eventchain, Right(e), dataset)
    classifier.updateClassifier(instance)
    println("Updated prediction model with instance "+instance)
  }

  //Create a dataset instance representation of the given event chain
  def createInstance(eventchain: EventChain, e2eProcess: Either[ProcessAdvancedEvent, ProcessEndedEvent], dataset: Instances) = {
    val instance = new Instance(dataset.numAttributes())

    for (a ← dataset.enumerateAttributes) {

      val attribute = a.asInstanceOf[Attribute]
      if (attribute.name().equals(actionPathAttName))
        instance.setValue(attribute, extractPath(eventchain, "action"));
      else if (attribute.name().equals(readpointPathAttName))
        instance.setValue(attribute, extractPath(eventchain, "readPoint"));
      else if (attribute.name().equals(businessLocationPathAttName))
        instance.setValue(attribute, extractPath(eventchain, "bizLocation"));
      else if (attribute.name().equals(modelAttName))
        enrich(instance, attribute, "hrafnxservice:customerModel", eventchain.events.head)
      else if (attribute.name().equals(totalPriceAttName))
        enrich(instance, attribute, "hrafnxservice:totalPrice", eventchain.events.head)
      else if (attribute.name().equals(brandAttName))
        enrich(instance, attribute, "hrafnxservice:customerBrand", eventchain.events.head)
      else if (attribute.name().equals(dispositionAttName))
        enrich(instance, attribute, "disposition", eventchain.events.head)
      else if (attribute.name().equals(lastWaitingTimeAttName))
        instance.setValue(attribute, extractLastWaitingTime(eventchain));
      else if (attribute.name().equals(classAttName))
        instance.setValue(attribute, extractViolations(eventchain.events.head, e2eProcess).reduceLeft(_ + ", " + _));
    }

    def enrich(instance: Instance, attribute: Attribute, propertyName: String, event: ObservationEvent) = {
      val value = event.values.get(attribute.name())
      value match {
        case Some(name) ⇒ {
          instance.setValue(attribute, name);
        }
      }
    }
    println("Created instance: "+instance);
    instance
  }

  def predictViolations(e: ProcessAdvancedEvent) = {

    def getBusinessRuleId(attributeValue: Int) = {
      dataset.classAttribute().value(attributeValue)
    }

    //Extracting an array of class membership probabilities
    val classMembershipProbabilities = classifier.distributionForInstance(createInstance(e.eventchain, Left(e), dataset))

    //Creating a textual representation of the predictions.
    var i = 0;
    val violations = classMembershipProbabilities.foldLeft(List[(String, Double)]()) { (violations, classMembershipProbability) ⇒

      if (classMembershipProbability > .8) {
        (getBusinessRuleId(i), classMembershipProbability) :: violations
      }
      i += 1
      violations
    }
    if (!violations.isEmpty)
      Some(PredictedViolationEvent(violations, e))
    else
      None
  }

  def extractEndToEndDuration(eventchain: EventChain) = {
    eventchain.interval.toDurationMillis()
  }

  def countOccurrences(eventchain: EventChain, propertyName: String, propertyValue: String) = {
    //TODO
  }

  def extractLastWaitingTime(eventchain: EventChain) = {
    if (eventchain.events.list.size > 1) {
      val duration: Duration = new Duration(eventchain.events.list.head.eventTime, eventchain.events.tail.head.eventTime)
      duration.getMillis
    }
    0l
  }

  def extractPath(eventchain: EventChain, propertyName: String) = {
    val separator = " => "
    var initialized = false;

    eventchain.events.list.foldLeft("") { (path, e) ⇒
      if (e.values.containsKey(propertyName)) {
        val value = e.get(propertyName)
        if (initialized)
          path + separator + value
        else {
          initialized = true
          value
        }
      } else {
        path
      }
    }
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
      BusinessRuleContainer.rules.foreach { businessRule ⇒
        if (!violationsFound.contains(businessRule.id) && e.values.containsKey("violation|" + businessRule.id)) {
          businessRule.id :: violationsFound
        }
      }
      violationsFound
    }
  }

}
//class ViolationPredictionBuilder(id: String) extends OperatorBuilder {
//
//  def extractAttributeMetaData(attributeName: String) = {
//    var attNameProcessed = false;
//    attributeName.split("|").foldLeft((List[String]())) { (dataList, s) ⇒
//      if (attNameProcessed) {
//        s :: dataList
//      } else {
//        attNameProcessed = true
//        dataList
//      }
//    }
//  }
//
//  lazy val operator = {
//    val inputRouter: PartialFunction[Any, Either[ProcessEndedEvent, ProcessAdvancedEvent]] = {
//      case OperatorInput(_, e: ProcessEndedEvent) ⇒ Left(e)
//      case OperatorInput(_, t: ProcessAdvancedEvent) ⇒ Right(t)
//    }
//
//    val outputRouter: Option[PredictedViolationEvent] ⇒ List[OperatorOutput[PredictedViolationEvent]] = { o ⇒
//      o.fold(e ⇒ List(OperatorOutput(id + ".updated", e)), List())
//    }
//    val dataset = createDatasetStructure()
//    new Operator(id, inputRouter, outputRouter, new ViolationPredictionState(dataset, createPredictionModel(dataset)))
//  }
//
//  val update = OutputBuilder(this, OutputPortId(id + ".updated"))
//  val in = InputBuilder(this, InputPortId(id + ".in"))
//
//  def update(context: Context) = context + PortBinding(InputPortId(id + ".in"), OperatorId(id)) + operator
//
//  def createDatasetStructure(): Instances = {
//    val atts = new FastVector()
//
//    atts.addElement(new Attribute(actionPathAttName, null: FastVector));
//    atts.addElement(new Attribute(readpointPathAttName, null: FastVector));
//    atts.addElement(new Attribute(businessLocationPathAttName, null: FastVector));
//    atts.addElement(new Attribute(brandAttName, null: FastVector));
//    atts.addElement(new Attribute(modelAttName, null: FastVector));
//    atts.addElement(new Attribute(dispositionAttName, null: FastVector));
//    atts.addElement(new Attribute(totalPriceAttName));
//    atts.addElement(new Attribute(timeElapsedAttName));
//    atts.addElement(new Attribute(lastWaitingTimeAttName));
//
//    val classAttribute = new Attribute(classAttName, null: FastVector)
//    atts.addElement(classAttribute);
//
//    //Initializing dataset
//    val data = new Instances("MyDataset", atts, 0)
//    data.setClass(classAttribute)
//    data
//  }
//
//  def createPredictionModel(data: weka.core.Instances) = {
//
//    val classifier = new AODE()
//
//    classifier.buildClassifier(data)
//
//    classifier
//  }
//
//}
