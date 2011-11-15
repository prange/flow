package flow.operator
import flow._
import scalaz._
import Scalaz._
import scalaz.Validation
import com.codecommit.antixml._
import flow.event.XmlEvent

object Filter {

}



case class PropertyExistsTest( property : Selector[Elem] ) extends Predicate[XmlEvent] {
	def apply( event : XmlEvent ) = {
		val extract = event select ( _ \ property )
		val e2 = extract some { _ ⇒ true } none { false }
		e2
	}
}

class OperatorTest[T]( property : Selector[Elem], toT : String ⇒ T, operator : ( T, T ) ⇒ Boolean, value : T ) extends Predicate[XmlEvent]{

	def apply( event : XmlEvent ) = {
		val extract = event select ( _ \ property)
		extract map ( toT( _ ) ) map ( operator( _, value ) ) getOrElse false

	}

}

case class PropertyEqualsTest( property : Selector[Elem], value : String ) extends OperatorTest[String]( property, identity, ( _ === _ ), value )

