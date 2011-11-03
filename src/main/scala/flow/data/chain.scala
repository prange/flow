package flow.data
import scala.collection._
import flow.data._
import scalaz.effects._
import scala.collection.immutable.TreeSet


class EventChains(  ) {

	val chains = mutable.Map[String, EventChain]()

	def record( events : Iterable[Event], f : Event ⇒ String ) = io[Unit] {
		for ( event ← events ) yield {
			val id = f( event )
			val chain = chains.getOrElseUpdate( id, EventChain( id, Nil ) )
			val updatedChain = chain.append( event )
			chains.update( id, updatedChain )
		}
	}

	def clear:IO[Unit] = io { chains.clear }

	def query( pred : EventChain ⇒ Boolean ) = io { chains.values.filter( pred ) }
}

case class EventChain( id : String, list : List[Event] ){

	def append( e : Event ) = EventChain( id,  e::list )

	def events = list.sorted
	
	override def toString() = events.mkString("EventChain("+id+")[\n",",\n","]")

}