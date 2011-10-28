package flow.data
import flow._
import flow.data._
import scalaz._
import Scalaz._
import scalaz.effects._
import scala.collection.mutable.ArrayBuffer

class Data {

	val eventlog = new RunningEventLog( ArrayBuffer[Event]() )

	val enricher = new Enrichments()

	val chains = new EventChains()

	val processes = new Processes()

	def handle( msg : Control ) : IO[Validation[String,String]] = msg match {
		case EventObservation( e ) ⇒ eventlog.record( e )
		case AddEnrichment( e ) ⇒ enricher.add( e )
		case BuildChain( pred, f ) ⇒ buildChains( pred, f )
		case BuildProcess( query, cut, enricher ) ⇒ buildProcess( query, cut )
		case _ ⇒ io {"unknownControlMessage".fail[String]}
	}

	def queryProcess( qry : ProcessQuery ) : IO[Iterable[Process]] = qry match {
		case PredicateProcessQuery( pred ) ⇒ processes.query( pred )
		case _ ⇒ io { List[Process]() }
	}

	def queryEvent( qry : EventQuery ) : IO[Iterable[Event]] = qry match {
		case PredicateEventQuery( pred ) ⇒ eventlog.query( pred )
		case _ ⇒ io { List[Event]() }
	}

	def buildChains( pred : Event ⇒ Boolean, f : Event ⇒ String ) = {
		for (
			_ ← chains.clear;
			view ← eventlog.query( pred );
			_ ← chains.record( view, f )
		) yield ("ok".success[String])
	}

	def buildProcess( query : EventChain ⇒ Boolean, cutpoints : List[CutPoint] ) = {
		for (
			_ ← processes.clear;
			cs ← chains.query( query );
			_ ← processes.record( cs.flatMap( c ⇒ new Cutter( cutpoints ).split( c ) ).map(ec=> Process( ec ) ) )
		) yield ("ok".success[String])

	}

}





