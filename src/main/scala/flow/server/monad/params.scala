package unfiltered.monad
import scalaz._
import Scalaz._
import unfiltered.request._
import scala.util.control.Exception._

trait Conversion[T] {
  def to(in:String):T
  def toSeq(in:Seq[String]):Seq[T]
}

object DefaultConversions {
  implicit val stringConversions = new Conversion[String] {
    def to(in:String):String = in
    def toSeq(in:Seq[String]):Seq[String] = in
  }

  implicit val intConversions = new Conversion[Int] {
    def to(in:String):Int = in.toInt
    def toSeq(in:Seq[String]):Seq[Int] = in.map(_.toInt)
  }

  implicit val doubleConversions = new Conversion[Double] {
    def to(in:String):Double = in.toDouble
    def toSeq(in:Seq[String]):Seq[Double] = in.map(_.toDouble)
  }

  implicit val boolConversions = new Conversion[Boolean] {
    def to(in:String):Boolean = in.toBoolean
    def toSeq(in:Seq[String]):Seq[Boolean] = in.map(_.toBoolean)
  }
}

class ParamOps(val params:Map[String,Seq[String]]) {
  self => 
  import RequestLogger._, RequestError._
  import LogLevel._
  import DefaultConversions._
  import ParamOps._

  def required[T](key:String,
                  noValueErrorMessage:String => String = defaultNoValueMessage _,
                  conversionErrorMessage:(String,String,String)=>String = defaultConversionMessage _)(implicit c:Conversion[T], m:Manifest[T]):RequestLogger[T] = mkRequestLogger {
    (params.get(key).map {
      x => allCatch.opt(c.to(x.head).success).getOrElse(conversionErrorMessage(key,x.head,m.erasure.getName).invalid)
    }) getOrElse (noValueErrorMessage(key).missing)
  }

  def optional[T](key:String,
                  conversionErrorMessage:(String,String,String)=>String = defaultConversionMessage _)(implicit c:Conversion[T], m:Manifest[T]):RequestLogger[Option[T]] = 
    mkRequestLogger { (allCatch.either(params
                                       .get(key)
                                       .map(x => c.to(x.head)))
                       .fold(fa = e => conversionErrorMessage(key,params(key).head,m.erasure.getName).invalid(e),
                             fb = s => s.success)) }

  def requiredSeq[T](key:String,
                     noValueErrorMessage:String => String = defaultNoValueMessage _,
                     conversionErrorMessage:(String,String,String)=>String = defaultConversionMessage _)(implicit c:Conversion[T], m:Manifest[T]):RequestLogger[Seq[T]] = mkRequestLogger {
    (params.get(key).map {
      x => allCatch.opt(c.toSeq(x).success).getOrElse(conversionErrorMessage(key,x.mkString("[",",","]"),m.erasure.getName).invalid)
    }) getOrElse (noValueErrorMessage(key).missing)
  }

  def optionalSeq[T](key:String,
                     conversionErrorMessage:(String,String,String)=>String = defaultConversionMessage _)(implicit c:Conversion[T], m:Manifest[T]):RequestLogger[Option[Seq[T]]] = 
    mkRequestLogger { (allCatch.either(params
                                       .get(key)
                                       .map(x => c.toSeq(x)))
                       .fold(fa = e => conversionErrorMessage(key,params(key).mkString("[",",","]"),m.erasure.getName).invalid(e),
                             fb = s => s.success)) }

}

object ParamOps {
  import RequestError._
  import LogLevel._
  import RequestLogger._

  def defaultConversionMessage(key:String,errorValue:String,targetType:String):String =
    "Unable to convert values of parameter '%s'->'%s' to %s".format(key,errorValue,targetType)
  def defaultNoValueMessage(key:String):String = "No value for '%s'".format(key)

  implicit def rlToParamOpsW[T](rl:RequestLogger[T]):ParamOpsW[T] = new ParamOpsW[T](rl)
  implicit def rlToParamOpsOptW[T](rl:RequestLogger[Option[T]]):ParamOpsOptW[T] = new ParamOpsOptW[T](rl)
  implicit def rlToSeqParamOpsOptW[T](rl:RequestLogger[Option[Seq[T]]]):SeqParamOpsOptW[T] = new SeqParamOpsOptW[T](rl)
  implicit def rlToSeqParamOpsW[T](rl:RequestLogger[Seq[T]]):SeqParamOpsW[T] = new SeqParamOpsW[T](rl)

  implicit def rlToStringParamOpsW(rl:RequestLogger[String]):StringParamOpsW = new StringParamOpsW(rl)
  implicit def rlToSeqStringParamOpsW(rl:RequestLogger[Seq[String]]):SeqStringParamOpsW = new SeqStringParamOpsW(rl)
  implicit def rlToStringParamOpsOptW(rl:RequestLogger[Option[String]]):StringParamOpsOptW = new StringParamOpsOptW(rl)
  implicit def rlToSeqStringParamOpsOptW(rl:RequestLogger[Option[Seq[String]]]):SeqStringParamOpsOptW = new SeqStringParamOpsOptW(rl)

  def applyValidation[T](value:T,body:T => Validation[ERRORS[RequestError],T]):Validation[ERRORS[RequestError],T] = body(value)

  def is[T](message:String)(body:T => Boolean):T => Validation[ERRORS[RequestError],T] = {
    x => {
      allCatch.either(body(x)) match {
        case Left(t) => t.uncaught
        case Right(true) => x.success
        case Right(false) => message.invalid
      }
    }
  }

  class ParamOpsW[T](rl:RequestLogger[T]) {
    def check(body:T => Validation[ERRORS[RequestError],T]):RequestLogger[T] =
      rl.copy(over = rl.over.flatMap(x => applyValidation(x,body)))
  }

  class SeqParamOpsW[T](rl:RequestLogger[Seq[T]]) {
    def filter(test:T => Boolean):RequestLogger[Seq[T]] =
      rl.copy(over = rl.over.map(_.filter(test)))

    def isEmpty(value: => Validation[ERRORS[RequestError],Seq[T]]):RequestLogger[Seq[T]] =
      rl.over.fold(failure = f => (f.head::f.tail) match {
                                    case (Missing(_)::Nil) => rl.copy(over=value)
                                    case _ => rl
                                   },
                   success = x => if (x.isEmpty) rl.copy(over=value) else rl)
  }

  class SeqParamOpsOptW[T](rl:RequestLogger[Option[Seq[T]]]) {
    def filter(test:T => Boolean):RequestLogger[Option[Seq[T]]] =
      rl.copy(over = rl.over.map(_.map(_.filter(test))))

    def ignoreEmpty:RequestLogger[Option[Seq[T]]] = isEmpty(None.success)

    def isEmpty(value: => Validation[ERRORS[RequestError],Option[Seq[T]]]):RequestLogger[Option[Seq[T]]] =
      rl.over.fold(failure = _ => rl,
                   success = {
                     case None => rl.copy(over=value)
                     case Some(x) => if (x.isEmpty) rl.copy(over=value) else rl
                   })
  }

  class StringParamOpsW(rl:RequestLogger[String]) {
    def trim:RequestLogger[String] =
      rl.copy(over = rl.over.map(_.trim))
  }

  class SeqStringParamOpsW(rl:RequestLogger[Seq[String]]) {
    def trim:RequestLogger[Seq[String]] =
      rl.copy(over = rl.over.map(_.map(_.trim)))
  }

  class StringParamOpsOptW(rl:RequestLogger[Option[String]]) {
    def trim:RequestLogger[Option[String]] =
      rl.copy(over = rl.over.map(_.map(_.trim)))
  }

  class SeqStringParamOpsOptW(rl:RequestLogger[Option[Seq[String]]]) {
    def trim:RequestLogger[Option[Seq[String]]] =
      rl.copy(over = rl.over.map(_.map(_.map(_.trim))))

    def filterEmpty:RequestLogger[Option[Seq[String]]] = rl.filter(_.length > 0).ignoreEmpty
  }

  class ParamOpsOptW[T](rl:RequestLogger[Option[T]]) {
    def check(body:T => Validation[ERRORS[RequestError],T]):RequestLogger[Option[T]] =
      rl.over.fold(failure = _ => rl,
                   success = {
                     case None => rl
                     case Some(x) => applyValidation(x,body).fold(failure = f1 => rl.copy(over = f1.fail),
                                                             success = s => rl.copy(over = Some(s).success))
                   })
  }
}
