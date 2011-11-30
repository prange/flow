package flow
import org.joda.time.DateTime
import org.joda.time.Days
import org.joda.time.Hours
import scalaz.Scalaz._
import scalaz._
import effects._
import org.joda.time.Duration

object Util {

}

object Time {

	implicit def DateTimeOrder : Order[DateTime] = orderBy( _.getMillis() )
	implicit def DateTimeOrdering : scala.Ordering[DateTime] = new scala.Ordering[DateTime]{
		def compare(one:DateTime,other:DateTime) = {one.getMillis.compare(other.getMillis)}
	}
	
	def time( millis : Long ) = new DateTime( millis )
	def now = io{new DateTime()}
	def hours( value : Int ) = Hours.hours( value ).toStandardDuration().getMillis()
	def days( value : Int ) = Days.days( value ).toStandardDuration().getStandardSeconds() * 1000
	def nextSecondAfter( time : DateTime ) = time.plusSeconds( 1 ).withMillisOfSecond( 0 )
	def nextMinuteAfter( time : DateTime ) = time.plusMinutes( 1 ).withSecondOfMinute( 0 ).withMillisOfSecond( 0 )
	def nextHourAfter( time : DateTime ) = time.plusHours( 1 ).withMinuteOfHour( 0 ).withSecondOfMinute( 0 ).withMillisOfSecond( 0 )
	def nextMidnightAfter( time : DateTime ) = time.plusDays( 1 ).toDateMidnight()
}

trait TimerTask {
	def cancel()
}

class JavaTimer( isDaemon : Boolean ) {

	private[this] val underlying = new java.util.Timer( isDaemon )

	def schedule( when : DateTime )( f : ⇒ Unit ) = {
		val task = toJavaTimerTask( f )
		underlying.schedule( task, when.toDate )
		toTimerTask( task )
	}

	def schedule( when : DateTime, period : Duration )( f : ⇒ Unit ) = {
		val task = toJavaTimerTask( f )
		underlying.schedule( task, when.toDate, period.getMillis() )
		toTimerTask( task )
	}

	def stop() = underlying.cancel()

	private[this] def toJavaTimerTask( f : ⇒ Unit ) = new java.util.TimerTask {
		def run { f }
	}

	private[this] def toTimerTask( task : java.util.TimerTask ) = new TimerTask {
		def cancel() { task.cancel() }
	}
}


object Threads{
	
	
	def sleep(time:Long) = io{
		Thread.sleep(time)
	}
	
}