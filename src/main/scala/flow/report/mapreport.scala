package flow.report
import flow._
import scalaz._
import effects._
import Scalaz._
import flow.data.Data
import data.Process
import data.Event
import scala.annotation.tailrec

object ProcessMap {

	def createBizLocationMap( processes : Iterable[Process] ) : Action[List[TransitionCount]] = {
		def getLocation : Event ⇒ String = e ⇒ e.values.getOrElse( "bizLocation", "<unknown>" )
		createMap( processes, getLocation )
	}

	def createBizStepMap( processes : Iterable[Process] ) : Action[List[TransitionCount]] = {
		def getLocation : Event ⇒ String = e ⇒ e.values.getOrElse( "bizStep", "<unknown>" )
		createMap( processes, getLocation )
	}

	def createMap( processes : Iterable[Process], key : Event ⇒ String ) : Action[List[TransitionCount]] = io {

		def incrementCount( transition : Transition, map : Map[Transition, Int] ) : Map[Transition, Int] = {
			map + ( transition -> ( map.getOrElse( transition, 0 ) + 1 ) )
		}

		@tailrec def countByProcess( processes : List[Process], transitions : Map[Transition, Int] ) : Map[Transition, Int] = {

			@tailrec def countByEvents( events : List[Event], transitions : Map[Transition, Int] ) : Map[Transition, Int] = events match {

				case Nil ⇒ transitions
				case x1 :: Nil ⇒ transitions
				case x1 :: x2 :: xs ⇒ countByEvents( xs, incrementCount( toTransition( x1, x2, key ), transitions ) )

			}

			processes match {
				case Nil ⇒ transitions
				case p1 :: ps ⇒ countByProcess( ps, countByEvents( p1.eventChain.events, transitions ) )
			}

		}

		countByProcess( processes.toList, Map.empty ).toList.map( t ⇒ TransitionCount( t._1, t._2 ) ).success[String]

	}

	def toTransition( e1 : Event, e2 : Event, f : Event ⇒ String ) = {
		new Transition( f( e1 ), f( e2 ) )
	}

	def stripPrefix( t : Transition ) : Transition = {
		def lastval( in : String ) = in.split( ':' ).last
		Transition( lastval( t.from ), lastval( t.to ) )
	}

}

case class Transition( from : String, to : String )
case class TransitionCount( transition : Transition, count : Int )
		
