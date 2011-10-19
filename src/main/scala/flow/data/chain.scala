package flow.data
import flow.data._
import scala.collection._
import scalaz.effects._
import scala.collection.mutable.ArrayBuffer


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

class Processes {
	val processes = ArrayBuffer[EventChain]()

	def record( chains : Iterable[EventChain] ) = io[Unit] {
		for ( chain ← chains ) yield ( processes += chain )
	}

	def query(pred:EventChain=>Boolean) = io { processes.view.filter(pred).toIterable }

	def clear:IO[Unit] = io { processes.clear }

}

case class EventChain( id : String, list : List[Event] ) {

	def append( e : Event ) = EventChain( id, e :: list )

	def events = list.reverse
	
	override def toString() = events.mkString("EventChain("+id+")[\n",",\n","]")

}