package flow.data
import org.joda.time.DateTime
import org.joda.time.Hours
import org.joda.time.Days

object Util {

}

object Time {

	def now = new DateTime()
	def hours (value:Int) = Hours.hours(value).toStandardDuration().getMillis()
	def days(value:Int) = Days.days(value).toStandardDuration().getStandardSeconds()*1000
}