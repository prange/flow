package flow.data

import org.joda.time.format._
import org.joda.time._
import com.codecommit.antixml._

import flow.data._

import java.io.File
import scala.io.Source

object Parser {

	val dateTimeParser = ( s : String ) ⇒ ISODateTimeFormat.dateTime().parseDateTime( s )

	val extention : Selector[Elem] = Selector( {
		case e @ Elem( Some( "hrafnxservice" ), _, _, _, _ ) ⇒ e
	} )


	def parse( parse:StAXParser => Elem ) = {
		val parser = new StAXParser()
		val elem = parse(parser)
		val objectEvents = elem \\ "ObjectEvent"
		val aggregationEvents = elem \\ "AggregationEvent"
		objectEvents.flatMap( createEventList ) ++ aggregationEvents.map( createAggregationEvent )
	}

	def createEventList( elem : Elem ) = {
		val tags = elem \\ "epc"
		tags.map( createEvent( elem ) )
	}

	def createEvent( parent : Elem )( epcTag : Elem ) : Event = {
		val epcO = epcTag \ text headOption
		val eventTimeO = parent.\( "eventTime" ).\( text ).headOption.map( dateTimeParser )
		val eventTimeZoneOffsetO = parent \ "eventTimeZoneOffset" \ text headOption //This is not used in Hrafn data...
		val actionO = parent \ "action" \ text headOption
		val bizStepO = parent \ "bizStep" \ text headOption
		val dispositionO = parent \ "disposition" \ text headOption
		val readPointO = parent \ "readPoint" \ "id" \ text headOption
		val bizLocationO = parent \ "bizLocation" \ "id" \ text headOption
		val extentionEls = parent \ extention
		val extentionsPairs = extentionEls map ( e ⇒ ( e.name, e.\( text ).headOption.getOrElse( "" ) ) )
		val event = for {
			epc ← epcO;
			eventTime ← eventTimeO
			eventTimeZoneOffset ← eventTimeZoneOffsetO;
			action ← actionO;
			bizStep ← bizStepO;
			disposition ← dispositionO;
			readPoint ← readPointO;
			bizLocation ← bizLocationO
		} yield ( Event( eventTime ).withProperty( "epc", epc ).withProperty( "action", action ).withProperty( "bizStep", bizStep ).withProperty( "disposition", disposition ).withProperty( "bizLocation", bizLocation ) )

		val updateEvent = ( event : Event ) ⇒ extentionsPairs.foldLeft( event )( ( event : Event, t : Tuple2[String, String] ) ⇒ event.withProperty( t._1, t._2 ) )

		val updatedEvent = event.map( updateEvent ) getOrElse ( Event( Time.now ) )

		updatedEvent
	}

	def createAggregationEvent( parent : Elem ) : Event = {
		val eventTimeO = parent.\( "eventTime" ).\( text ).headOption.map( dateTimeParser )
		val eventTimeZoneOffsetO = parent \ "eventTimeZoneOffset" \ text headOption //This is not used in Hrafn data...
		val actionO = parent \ "action" \ text headOption
		val bizStepO = parent \ "bizStep" \ text headOption
		val dispositionO = parent \ "disposition" \ text headOption
		val readPointO = parent \ "readPoint" \ "id" \ text headOption
		val bizLocationO = parent \ "bizLocation" \ "id" \ text headOption
		val extentionEls = parent \ extention
		val extentionsPairs = extentionEls map ( e ⇒ ( e.name, e.\( text ).headOption.getOrElse( "" ) ) )
		val aggregationParentO = parent \ "parentID" \ text headOption
		val aggregationChildrenO = parent \ "childEPCs" \ "epc" \ text headOption

		val event = for {
			eventTime ← eventTimeO
			eventTimeZoneOffset ← eventTimeZoneOffsetO;
			action ← actionO;
			bizStep ← bizStepO;
			disposition ← dispositionO;
			readPoint ← readPointO;
			bizLocation ← bizLocationO;
			epc ← aggregationParentO;
			product ← aggregationChildrenO
		} yield ( Event( eventTime ).
			withProperty( "epc", epc ).
			withProperty( "action", action ).
			withProperty( "bizStep", bizStep ).
			withProperty( "disposition", disposition ).
			withProperty( "bizLocation", bizLocation ).
			withProperty( "product", product ) )

		val updateEvent = ( event : Event ) ⇒ extentionsPairs.foldLeft( event )( ( event : Event, t : Tuple2[String, String] ) ⇒ event.withProperty( t._1, t._2 ) )

		val updatedEvent = event.map( updateEvent ) getOrElse ( Event( Time.now ) )

		updatedEvent
	}
}