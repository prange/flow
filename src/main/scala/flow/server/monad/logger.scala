package unfiltered.monad
import scalaz._
import Scalaz._

sealed trait LogLevel {
  val message:String
}
case class Trace(message:String) extends LogLevel
case class Debug(message:String) extends LogLevel
case class Info(message:String) extends LogLevel
case class Warn(message:String, exception:Option[Throwable] = None) extends LogLevel
case class Error(message:String, exception:Option[Throwable] = None) extends LogLevel

trait RequestError
case class Missing(message:String) extends RequestError
case class Invalid(message:String,exception:Option[Throwable] = None) extends RequestError
case class Uncaught(exception:Throwable) extends RequestError

object RequestError {
  implicit def toFullValidation[R](in:Validation[RequestError,R]):Validation[RequestLogger.ERRORS[RequestError],R] = 
    in.fold(failure = f => f.pure[RequestLogger.ERRORS].fail,
            success = s => s.success)

  implicit def RequestErrorShow: Show[RequestError] = new Show[RequestError] {
    def show(a: RequestError) = a.toString.toList
  }

  implicit def RequestErrorEquals: Equal[RequestError] = new Equal[RequestError] {
    def equal(a1: RequestError, a2: RequestError) =
      a1 === a2
  }

  implicit def RequestErrorOrder: Order[RequestError] = new Order[RequestError] {
    def order(a1: RequestError, a2: RequestError) =
      a1 ?|? a2
  }

  class RequestErrorW(message:String) {
    def missing[R]:Validation[RequestLogger.ERRORS[RequestError],R] = Missing(message).fail[R]
    def invalid[R](implicit exception:Option[Throwable]):Validation[RequestLogger.ERRORS[RequestError],R] = Invalid(message,exception).fail[R]
  }

  class RequestErrorEW(exception:Throwable) {
    def uncaught[R]:Validation[RequestLogger.ERRORS[RequestError],R] = Uncaught(exception).fail[R]
  }

  implicit def stringToRE(message:String):RequestErrorW = new RequestErrorW(message)
  implicit def throwableToRE(exception:Throwable):RequestErrorEW = new RequestErrorEW(exception)
  implicit def throwableToOption(exception:Throwable):Option[Throwable] = Some(exception)
}

object LogLevel {
  implicit val defaultException:Option[Throwable] = None

  class LogLevelW(message:String) {
    def trace:LogLevel = Trace(message)
    def debug:LogLevel = Debug(message)
    def info:LogLevel = Info(message)
    def warn(implicit exception:Option[Throwable]):LogLevel = Warn(message,exception)
    def err(implicit exception:Option[Throwable]):LogLevel = Error(message,exception)
  }

  implicit def string2LogLevel(message:String):LogLevelW = new LogLevelW(message)

  implicit def LogLevelShow: Show[LogLevel] = new Show[LogLevel] {
    def show(a: LogLevel) = a.toString.toList
  }

}

sealed case class RequestLogger[R](log: RequestLogger.LOG[LogLevel], over: Validation[RequestLogger.ERRORS[RequestError],R]) extends NewType[Writer[IndSeq[LogLevel], Validation[NonEmptyList[RequestError],R]]] {
  import LogLevel._
  import RequestLogger._
  import FingerTree._
  import RequestError._

  val value = writer(log, over)

  def map[B](f:R => B): RequestLogger[B] = RequestLogger[B](RequestLogger.this.log,RequestLogger.this.over map f)

  def flatMap[B](f:R => RequestLogger[B]): RequestLogger[B] = {
    over.fold(failure = f1 => RequestLogger[B](RequestLogger.this.log,f1.fail),
              success = s => {
                val l = f(s)
                RequestLogger[B](RequestLogger.this.log |+| l.log,l.over)
              })
  }

  def foreach(f:R => Unit) =
    over.foreach(f)

  /**
   * Transform the log by the given function.
   */
  def withLog(k: LOG[LogLevel] => LOG[LogLevel]): RequestLogger[R] = RequestLogger[R](k(RequestLogger.this.log),RequestLogger.this.over)

  /**
   * Transform each log value by the given function.
   */
  def withEachLog(k: LogLevel => LogLevel): RequestLogger[R] =
    withLog(_ ∘ k)

  /**
   * Set the log to the given value, losing any previous value.
   */
  def setLog(l: LOG[LogLevel]): RequestLogger[R] =
    withLog(_ => l)

  /**
   * Append the given value to the current log.
   */
  def :+->(e: LogLevel): RequestLogger[R] =
    withLog(_ |+| e.η[LOG])

  /**
   * Append the given value to the current log by applying to the underlying value.
   */
  def :->>(e:Validation[ERRORS[RequestError],R] => LogLevel): RequestLogger[R] =
    :+->(e(over))

  /**
   * Prepend the given value to the current log.
   */
  def <-+:(e: LogLevel): RequestLogger[R] =
    withLog(e.η[LOG] |+| _)

  /**
   * Prepend the given value to the current log by applying to the underlying value.
   */
  def <<-:(e:Validation[ERRORS[RequestError],R] => LogLevel): RequestLogger[R] =
    <-+:(e(over))

  /**
   * Append the given value to the current log.
   */
  def :++->(e: LOG[LogLevel]): RequestLogger[R] =
    withLog(_ |+| e)

  /**
   * Append the given value to the current log by applying to the underlying value.
   */
  def :+->>(e:Validation[ERRORS[RequestError],R] => LOG[LogLevel]): RequestLogger[R] =
    withLog(_ |+| e(over))

  /**
   * Prepend the given value to the current log.
   */
  def <-++:(e: LOG[LogLevel]): RequestLogger[R] =
    withLog(e |+| _)

  /**
   * Prepend the given value to the current log by applying to the underlying value.
   */
  def <<-+:(e:Validation[ERRORS[RequestError],R] => LOG[LogLevel]): RequestLogger[R] =
    <-++:(e(over))

  /**
   * Set the log to be empty.
   */
  def resetLog: RequestLogger[R] =
    withLog(_ => ∅[LOG[LogLevel]])

  /**
   * Runs the given side-effect on the log, then returns this underlying value. '''CAUTION: side-effect'''
   */
  def effectLog(k: LOG[LogLevel] => Unit): RequestLogger[R] = {
    k(log)
    this
  }

  /**
   * Runs the given side-effect on each element of the log, then returns this underlying value. '''CAUTION: side-effect'''
   */
  def effectEachLog(k: LogLevel => Unit): RequestLogger[R] =
    effectLog(_ foreach k)

  /**
   * Runs the given side-effect on the log, then returns this underlying value with an empty log. '''CAUTION: side-effect'''
   */
  def flushLog(k: LOG[LogLevel] => Unit): RequestLogger[R] = {
    effectLog(k)
    resetLog
  }

  /**
   * Runs the given side-effect on each element of the log, then returns this underlying value with an empty log. '''CAUTION: side-effect'''
   */
  def flushEachLog(k: LogLevel => Unit): RequestLogger[R] = {
    effectLog(_ foreach k)
    resetLog
  }

  /**
   * Prints the log, then returns this underlying value. '''CAUTION: side-effect'''
   */
  def printLog: RequestLogger[R] =
    effectLog(_.println)

  /**
   * Prints each element of the log, then returns this underlying value. '''CAUTION: side-effect'''
   */
  def printEachLog: RequestLogger[R] =
    effectEachLog(_.println)

  /**
   * Prints the log, then returns this underlying value with an empty log. '''CAUTION: side-effect'''
   */
  def printFlushLog: RequestLogger[R] =
    flushLog(_.println)

  /**
   * Prints each element of the log, then returns this underlying value with an empty log. '''CAUTION: side-effect'''
   */
  def printFlushEachLog: RequestLogger[R] =
    flushEachLog(_.println)

  def ifMissing(r: => Validation[ERRORS[RequestError],R]):RequestLogger[R] = 
    over.fold(failure = f => f.head match {
      case Missing(_) => RequestLogger[R](RequestLogger.this.log,r)
      case _ => this
    },
              success = _ => this)


  def orElse(r: => Validation[ERRORS[RequestError],R]):RequestLogger[R] = 
    over.fold(failure = _ => RequestLogger[R](RequestLogger.this.log,r),
              success = _ => this)

  def orElseAndLog(r: => Validation[ERRORS[RequestError],R])(implicit toLog: RequestError => LogLevel):RequestLogger[R] = {
    over.fold(failure = f => RequestLogger[R](f.foldl(RequestLogger.this.log)((s,v) => s |+| toLog(v).pure[LOG]),r),
              success = _ => this)
    
  }
}

object RequestLogger {
  type LOG[C] = IndSeq[C]
  type ERRORS[C] = NonEmptyList[C]
  import LogLevel._
  import FingerTree._
  import RequestError._
  import Apply._
  import Traverse._

  implicit def RequestLoggerInjective = Injective[RequestLogger]

  implicit def RequestLoggerPure: Pure[RequestLogger] = new Pure[RequestLogger] {
    def pure[R](a: => R) = RequestLogger[R](∅[LOG[LogLevel]],a.success)
  }

  implicit def RequestLoggerFunctor: Functor[RequestLogger] = new Functor[RequestLogger] {
    def fmap[R, B](x: RequestLogger[R], f: R => B) =
      x map f
  }

  implicit def RequestLoggerApply: Apply[RequestLogger] = new Apply[RequestLogger] {
    def apply[R, B](f: RequestLogger[R=>B], a: RequestLogger[R]): RequestLogger[B] = {
      val w1 = f.value
      val w2 = a.value
      // Hrm.  Had to do it this way, some implicit conversion failure somehwhere.
      RequestLogger[B](w1.written |+| w2.written,
                       ValidationApply[ERRORS[RequestError]].apply(w1.over,w2.over))
    }
  }

  implicit def RequestLoggerBind: Bind[RequestLogger] = new Bind[RequestLogger] {
    def bind[R, B](a: RequestLogger[R], f:R => RequestLogger[B]) =
      a flatMap f
  }

  implicit def RequestLoggerEach: Each[RequestLogger] = new Each[({type λ[α]= RequestLogger[α]})#λ] {
    def each[R](x: RequestLogger[R], f:R => Unit) =
      x foreach f
  }

  /*  implicit def RequestLoggerIndex[E]: Index[({type λ[α]=RequestLogger[E,α]})#λ] = new Index[({type λ[α]=RequestLogger[E,α]})#λ] {
   def index[R](a: RequestLogger[E,R], n: Int) =
   if(n == 0) Some(a.over) else None
   }*/

  implicit def RequestLoggerFoldable: Foldable[RequestLogger] = new Foldable[RequestLogger] {
    override def foldRight[R, B](t: RequestLogger[R], b: => B, f: (R, => B) => B) =
      t.over.fold(failure = _ => b,
                  success = s => f(s,b))
  }

  implicit def RequestLoggerTraverse: Traverse[RequestLogger] = new Traverse[RequestLogger] {
    def traverse[F[_] : Applicative, R, B](f: R => F[B], t: RequestLogger[R]) =
      ValidationTraverse.traverse(f,t.over) ∘ (b => RequestLogger[B](t.log,b))
  }

  implicit def RequestLoggerShow[R : Show]: Show[RequestLogger[R]] = new Show[RequestLogger[R]] {
    def show(a: RequestLogger[R]) =
      ("RequestLogger(" + a.log.shows + "," + a.over.shows + ")").toList
  }

  implicit def RequestLoggerEqual[ R: Equal]: Equal[RequestLogger[R]] = new Equal[RequestLogger[R]] {
    def equal(a1: RequestLogger[R], a2: RequestLogger[R]) =
      a1.over === a2.over
  }

  implicit def RequestLoggerOrder[ R: Order]: Order[RequestLogger[R]] = new Order[RequestLogger[R]] {
    def order(a1: RequestLogger[R], a2: RequestLogger[R]) =
      a1.over ?|? a2.over
  }

  implicit def RequestLoggerZero[R:Zero]: Zero[RequestLogger[R]] = new Zero[RequestLogger[R]] {
    val zero = RequestLogger[R](∅[LOG[LogLevel]],∅[R].success)
  }
}

trait RequestLoggers {
  import RequestLogger._

  def mkRequestLogger[R](a:Validation[ERRORS[RequestError],R],log:LOG[LogLevel] = ∅[LOG[LogLevel]]) = 
    RequestLogger[R](log,a)
}
