package flow.server
import com.codecommit.antixml.StAXParser

import flow.data._
import flow.report.ProcessMap
import flow.report.TransitionCount
import flow._
import scalaz.Scalaz._
import scalaz.effects._
import scalaz._

class FlowServer {

	val data = new Data()

	def getBizLocMap = getMap(ProcessMap.createBizLocationMap)
	
	def getBizStepMap = getMap(ProcessMap.createBizStepMap)
	
	def getMap( f : Iterable[Process] ⇒ Action[List[TransitionCount]] ) = {
			val result = data.queryProcess( PredicateProcessQuery( e ⇒ true ) ).unsafePerformIO
			val transitions = f( result ).unsafePerformIO
			val stripped = transitions.map( l ⇒ l.map( t ⇒ TransitionCount( ProcessMap.stripPrefix( t.transition ), t.count ) ) )
			stripped
	
	}

	
	def capture( body : java.io.Reader ) : IO[Validation[String, String]] = {
		val events = Parser.parseReader( body )
		data.handle( EventObservation( events ) )
	}

}

class TestFlowServer extends FlowServer{
	val filename = "gsport_epcis_events2.xml"
	override val data = loadData()
	def loadData() = {
		val events = Parser.parseFile( filename )

		val observations = events.map( Parser.createEventList ).flatten

		val flow = new Data()

		flow.handle( AddEnrichment( WeekdayEnricher() ) ).unsafePerformIO

		flow.handle( EventObservation( observations ) ).unsafePerformIO

		flow.handle( BuildChain( ( e : Event ) ⇒ true, e ⇒ e.values( "epc" ) ) ).unsafePerformIO

		flow.handle( BuildProcess( c ⇒ true, List(), p ⇒ p ) ).unsafePerformIO

		flow
	}

}