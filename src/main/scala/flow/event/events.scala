package flow.event
import flow._
import org.joda.time.DateTime
import com.codecommit.antixml._

case class XmlEvent( eventTime : DateTime, eventType : String, data : Group[Elem] ) {
	def select( property : Selector[Elem] ) = data \ property \ text headOption
}

case class EventChain( events : List[XmlEvent], data : Group[Elem] ) {
	def ::( event : XmlEvent ) = EventChain( event :: events, data )
	def select( property : Selector[Elem] ) = data \ property \ text headOption
}




object EventChain {

	def from( event : XmlEvent ) = EventChain( event :: Nil, Group() )
}



