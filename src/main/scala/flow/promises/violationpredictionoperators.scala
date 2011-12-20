package flow.promises
import scala.collection.JavaConversions.mapAsJavaMap

import TrainingsetUtils.createPredictionModel
import flow.event.PredictedViolationEvent
import flow.event.ProcessAdvancedEvent
import flow.event.ProcessEvent
import flow.event.UpdatedTrainingsetEvent
import flow.operator.MealyOperator
import weka.classifiers.`lazy`.IBk
import weka.core.Instances

object ViolationPredictionBuilder {

	def predictor( id : String ) = new ViolationPredictionOperator( id )

}

object Violationprediction {

	def predictViolations( e : ProcessAdvancedEvent, trainingsets : Map[Promise, ( Instances, IBk )] ) = {
		val violations = trainingsets.foldLeft( List[( String, Double )]() ) { ( list, tuple ) ⇒
			//Extracting an array of class membership probabilities
			val classMembershipProbabilities = tuple._2._2.distributionForInstance( TrainingsetUtils.createInstance( e, tuple._2._1, tuple._1 ) )

			//Creating a textual representation of the predictions.
			if ( classMembershipProbabilities.length == 2 && classMembershipProbabilities( 0 ) > 0.5d ) {
				println( "We have an likely violation of promise: "+tuple._1.id )
				( tuple._1.id, classMembershipProbabilities( 0 ) ) :: list
			}
			else {
				list
			}

		}
		if ( !violations.isEmpty )
			List( PredictedViolationEvent( violations, e ) )
		else
			List()
	}

	def extractViolations( e2eProcess : ProcessEvent, promises : List[Promise] ) = {
		val eventchain = e2eProcess.eventchain

		eventchain.events.list.foldRight( List[String]() ) { ( e, violationsFound ) ⇒
			promises.foreach { promise ⇒
				if ( !violationsFound.contains( promise.id ) && e.values.containsKey( "violation|"+promise.id ) ) {
					promise.id :: violationsFound
				}
			}
			violationsFound
		}
	}

	def update : UpdatedTrainingsetEvent ⇒ ViolationPredictionState ⇒ ( List[PredictedViolationEvent], ViolationPredictionState ) = event ⇒ state ⇒ {
		val dataset = event.dataset
		val classifier = createPredictionModel( dataset )
		( List(), new ViolationPredictionState( state.promises, state.trainingsets ++ Map( event.promise -> ( dataset, classifier ) ) ) )
	}

	def advanced : ProcessAdvancedEvent ⇒ ViolationPredictionState ⇒ ( List[PredictedViolationEvent], ViolationPredictionState ) = event ⇒ state ⇒ {
		( predictViolations( event, state.trainingsets ), new ViolationPredictionState( state.promises, state.trainingsets ) )
	}

}

case class ViolationPredictionState( promises : List[Promise], trainingsets : Map[Promise, ( Instances, IBk )] )

class ViolationPredictionOperator( id : String ) extends MealyOperator[List[PredictedViolationEvent], ViolationPredictionState] {
	val process = mealyInput( id.toOpId, "process".toPortId, Violationprediction.advanced )
	val updates = mealyInput( id.toOpId, "updates".toPortId, Violationprediction.update )
	val violationpredicted = output[PredictedViolationEvent]( id.toOpId, "violations".toPortId )
	def * = ( process & updates ) ~> violationpredicted.list
}

 


