package flow.data

import org.joda.time.format._
import org.joda.time._
import com.codecommit.antixml._
import flow.data._
import java.io.File
import scala.io.Source
import flow.Time
import flow.event.XmlEvent

object Parser {

	val dateTimeParser = ( s : String ) ⇒ ISODateTimeFormat.dateTime().parseDateTime( s )

	val extention : Selector[Elem] = Selector( {
		case e @ Elem( Some( "hrafnxservice" ), _, _, _, _ ) ⇒ e
	} )

	def parse( parse : StAXParser ⇒ Elem ) = {
		val parser = new StAXParser()
		val elem = parse( parser )
		val objectEvents = elem \\ "ObjectEvent"
		val aggregationEvents = elem \\ "AggregationEvent"
		objectEvents ++ aggregationEvents
	}

	def parseEvent( parse : StAXParser ⇒ Elem ) = {
		val parser = new StAXParser()
		parse( parser )
	}

	def toEvent( elem : Elem ) = {
		val eventTime = elem.\( "eventTime" ).\( text ).headOption.map( dateTimeParser ).getOrElse( Time.now )
		val eventType = elem.name
		XmlEvent( eventTime, eventType, elem.toGroup )
	}


}