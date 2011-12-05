package flow.promises
import scala.collection.JavaConversions._
import org.joda.time.DateTime
import org.joda.time.Duration
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
import flow.event.DayTimer
import flow.event.EventChain
import flow.event.ObservationEvent
import flow.event.ProcessEndedEvent
import flow.event.ProcessEvent
import flow.event.TimerEvent
import flow.event.UpdatedTrainingsetEvent
import flow.event.UpdatedTrainingsetEvent
import scalaz.Scalaz._
import scalaz._
import weka.core.Attribute
import weka.core.FastVector
import weka.core.Instance
import weka.core.Instances
import weka.filters.unsupervised.attribute.StringToNominal
import weka.filters.Filter
import weka.classifiers.`lazy`.IBk
import weka.classifiers.meta.FilteredClassifier

object DatasetUtils {
  val brandAttName = "brand"
  val modelAttName = "model"
  val totalPriceAttName = "totalPrice"
  val timeElapsedAttName = "totalTime"
  val classAttName = "class"
  val lastWaitingTimeAttName = "lastWaitingTime"
  val dispositionAttName = "disposition"
  val actionPathAttName = "path|action"
  val readpointPathAttName = "path|readpoint"
  val businessLocationPathAttName = "path|businessLocation"

  //  def occurrencesAttName(propertyName: String, propertyValue: String) = {
  //    "occurrences|" + propertyName + "|" + propertyValue
  //  }
  def createPredictionModel(data: weka.core.Instances): IBk = {

    val attributeRange = data.enumerateAttributes().foldLeft("") { (range, attribute) ⇒
      if (range.length() < 1)
        (attribute.asInstanceOf[Attribute].index() + 1).toString()
      else if (attribute.asInstanceOf[Attribute].isString)
        range + "," + (attribute.asInstanceOf[Attribute].index() + 1)
      else
        range
    }
    val filter = new StringToNominal
    filter.setAttributeRange(attributeRange)

    filter.setInputFormat(data) // inform filter about dataset **AFTER**
    //setting options
    val newData = Filter.useFilter(data, filter) // apply filter
    // meta-classifier
    val fc = new FilteredClassifier()
    val classifier = new IBk()
    classifier.setKNN(7)
    fc.setFilter(filter)
    fc.setClassifier(classifier)
    fc.buildClassifier(data)

    //    classifier.buildClassifier(data)
    classifier
  }

  def createDatasetStructure: Instances = {
    val atts = new FastVector

    atts.addElement(new Attribute(actionPathAttName, null: FastVector));
    atts.addElement(new Attribute(readpointPathAttName, null: FastVector));
    atts.addElement(new Attribute(businessLocationPathAttName, null: FastVector));
    atts.addElement(new Attribute(brandAttName, null: FastVector));
    atts.addElement(new Attribute(modelAttName, null: FastVector));
    atts.addElement(new Attribute(dispositionAttName, null: FastVector));
    atts.addElement(new Attribute(totalPriceAttName));
    atts.addElement(new Attribute(timeElapsedAttName));
    atts.addElement(new Attribute(lastWaitingTimeAttName));

    var classValues = new FastVector
    classValues.addElement("true")
    classValues.addElement("false")
    val classAttribute = new Attribute(classAttName, classValues)
    atts.addElement(classAttribute);

    //Initializing dataset
    val data = new Instances("MyDataset", atts, 0)
    data.setClass(classAttribute)

    data
  }

  //Create a dataset instance representation of the given event chain
  def createInstance(process: ProcessEvent, dataset: Instances, promise: Promise): Instance = {
    val instance = new Instance(dataset.numAttributes())
    instance.setDataset(dataset)

    for (attr ← dataset.enumerateAttributes) {
      val attribute = attr.asInstanceOf[Attribute]
      println(attribute.name());
      if (attribute.name().equals(actionPathAttName))
        instance.setValue(attribute, extractPath(process.eventchain, "action"))
      else if (attribute.name().equals(readpointPathAttName))
        instance.setValue(attribute, extractPath(process.eventchain, "readPoint"))
      else if (attribute.name().equals(businessLocationPathAttName))
        instance.setValue(attribute, extractPath(process.eventchain, "bizLocation"))
      else if (attribute.name().equals(modelAttName))
        enrich(instance, attribute, "hrafnxservice:customerModel", process.eventchain.events.head)
      else if (attribute.name().equals(totalPriceAttName))
        enrich(instance, attribute, "hrafnxservice:totalPrice", process.eventchain.events.head)
      else if (attribute.name().equals(brandAttName))
        enrich(instance, attribute, "hrafnxservice:customerBrand", process.eventchain.events.head)
      else if (attribute.name().equals(dispositionAttName))
        enrich(instance, attribute, "disposition", process.eventchain.events.head)
      else if (attribute.name().equals(lastWaitingTimeAttName))
        instance.setValue(attribute, extractLastWaitingTime(process.eventchain));
    }
    instance.setValue(dataset.classAttribute(), promise.isViolated(process).toString)
    instance
  }
  //  def stringToNominalDatasetConversion(dataset: Instances): Instances = {
  //
  //    val attributeRange = dataset.enumerateAttributes().foldLeft("") { (range, attribute) ⇒
  //      if (range.length() < 1)
  //        (attribute.asInstanceOf[Attribute].index()+1).toString()
  //      else if (attribute.asInstanceOf[Attribute].isString)
  //        range + "," + (attribute.asInstanceOf[Attribute].index()+1)
  //      else
  //        range
  //    }
  //
  //    println("RANGE: "+attributeRange)
  //    stringToNominalAttributeConversion(dataset, attributeRange)
  //  }
  //
  //  def stringToNominalAttributeConversion(dataset: Instances, range: String): Instances = {
  //    val filter = new StringToNominal
  //    filter.setAttributeRange(range)
  //    filter.setInputFormat(dataset)
  //    Filter.useFilter(dataset, filter)
  //  }

  def enrich(instance: Instance, attribute: Attribute, propertyName: String, event: ObservationEvent) = {
    val value = event.values.get(attribute.name())
    value match {
      case Some(name) ⇒ {
        instance.setValue(attribute, name.toString())
      }
      case None ⇒ {
        instance.setMissing(attribute)
      }
    }
    println("Created instance: " + instance);
    instance
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
}
object TrainingsetsBuilder {

  def trainingsetsBuilder(id: String) = new TrainingsetsBuilder(id)

}

class TrainingsetsState(trainingsets: Map[Promise, Instances]) extends OperatorState[Either[ProcessEndedEvent, TimerEvent], List[UpdatedTrainingsetEvent]] {

  def apply(e: Either[ProcessEndedEvent, TimerEvent]): (List[UpdatedTrainingsetEvent], TrainingsetsState) = {

    def append(event: ProcessEndedEvent) = {
      for (promise ← trainingsets.keySet) {
        if (promise.appliesToPred(event)) {
          val dataset = trainingsets.get(promise);
          dataset match {
            case Some(instances) ⇒ {
              instances.add(DatasetUtils.createInstance(event, instances, promise))
            }
          }
        }
      }
      (List[UpdatedTrainingsetEvent](), new TrainingsetsState(trainingsets))
    }

    def distributeTrainingsets(time: DateTime) = {
      val output = trainingsets.map { case (k, v) ⇒ new UpdatedTrainingsetEvent(k, v) } toList: List[UpdatedTrainingsetEvent]
      (output, new TrainingsetsState(trainingsets))
    }

    e match {
      case Left(event) ⇒ append(event)
      case Right(time) ⇒ distributeTrainingsets(time.time)
    }
  }

  def stringToNominalDatasetConversion(dataset: Instances): Instances = {

    val attributeRange = dataset.enumerateAttributes().foldLeft("") { (range, attribute) ⇒
      if (range.length() < 1)
        attribute.asInstanceOf[Attribute].index().toString()
      else if (attribute.asInstanceOf[Attribute].isString)
        range + "," + attribute.asInstanceOf[Attribute].index()
      else
        range
    }

    stringToNominalAttributeConversion(dataset, attributeRange)
  }

  def stringToNominalAttributeConversion(dataset: Instances, range: String): Instances = {
    val filter = new StringToNominal
    filter.setAttributeRange(range)
    filter.setInputFormat(dataset)
    Filter.useFilter(dataset, filter)
  }

}

class TrainingsetsBuilder(id: String) extends OperatorBuilder {

  lazy val operator = {
    val inputRouter = handle[Either[ProcessEndedEvent, TimerEvent]] {
      case OperatorInput(_, e: ProcessEndedEvent) ⇒ Left(e)
      case OperatorInput(_, t: DayTimer) ⇒ Right(t)
    }

    val outputRouter: List[UpdatedTrainingsetEvent] ⇒ List[OperatorOutput] = { o ⇒
      o.foldLeft(List[OperatorOutput]()) { (out, e) ⇒
        out.add(OperatorOutput(id + ".updated", e))
        out
      }
    }

    val map = Map(PromiseRepository.promises map { promise ⇒ (promise, DatasetUtils.createDatasetStructure) }: _*)
    new Operator(id, inputRouter, outputRouter, new TrainingsetsState(map))
  }

  val update = OutputBuilder(this, OutputPortId(id + ".updated"))
  val in = InputBuilder(this, InputPortId(id + ".in"))

  def update(context: Context) = context + PortBinding(InputPortId(id + ".in"), OperatorId(id)) + operator

}
