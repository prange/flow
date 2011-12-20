package flow.promises
import weka.classifiers.`lazy`.IBk
import flow.event.ObservationEvent
import flow.event.EventChain
import weka.classifiers.meta.FilteredClassifier
import weka.core.Instances
import weka.filters.unsupervised.attribute.StringToNominal
import weka.core.FastVector
import flow.event.ProcessEvent
import org.joda.time.Duration
import weka.core.Attribute
import weka.filters.Filter
import weka.core.Instance
import scala.collection.JavaConversions._

object TrainingsetUtils {
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

	def createPredictionModel( data : weka.core.Instances ) : IBk = {

		val attributeRange = data.enumerateAttributes().foldLeft( "" ) { ( range, attribute ) ⇒
			if ( range.length() < 1 )
				( attribute.asInstanceOf[Attribute].index() + 1 ).toString()
			else if ( attribute.asInstanceOf[Attribute].isString )
				range+","+( attribute.asInstanceOf[Attribute].index() + 1 )
			else
				range
		}
		val filter = new StringToNominal
		filter.setAttributeRange( attributeRange )

		filter.setInputFormat( data ) // inform filter about dataset **AFTER**
		//setting options
		val newData = Filter.useFilter( data, filter ) // apply filter
		// meta-classifier
		val fc = new FilteredClassifier()
		val classifier = new IBk()
		classifier.setKNN( 7 )
		fc.setFilter( filter )
		fc.setClassifier( classifier )
		fc.buildClassifier( data )

		//    classifier.buildClassifier(data)
		classifier
	}

	def createDatasetStructure : Instances = {
		val atts = new FastVector

		atts.addElement( new Attribute( actionPathAttName, null : FastVector ) );
		atts.addElement( new Attribute( readpointPathAttName, null : FastVector ) );
		atts.addElement( new Attribute( businessLocationPathAttName, null : FastVector ) );
		atts.addElement( new Attribute( brandAttName, null : FastVector ) );
		atts.addElement( new Attribute( modelAttName, null : FastVector ) );
		atts.addElement( new Attribute( dispositionAttName, null : FastVector ) );
		atts.addElement( new Attribute( totalPriceAttName ) );
		atts.addElement( new Attribute( timeElapsedAttName ) );
		atts.addElement( new Attribute( lastWaitingTimeAttName ) );

		var classValues = new FastVector
		classValues.addElement( "true" )
		classValues.addElement( "false" )
		val classAttribute = new Attribute( classAttName, classValues )
		atts.addElement( classAttribute );

		//Initializing dataset
		val data = new Instances( "MyDataset", atts, 0 )
		data.setClass( classAttribute )

		data
	}

	//Create a dataset instance representation of the given event chain
	def createInstance( process : ProcessEvent, dataset : Instances, promise : Promise ) : Instance = {
		val instance = new Instance( dataset.numAttributes() )
		instance.setDataset( dataset )

		for ( attr ← dataset.enumerateAttributes ) {
			val attribute = attr.asInstanceOf[Attribute]
			println( attribute.name() );
			if ( attribute.name().equals( actionPathAttName ) )
				instance.setValue( attribute, extractPath( process.eventchain, "action" ) )
			else if ( attribute.name().equals( readpointPathAttName ) )
				instance.setValue( attribute, extractPath( process.eventchain, "readPoint" ) )
			else if ( attribute.name().equals( businessLocationPathAttName ) )
				instance.setValue( attribute, extractPath( process.eventchain, "bizLocation" ) )
			else if ( attribute.name().equals( modelAttName ) )
				enrich( instance, attribute, "hrafnxservice:customerModel", process.eventchain.events.head )
			else if ( attribute.name().equals( totalPriceAttName ) )
				enrich( instance, attribute, "hrafnxservice:totalPrice", process.eventchain.events.head )
			else if ( attribute.name().equals( brandAttName ) )
				enrich( instance, attribute, "hrafnxservice:customerBrand", process.eventchain.events.head )
			else if ( attribute.name().equals( dispositionAttName ) )
				enrich( instance, attribute, "disposition", process.eventchain.events.head )
			else if ( attribute.name().equals( lastWaitingTimeAttName ) )
				instance.setValue( attribute, extractLastWaitingTime( process.eventchain ) );
		}
		instance.setValue( dataset.classAttribute(), promise.isViolated( process ).toString )
		instance
	}

	def enrich( instance : Instance, attribute : Attribute, propertyName : String, event : ObservationEvent ) = {
		val value = event.values.get( attribute.name() )
		value match {
			case Some( name ) ⇒ {
				instance.setValue( attribute, name.toString() )
			}
			case None ⇒ {
				instance.setMissing( attribute )
			}
		}
		println( "Created instance: "+instance );
		instance
	}

	def extractEndToEndDuration( eventchain : EventChain ) = {
		eventchain.interval.toDurationMillis()
	}

	def countOccurrences( eventchain : EventChain, propertyName : String, propertyValue : String ) = {
		//TODO
	}

	def extractLastWaitingTime( eventchain : EventChain ) = {
		if ( eventchain.events.list.size > 1 ) {
			val duration : Duration = new Duration( eventchain.events.list.head.eventTime, eventchain.events.tail.head.eventTime )
			duration.getMillis
		}
		0l
	}

	def extractPath( eventchain : EventChain, propertyName : String ) = {
		val separator = " => "
		var initialized = false;

		eventchain.events.list.foldLeft( "" ) { ( path, e ) ⇒
			if ( e.values.containsKey( propertyName ) ) {
				val value = e.get( propertyName )
				if ( initialized )
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
	
	def stringToNominalDatasetConversion( dataset : Instances ) : Instances = {

		val attributeRange = dataset.enumerateAttributes().foldLeft( "" ) { ( range, attribute ) ⇒
			if ( range.length() < 1 )
				attribute.asInstanceOf[Attribute].index().toString()
			else if ( attribute.asInstanceOf[Attribute].isString )
				range+","+attribute.asInstanceOf[Attribute].index()
			else
				range
		}

		stringToNominalAttributeConversion( dataset, attributeRange )
	}

	def stringToNominalAttributeConversion( dataset : Instances, range : String ) : Instances = {
		val filter = new StringToNominal
		filter.setAttributeRange( range )
		filter.setInputFormat( dataset )
		Filter.useFilter( dataset, filter )
	}

	
}