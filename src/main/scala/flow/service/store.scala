package flow.service
import scalaz._
import effects._
import Scalaz._
object Store {

}

case class Key[T](keyval:String)
case class Keyed[T](key:Key[T],value:T)
trait Datastore[T] {

	def get( key : Key[T] ) : IO[Option[T]]
	def put( t : Keyed[T] ) : IO[Unit]

}

class MemoryDatastore[T] extends Datastore[T]{
	import scala.collection._
	
	val map = mutable.Map[Key[T],T]()
	
	def get(key:Key[T]) =io{ map.get(key) }
	def put(t:Keyed[T]) = io{map.put(t.key,t.value)}
}