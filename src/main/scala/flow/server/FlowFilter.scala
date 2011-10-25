package flow.server
import flow.data._
import unfiltered.response._
import unfiltered.request._
import com.codecommit.antixml.StAXParser

class FlowFilter extends unfiltered.filter.Plan {
	import QParams._

	val parser = new StAXParser()
	val data = new Data()
	
	def intent = {
		case p@POST( Path( "/capture" ) ) ⇒{
			val body = parser.fromReader(p.reader)
			Ok ~> ResponseString( "ok" )
		}
		
		case GET(Path("/report/time")) => {
			
		}
	}
}