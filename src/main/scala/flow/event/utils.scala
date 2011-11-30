package flow.event

import scalaz._
import Scalaz._

object Predicates {

	val where = new WhereType()

	def asSplit[T](pred:T=>Boolean):T=>Either[T,T] = t=>if(pred(t)) Left(t) else Right(t)
}

class WhereType {
	def field( name : String ) = new FieldOperatorsType( name )
}

class FieldOperatorsType( name : String ) {
	def test( test : String ⇒ Boolean ) : ValuedEvent ⇒ Boolean = e ⇒ e.values.get( name ).map( test( _ ) ) getOrElse false
	def is( value : String ) : ValuedEvent ⇒ Boolean = test( _ === value )
	def contains( value : String ) : ValuedEvent ⇒ Boolean = test( _.contains( value ) )
}
