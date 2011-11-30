package flow
import org.specs2.mutable.Specification

class EventSpec  extends Specification {


	"EventSimulator" should {
		"Create times for events" in {
			
			val time = Time.now.unsafePerformIO
			val list = EventSimulator.init(time,5000).times.toList.reverse
			
			success
		}
	}
		
}