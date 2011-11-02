package flow.analyzer
import scala.collection.JavaConversions._
import flow.data._
import weka.classifiers.bayes.AODE
import weka.core.Attribute
import weka.core.FastVector
import weka.core.Instance
import weka.core.Instances
import weka.filters.unsupervised.attribute.Discretize
import weka.filters.Filter
import weka.classifiers.trees.J48
import weka.classifiers.Classifier

object ProcessPredicate {
  def valuePred(key: String, value: String): Process ⇒ Boolean = { p ⇒
    p.values.get(key).map(_.contains(value)) getOrElse false
  }
  def valueLessThanPred(key: String, value: Long): Process ⇒ Boolean = { p ⇒
    p.values.get(key).map(_.toLong > value) getOrElse false
  }

  def acceptedValuePred(acceptedKeys: Set[String]): String ⇒ Boolean = { key ⇒

    if (acceptedKeys.contains(key))
      true
    else
      false

  }
}

class FrequentPatternAnalyzer {

  def acceptedKeys: Set[String] = {
    Set("customerBrand", "chain", "activity", "product", "bizLocation", "disposition", "action")
  }

  def generateFrequentPatterns(processes: Iterable[Process], pred: Process ⇒ Boolean): String = {

    //    val pEnriched = processes.map(p ⇒ if (pred(p)) p.withProperty("class", "true") else p.withProperty("class", "false"))

    var data = createInstances(processes, pred)

    val filter = new Discretize()
    filter.setInputFormat(data)

    // apply filter
    data = Filter.useFilter(data, filter)
    data.setClass(data.attribute("class"))
//    println(data.toString())

    // setting class attribute

    val classifier = getDecisionTreeClassifier()

    classifier.buildClassifier(data)

    classifier.toString()
  }

  private def getProbabilisticClassifier(): Classifier = {
    new AODE()
  }
  private def getDecisionTreeClassifier(): Classifier = {
    val classifier = new J48()
    classifier.setUnpruned(true)
    classifier
  }

  private def createInstances(pEnriched: Iterable[flow.data.Process], pred: Process ⇒ Boolean): Instances = {
    // 1. set up attributes
    // Create vector to hold nominal values "first", "second", "third" 
    val booleanValues = new FastVector(2)
    booleanValues.addElement("true")
    booleanValues.addElement("false")

    val atts = new FastVector()
    atts.addElement(new Attribute("class", booleanValues));

    for (row ← pEnriched) {
      for (value ← row.values) {
        val keyHead = value._1.split('.').head
        if (acceptedKeys.contains(keyHead)) {
          val attributeName = keyHead + ": " + value._2;
          val attribute = new Attribute(attributeName, booleanValues);
          if (!atts.contains(attribute)) {
            atts.addElement(attribute);
//            println("Adding attribute: " + attributeName);
          }
        }
      }
    }

    //Initializing dataset
    val data = new Instances("MyDataset", atts, 0)

    for (row ← pEnriched) {
      val instance = new Instance(atts.size())

      instance.setValue(data.attribute("class"), pred(row).toString())

      for (attribute ← atts.elements()) {
        instance.setValue(attribute.asInstanceOf[Attribute], "false");
      }

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

}