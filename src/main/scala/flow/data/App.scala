package flow.data
import com.codecommit.antixml._

import flow.data._
import scala.io._
import java.io._
import scala.collection.mutable.ArrayBuffer
import scalaz._
import Scalaz._

object App {

	def main( args : Array[String] ) {
		args match {
			case Array( filename ) ⇒ run( filename )
			case _ ⇒ run( "gsport_epcis_events2.xml" )
		}

	}

	def run( filename : String ) = {

		val events = Parser.parseFile(filename)

		val observations = events.map( Parser.createEventList ).flatten

		val flow = new Data()

		val register = observations.map( flow.handle ).sequence

		register.unsafePerformIO

		val chain = flow.handle( BuildChain( e ⇒ true ,e=>e.values("epc") ) )

		chain.unsafePerformIO

		val build = flow.handle( BuildProcess( c ⇒ true, Nil ) )

		build.unsafePerformIO
		

	}
}  
	
	
