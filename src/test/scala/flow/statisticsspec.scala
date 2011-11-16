package flow.data

import org.specs2.mutable.Specification
import flow._
import data._
import actor._
import OperatorBuilder._
import scalaz._
import Scalaz._
import scala.io.Source
import org.joda.time.Hours
import org.joda.time.Duration
import flow.statistics.HistogramState
import org.joda.time.DurationFieldType
import flow.event.ProcessEndedEvent
import flow.event.ProcessEndedEvent
import operator.WeekdayEnricher
import flow.statistics.HistogramBuilder._
import flow.operator.AssembleBuilder._
import flow.event.ProcessEvent
import flow.event.XmlEvent
import flow.event.EventChain
import flow.event.XmlEvent
import org.joda.time.DateTime
import com.codecommit.antixml.Elem
import com.codecommit.antixml.Group
import flow.event.ProcessEndedEvent
import flow.event.TimerEvent
import event.ProcessEndedEvent
import event.TimerEvent
import event.TimerEvent
import event.TimerEvent
import event.SecondTimer

class StatisticsSpec extends Specification {

  var histogramState = new HistogramState(new Duration(Time.hours(24)), List[ProcessEndedEvent]())

  "Adding events to the histogram state" should {
    "Result" in {

      val trace1 = EventChain("t1", NonEmptyList[XmlEvent](XmlEvent(new DateTime().minusHours(4), "A", Group[Elem]()), XmlEvent(new DateTime(), "A", Group[Elem]())), Group[Elem]())
      val pee1 = ProcessEndedEvent(new DateTime(), trace1)
      val trace2 = EventChain("t2", NonEmptyList[XmlEvent](XmlEvent(new DateTime().minusHours(4), "B", Group[Elem]()), XmlEvent(new DateTime(), "B", Group[Elem]())), Group[Elem]())
      val pee2 = ProcessEndedEvent(new DateTime(), trace2)
      val trace3 = EventChain("t3", NonEmptyList[XmlEvent](XmlEvent(new DateTime().minusHours(32), "C", Group[Elem]()), XmlEvent(new DateTime(), "C", Group[Elem]())), Group[Elem]())
      val pee3 = ProcessEndedEvent(new DateTime(), trace3)
      val trace4 = EventChain("t4", NonEmptyList[XmlEvent](XmlEvent(new DateTime().minusHours(32), "D", Group[Elem]()), XmlEvent(new DateTime(), "D", Group[Elem]())), Group[Elem]())
      val pee4 = ProcessEndedEvent(new DateTime().minusDays(32), trace4)

      val response1 = histogramState(Left[ProcessEndedEvent, TimerEvent](pee1))
      histogramState = response1._2
      val response2 = histogramState(Left[ProcessEndedEvent, TimerEvent](pee2))
      histogramState = response2._2
      val response3 = histogramState(Left[ProcessEndedEvent, TimerEvent](pee3))
      histogramState = response3._2
      val response4 = histogramState(Left[ProcessEndedEvent, TimerEvent](pee4))
      histogramState = response4._2


      val timerEvent = SecondTimer(new DateTime)
      val timerResponse = histogramState(Right[ProcessEndedEvent, TimerEvent](timerEvent))
      histogramState = timerResponse._2
      println("TIMER RESPONSE: " + timerResponse)

      timerResponse._1.toList.head.histogram.size must be >= 11
    }
  }

}