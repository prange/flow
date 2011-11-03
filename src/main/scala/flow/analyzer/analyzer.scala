package flow.analyzer
import scala.collection.JavaConversions._
import flow.data._
import weka.attributeSelection.InfoGainAttributeEval
import weka.attributeSelection.Ranker
import weka.classifiers.bayes.AODE
import weka.classifiers.trees.J48
import weka.classifiers.Classifier
import weka.core.Attribute
import weka.core.FastVector
import weka.core.Instance
import weka.core.Instances
import weka.filters.Filter
import weka.filters.unsupervised.attribute.Discretize

object ProcessPredicate {
  def valuePred(key: String, value: String): Process ⇒ Boolean = { p ⇒
    p.values.get(key).map(_.contains(value)) getOrElse false
  }

  def valueLessThan(key: String, value: Long): Process ⇒ Boolean = { p ⇒
    p.values.get(key).map(_.toLong < value) getOrElse false
  }

  def notPred(pred: Process ⇒ Boolean): Process ⇒ Boolean = p ⇒ !pred(p)

  def acceptedValuePred(acceptedKeys: Set[String]): String ⇒ Boolean = { key ⇒

    if (acceptedKeys.contains(key))
      true
    else
      false

  }
}

class FrequentPatternAnalyzer {

  def acceptedKeys: Set[String] = {
    Set("customerBrand", "chain", "activity", "product", "customerModel", "priority")
  }

  def generateFrequentPatterns(processes: Iterable[Process], pred: Process ⇒ Boolean): Classifier = {

    //    val pEnriched = processes.map(p ⇒ if (pred(p)) p.withProperty("class", "true") else p.withProperty("class", "false"))

    var data = createDescriptionModelInstances(processes, pred)
    createDescriptiveModel(data)
  }

  private def getProbabilisticClassifier(): Classifier = {
    new AODE()
  }
  private def getDecisionTreeClassifier(): Classifier = {
    val classifier = new J48()
    classifier.setUnpruned(true);
    classifier
  }

  private def createDescriptionModelInstances(pEnriched: Iterable[flow.data.Process], pred: Process ⇒ Boolean): Instances = {
    // 1. set up attributes
    // Create vector to hold nominal values "first", "second", "third" 
    val booleanValues = new FastVector(2)
    booleanValues.addElement("true")
    booleanValues.addElement("false")

    val atts = new FastVector()
    val classAttribute = new Attribute("class", booleanValues)
    atts.addElement(classAttribute);

    for (row ← pEnriched) {

      for (value ← row.values) {
        val keyHead = value._1.split('.').head
        if (acceptedKeys.contains(keyHead)) {
          val attributeName = keyHead + ": " + value._2;
          val attribute = new Attribute(attributeName, booleanValues);
          if (!atts.contains(attribute)) {
            atts.addElement(attribute);
          }
        }
      }
    }

    //Initializing dataset
    val data = new Instances("MyDataset", atts, 0)
    data.setClass(classAttribute)

    var i = 0
    var count = 0

    for (row ← pEnriched) {
      val instance = new Instance(atts.size())

      count = count + 1
      if (pred(row)) {
        i = i + 1
      }

      //Setting all values to false
      for (attribute ← atts.elements()) {
        instance.setValue(attribute.asInstanceOf[Attribute], "false");
      }

      instance.setValue(classAttribute, pred(row).toString())

      for (value ← row.values) {
        val keyHead = value._1.split('.').head
        if (acceptedKeys.contains(keyHead)) {
          val attributeName = keyHead + ": " + value._2;

          val attribute = data.attribute(attributeName);

          instance.setValue(attribute, "true");
        }
      }
      data.add(instance)
    }
    data
  }

  def createPredictionModelInstances(pEnriched: Iterable[flow.data.Process], timeAttributeName: String): Instances = {
    // 1. set up attributes
    // Create vector to hold nominal values "first", "second", "third" 
    val booleanValues = new FastVector(2)
    booleanValues.addElement("true")
    booleanValues.addElement("false")

    val atts = new FastVector()
    val classAttribute = new Attribute("class")
    atts.addElement(classAttribute);

    for (row ← pEnriched) {

      for (value ← row.values) {
        val keyHead = value._1.split('.').head
        if (acceptedKeys.contains(keyHead)) {
          val attributeName = keyHead + ": " + value._2;
          val attribute = new Attribute(attributeName, booleanValues);
          if (!atts.contains(attribute)) {
            atts.addElement(attribute);
          }
        }
      }
    }

    //Initializing dataset
    val data = new Instances("MyDataset", atts, 0)
    data.setClass(classAttribute)

    for (row ← pEnriched) {
      val instance = createInstance(data, row)
      data.add(instance)
    }
    data
  }

  private def createDescriptiveModel(data: weka.core.Instances): Classifier = {

    val classifier = getDecisionTreeClassifier()

    classifier.buildClassifier(data)

    val attsel = new weka.attributeSelection.AttributeSelection()
    val infoGain = new InfoGainAttributeEval();
    val ranker = new Ranker();
    attsel.setEvaluator(infoGain);
    attsel.setSearch(ranker);
    attsel.setRanking(true);
    attsel.SelectAttributes(data)
    // obtain the attribute indices that were selected
    val indices = attsel.selectedAttributes()

    classifier
  }
  def createPredictionModel(data: weka.core.Instances): Classifier = {

    val classifier = new weka.classifiers.functions.SMOreg()

    classifier.buildClassifier(data)

    classifier
  }
  
  private def createInstance(data: weka.core.Instances, process: flow.data.Process): weka.core.Instance = {
    val instance = new Instance(data.numAttributes())
    //Setting all values to false
    for (a ← data.enumerateAttributes()) {
      val attribute = a.asInstanceOf[Attribute]
      if (attribute.isNominal())
        instance.setValue(attribute, "false");
    }

    instance.setValue(data.classAttribute(), java.lang.Double.valueOf(process.values.get("time").getOrElse("-1"))/(1000*3600));

    for (value ← process.values) {
      val keyHead = value._1.split('.').head
      if (acceptedKeys.contains(keyHead)) {
        val attributeName = keyHead + ": " + value._2;

        val attribute = data.attribute(attributeName);

        instance.setValue(attribute, "true");
      }
    }
    instance
  }
  private def createPredictionInstance(data: weka.core.Instances, process: flow.data.Process): weka.core.Instance = {
    val instance = new Instance(data.numAttributes())
    //Setting all values to false
    for (a ← data.enumerateAttributes()) {
      val attribute = a.asInstanceOf[Attribute]
      if (attribute.isNominal())
        instance.setValue(attribute, "false");
    }

    instance.setValue(data.classAttribute(), java.lang.Double.valueOf(process.values.get("time").getOrElse("-1"))/(1000*3600));

    //TODO Fixme! Handle comma separated features in commissioning events.
    for (value ← process.values) {
      val keyHead = value._1.split('.').head
      if (acceptedKeys.contains(keyHead)) {
        val attributeName = keyHead + ": " + value._2;

        val attribute = data.attribute(attributeName);

        instance.setValue(attribute, "true");
      }
    }
    instance
  }

}