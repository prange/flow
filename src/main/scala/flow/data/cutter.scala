package flow.data

import flow.data._
import scala.annotation.tailrec
import scalaz._
import Scalaz._

class Cutter( cutpoints : List[CutPoint] ) {

	def split( chain:EventChain ) : List[EventChain] = {

		@tailrec def doSplit( result : List[List[Event]], currentReversed : List[Event], rest : List[Event] ) : List[EventChain] = ( currentReversed, rest ) match {

			case ( current, Nil ) ⇒ (( current.reverse ) :: result).map( l => EventChain("",l))

			case ( Nil, next ) ⇒ doSplit( result, List( next.head ), next.tail )

			case ( prev, next ) ⇒ if ( cut_?( prev, next ) ) doSplit( ( prev.reverse ) :: result, Nil, next ) else doSplit( result, next.head :: prev, next.tail )

		}
		doSplit( Nil, Nil, chain.events )
	}

	def cut_?( prev : List[Event], next : List[Event] ) : Boolean = {

		cutpoints.exists( point ⇒ point( prev, next ) )

	}

}

trait CutPoint {

	def apply( before : List[Event], after : List[Event] ) : Boolean

}

object Cut {

	def cutAfter( pred : Event ⇒ Boolean ) = new CutPoint {
		def apply( before : List[Event], after : List[Event] ) = pred( before.head )
	}

	def cutBefore( pred : Event ⇒ Boolean ) = new CutPoint {
		def apply( before : List[Event], after : List[Event] ) = pred( after.head )
	}

	def pred( kv : Tuple2[String, String] ) : Event ⇒ Boolean = { e : Event ⇒ e.values.get( kv._1 ).map( _ contains kv._2 ) getOrElse false }
}


