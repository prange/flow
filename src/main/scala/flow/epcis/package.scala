package flow
import flow.event.XmlEvent
import com.codecommit.antixml.Selector._

package object epcis {

	def idExtractor : XmlEvent ⇒ String = event ⇒ event.select(  _ \ "epcList" \ "epc" ).getOrElse( event.select(_ \ "parentID") getOrElse ("<unknown>") )
	def predicateFor( fieldType : String ) : ( String ⇒ Boolean ) ⇒ XmlEvent ⇒ Boolean = f ⇒ event ⇒ event.select( _ \  fieldType ).map( f ) getOrElse false
	def whereField( fieldType : String ) = new {
		def contains( value : String ) = predicateFor( fieldType )( _.contains(value) )
	}

}