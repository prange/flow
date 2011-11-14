package flow.actor
import akka.actor.Actor

class LoggerActor extends Actor {

	def receive = {
		case Warn( text ) ⇒ println( "WARN: %s".format( text ) )
		case Info( text ) ⇒ println( "INFO: %s".format( text ) )
		case Debug( text ) ⇒ println( "DEBUG: %s".format( text ) )
	}

}

case class Warn( text : String )
case class Info( text : String )
case class Debug( text : String )