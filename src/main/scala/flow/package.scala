import scalaz._
import Scalaz._
import effects._

package object flow {

	type Action[T] = IO[Validation[String,T]]
	type Predicate[T] =  ( T ⇒ Boolean )
}