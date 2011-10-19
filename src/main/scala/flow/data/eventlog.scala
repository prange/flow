package flow.data
import scalaz.Scalaz._
import scalaz._
import effects._
import flow.data._
import scala.collection.mutable.ArrayBuffer

object Eventlog {

	def apply() = new NonrunningEventLog( ArrayBuffer[Event]() )
}

trait EventLog {

}

class NonrunningEventLog( list : ArrayBuffer[Event] ) {

	def start( url : String ) = {
		//		try {
		//			Class.forName( "org.h2.Driver" );
		//			val conn = DriverManager.getConnection( url );
		//			new RunningEventLog( conn ).success[String]
		//		} catch {
		//			case e : Exception ⇒ e.getMessage.fail[RunningEventLog]
		//		}
		new RunningEventLog( list ).success[String]
	}

}

class RunningEventLog(val list : ArrayBuffer[Event] ) extends EventLog {

	def stop = {
		//		try {
		//			conn.close()
		//			( new NonrunningEventLog(), none[String] )
		//		} catch {
		//			case e : Exception ⇒ ( new NonrunningEventLog(), Some( e.getMessage() ) )
		//		}
		( new NonrunningEventLog( list ), none[String] )
	}

	def record( event : Event) :IO[Unit] = io {
		list += event
	}
	
	
	def query(pred:Event=>Boolean):IO[Iterable[Event]] = io{
		list.filter(pred)
	}

}

