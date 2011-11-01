package flow.data
import org.joda.time.DateTime
import org.joda.time.Hours

object Util {

}

object Time {

	def now = new DateTime()
	def hours (value:Int) = Hours.hours(value).toStandardDuration().getMillis()
}