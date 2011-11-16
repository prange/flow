package flow.statistics

import org.joda.time.DateTime
import org.joda.time.Days
import org.joda.time.Duration
import flow.actor.OperatorOutput
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
import flow.event.ProcessEndedEvent
import flow.event.TimerEvent
import flow.event.UpdatedHistogramEvent
import scalaz.Scalaz._
import flow.event.MinuteTimer

object HistogramBuilder {

  def histogram(id: String, windowLength: Duration) = new HistogramBuilder(id, windowLength)

}

case class BucketCount(value: Int)
case class Bucket(num: Int, label: String, count: BucketCount) {
  def increment = Bucket(num, label, BucketCount(count.value + 1))
  
  override def toString = {
    label+": "+count.value
  }
}

class HistogramState(windowLength: Duration, history: List[ProcessEndedEvent]) extends OperatorState[Either[ProcessEndedEvent, TimerEvent], Option[UpdatedHistogramEvent]] {

  def apply(e: Either[ProcessEndedEvent, TimerEvent]): (Option[UpdatedHistogramEvent], HistogramState) = {

    def appendEvent(event: ProcessEndedEvent) = {
      (None, new HistogramState(windowLength, event :: history))
    }

    def updateHistogram(time: DateTime) = {

      val updateHistory = history.filter(e ⇒ e.timestamp.isAfter(time.minus(windowLength)))
      val days = updateHistory.map(getDurationInDays)
      val max = (Days.days(10)::days).maxBy(_.getDays())

      val count = days.foldLeft(Map[Int,Int]()){(map,days)=>
        map + (days.getDays() -> (map.getOrElse(days.getDays(),0)+1))
      }
      
      val buckets = (0 to max.getDays()).map(day=>Bucket(day,day.toString+" days",BucketCount(count.getOrElse(day,0)))).toList
      (Some(UpdatedHistogramEvent(buckets)),new HistogramState(windowLength,updateHistory))
    }

    e match {
      case Left(event) ⇒ appendEvent(event)
      case Right(time)=> updateHistogram(time.time)
    }

  }

  val getDurationInDays: ProcessEndedEvent ⇒ Days = e ⇒ e.eventchain.interval.toDuration().toStandardSeconds().toStandardDays()

}

class HistogramBuilder(id: String, windowLength: Duration) extends OperatorBuilder {
  lazy val operator = {
    val inputRouter: PartialFunction[Any, Either[ProcessEndedEvent, TimerEvent]] = {
      case OperatorInput(_, e: ProcessEndedEvent) ⇒ Left(e)
      case OperatorInput(_, t: MinuteTimer) ⇒ Right(t)
    }

    val outputRouter: Option[UpdatedHistogramEvent] ⇒ List[OperatorOutput[UpdatedHistogramEvent]] = { o ⇒
      o.fold(e ⇒ List(OperatorOutput(id + ".updated", e)), List())
    }

    new Operator(id, inputRouter, outputRouter, new HistogramState(windowLength, List()))
  }

  val update = OutputBuilder(this, OutputPortId(id + ".updated"))
  val in = InputBuilder(this, InputPortId(id + ".in"))

  def update(context: Context) = context + PortBinding(InputPortId(id + ".in"), OperatorId(id)) + operator
}
