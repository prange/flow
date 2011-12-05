package flow
import org.joda.time.DateTime
import org.specs2.mutable.Specification
import event.ObservationEvent
import event.ProcessEndedEvent
import flow.event.EventChain
import flow.event.ProcessAdvancedEvent
import flow.event.ProcessEndedEvent
import flow.promises.Promise
import flow.promises.PromiseRepository
import flow.promises.PromiseViolationsState
import flow.promises.TrainingsetsState
import promises.ViolationPredictionState
import scalaz._
import weka.core.Instances
import promises.DatasetUtils
import event.DayTimer
import weka.classifiers.`lazy`.IBk
import event.PredictedViolationEvent

class BusinessRulesSpec extends Specification {

  "PromiseOperator" should {
    "extract violations " in {

      val violationsState = new PromiseViolationsState(PromiseRepository.promises)
      val violationEvents = violationsState.apply(violationEvent)

      println("Number of violations in test: " + violationEvents._1._1.size)
      violationEvents._1._1.size must be > 0
    }
  }

  "TrainingsetOperator" should {
    "update dataset " in {
      val map = Map(PromiseRepository.promises map { promise ⇒ (promise, DatasetUtils.createDatasetStructure) }: _*)
      var trainingsetsState = new TrainingsetsState(map)
      trainingsetsState = trainingsetsState.apply(Left(processEndedEvent))._2
      trainingsetsState = trainingsetsState.apply(Left(processEndedEvent))._2
      val output = trainingsetsState.apply(Right(DayTimer(new DateTime)))
      
      val dataset = output._1(0).dataset
      println(dataset)

      var predictionState = new ViolationPredictionState(Map[Promise, (Instances, IBk)](output._1(0).promise-> (dataset,DatasetUtils.createPredictionModel(dataset))))
      predictionState = predictionState.apply(Left(output._1(0)))._2
      val output2 = predictionState.apply(Right(violationEvent))
      
      val predictionEvent = output2._1.getOrElse(PredictedViolationEvent(List[( String, Double )](), violationEvent ));
      println("OUTPUT2: "+output2)
      predictionEvent.violations.size must be > 0
    }
  }

  val violationEvent = {
    val event1 = ObservationEvent(new DateTime().minusDays(14), Map[String, String]("action" -> "A"))
    val event2 = ObservationEvent(new DateTime(), Map[String, String]("action" -> "B"))
    val eventchain = new EventChain("violating event chain", NonEmptyList[ObservationEvent](event1, event2), Map[String, String]())
    new ProcessAdvancedEvent(new DateTime, eventchain)
  }

  val processEndedEvent = {
    val event1 = ObservationEvent(new DateTime().minusDays(14), Map[String, String]("action" -> "A"))
    val event2 = ObservationEvent(new DateTime(), Map[String, String]("action" -> "C"))
      
    val eventchain = new EventChain("violating event chain", NonEmptyList[ObservationEvent](event1, event2), Map[String, String]("voilation|B1" -> "true"))
    new ProcessEndedEvent(new DateTime, eventchain)
  }

}