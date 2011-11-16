package flow
import org.joda.time.DateTime
import org.joda.time.Hours
import org.joda.time.Days

object Util {

}

object Time {
	def time( millis : Long ) = new DateTime( millis )
	def now = new DateTime()
	def hours( value : Int ) = Hours.hours( value ).toStandardDuration().getMillis()
	def days( value : Int ) = Days.days( value ).toStandardDuration().getStandardSeconds() * 1000
	def nextSecondAfter(time:DateTime) = time.plusSeconds(1).withMillisOfSecond(0)
	def nextHourAfter(time:DateTime) = time.plusHours(1).withMinuteOfHour(0).withSecondOfMinute(0).withMillisOfSecond(0)
	def nextMidnightAfter(time:DateTime) = time.plusDays(1).toDateMidnight()
}