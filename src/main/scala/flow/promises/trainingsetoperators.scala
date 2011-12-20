package flow.promises
import scala.collection.JavaConversions._
import org.joda.time.DateTime
import org.joda.time.Duration
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
import scalaz.effects._
import flow.operator.EffectOperator

object TrainingSets{
	
	import TrainingsetUtils._
	
	def handleEvent : ProcessEndedEvent ⇒ TrainingsetsState ⇒ IO[List[UpdatedTrainingsetEvent]] = event ⇒ state ⇒ io {
		for ( promise ← state.trainingsets.keySet ) {
			if ( promise.appliesToPred( event ) ) {
				val dataset = state.trainingsets.get( promise );
				dataset match {
					case Some( instances ) ⇒ {
						instances.add( createInstance( event, instances, promise ) )
					}
				}
			}
		}
		List[UpdatedTrainingsetEvent]()
	}

	def handleTimer : DayTimer ⇒ TrainingsetsState ⇒ IO[List[UpdatedTrainingsetEvent]] = timer ⇒ state ⇒ io {
		val output = state.trainingsets.map { case ( k, v ) ⇒ new UpdatedTrainingsetEvent( k, v ) } toList : List[UpdatedTrainingsetEvent]
		output
	}

	val map : List[Promise] ⇒ Map[Promise, Instances] = promises ⇒ Map( promises map { promise ⇒ ( promise, createDatasetStructure ) } : _* )
}

trait TrainingsetsBuilder {
	def trainingsets( id : String ) = new TrainingSetOperator( id )
}

case class TrainingsetsState( trainingsets : Map[Promise, Instances] )

class TrainingSetOperator( id : String ) extends EffectOperator[List[UpdatedTrainingsetEvent], TrainingsetsState] {
	val events = effectInput[ProcessEndedEvent, List[UpdatedTrainingsetEvent], TrainingsetsState]( id.toOpId, "events".toPortId, TrainingSets.handleEvent )
	val timer = effectInput[DayTimer, List[UpdatedTrainingsetEvent], TrainingsetsState]( id.toOpId, "timer".toPortId, TrainingSets.handleTimer )
	val updates = output[List[UpdatedTrainingsetEvent]]( id.toOpId, "updates".toPortId )
	val * = (events & timer) ~> updates.list
}

