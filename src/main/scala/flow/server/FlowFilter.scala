package flow.server
import flow.data._
import scalaz._
import Scalaz._
import effects._
import unfiltered.response._
import unfiltered.request._
import com.codecommit.antixml.StAXParser

class FlowFilter extends unfiltered.filter.Plan {
	import QParams._

	val data = new Data()

	def intent = {
		case p @ POST( Path( "/capture" ) ) ⇒ {
			capture( p.reader ).unsafePerformIO.fold( f ⇒ Ok ~> ResponseString( f ), s ⇒ Ok ~> ResponseString( s ) )
		}

		case GET( Path( Seg( "report" :: "time" :: key :: Nil ) ) ) ⇒ {
			Ok
		}
	}

	def capture( body : java.io.Reader ) : IO[Validation[String, String]] = {
		val events = Parser.parseReader( body )
		data.handle( EventObservation( events ) )
	}

	def time( key : String, fromVal : String, toVal : String ) = io {
		val build = for {
			val1 ← data.handle( BuildChain( e ⇒ true, e ⇒ e.values( "epc" ) ) ).unsafePerformIO;
			val2 ← data.handle( BuildProcess( c ⇒ true, List(), p ⇒ p ) ).unsafePerformIO
		} yield ("ok")
		val io3 = data.queryProcess( PredicateProcessQuery( e ⇒ true ) ).unsafePerformIO
		io3.success[String]
	}
}