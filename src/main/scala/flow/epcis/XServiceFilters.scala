package flow.epcis

import flow.event.XmlEvent
import com.codecommit.antixml.Selector._
import flow.event.AggregationEvent
import flow.event.ObjectEvent
import com.codecommit.antixml._
import org.joda.time.format.ISODateTimeFormat
import flow.event.EPC
import flow.event.AggregationEvent
import flow.Time
import flow.event.ObservationEvent
import flow.event.ObservationEvent

case class EpcisTransform() extends ( XmlEvent ⇒ Either[AggregationEvent, ObjectEvent] ) {

	def apply( xmle : XmlEvent ) : Either[AggregationEvent, ObjectEvent] = {

		def idExtractor : XmlEvent ⇒ String = event ⇒ event.select( _ \ "epcList" \ "epc" ).getOrElse( event.select( _ \ "parentID" ) getOrElse ( "<unknown>" ) )

		def predicateFor( fieldType : String ) : ( String ⇒ Boolean ) ⇒ XmlEvent ⇒ Boolean = f ⇒ event ⇒ event.select( _ \ fieldType ).map( f ) getOrElse false

		def whereField( fieldType : String ) = new {
			def contains( value : String ) = predicateFor( fieldType )( _.contains( value ) )
		}

		val dateTimeParser = ( s : String ) ⇒ ISODateTimeFormat.dateTime().parseDateTime( s )

		val extention : Selector[Elem] = Selector( {
			case e @ Elem( Some( "hrafnxservice" ), _, _, _, _ ) ⇒ e
		} )

		def createObjectEvent( parent : Elem ) : ObjectEvent = {
			val epcList = parent.\( "epcList" ).\("epc").map( e ⇒ e \ text head ).map( epc ⇒ EPC( epc ) ).toList
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
				eventTime ← eventTimeO
				eventTimeZoneOffset ← eventTimeZoneOffsetO;
				action ← actionO;
				bizStep ← bizStepO;
				disposition ← dispositionO;
				readPoint ← readPointO;
				bizLocation ← bizLocationO
			} yield ( ObjectEvent( eventTime, epcList, Map( "action" -> action, "bizStep" -> bizStep, "disposition" -> disposition, "bizLocation" -> bizLocation ) ) )

			val updateEvent = ( event : ObjectEvent ) ⇒ extentionsPairs.foldLeft( event )( ( event, t ) ⇒ ObjectEvent( event.eventTime, event.epcList, event.values + ( t ) ) )

			val updatedEvent = event.map( updateEvent ) getOrElse ( ObjectEvent( Time.time( 1 ), List(), Map() ) )

			updatedEvent
		}

		def createAggregationEvent( parent : Elem ) : AggregationEvent = {
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
			val epcList = parent.\( "childEPCs" ).\( "epc" ).map( e ⇒ e \ text head ).map( epc ⇒ EPC( epc ) ).toList

			val event = for {
				eventTime ← eventTimeO
				eventTimeZoneOffset ← eventTimeZoneOffsetO;
				action ← actionO;
				bizStep ← bizStepO;
				disposition ← dispositionO;
				readPoint ← readPointO;
				bizLocation ← bizLocationO;
				epc ← aggregationParentO
			} yield ( AggregationEvent( eventTime, EPC( epc ), epcList, Map( "action" -> action, "bizStep" -> bizStep, "disposition" -> disposition, "bizLocation" -> bizLocation ) ) )

			val updateEvent = ( event : AggregationEvent ) ⇒ extentionsPairs.foldLeft( event )( ( event, t ) ⇒ AggregationEvent( event.eventTime, event.parent, event.childEPCs, event.values + ( t ) ) )

			val updatedEvent = event.map( updateEvent ) getOrElse ( AggregationEvent( Time.time( 1 ), EPC( "" ), List(), Map() ) )

			updatedEvent
		}

		if ( xmle.data.name == "AggregationEvent" ) Left( createAggregationEvent( xmle.data ) ) else Right( createObjectEvent( xmle.data ) )
	}

}

case class ObservationTransform() extends ( Either[AggregationEvent, ObjectEvent] ⇒ List[ObservationEvent] ) {

	def apply( epcisEvent : Either[AggregationEvent, ObjectEvent] ) = {

		val aggToObs : AggregationEvent ⇒ List[ObservationEvent] = { ae ⇒
			val parent = ObservationEvent( ae.eventTime, ae.values + ( "children" -> ae.childEPCs.mkString( "", ",", "" ) ) )
			List( parent )
		}

		val objToObs : ObjectEvent ⇒ List[ObservationEvent] = { oe ⇒
			oe.epcList.map( epc ⇒ ObservationEvent( oe.eventTime, oe.values + ( "id" -> epc.value ) ) )
		}

		val r = epcisEvent.fold( aggToObs, objToObs )
		r
	}

}

case class ProductEnricher() extends ( List[ObservationEvent] ⇒ List[ObservationEvent] ) {
	def apply( list : List[ObservationEvent] ) : List[ObservationEvent] = {
		list.map( in ⇒ in + ( "product" -> in.get( "children" ) ) )
	}
}