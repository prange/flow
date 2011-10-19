package flow.analyzer
import flow.data._

trait FrequentPatterAnalyzer {

	def generateFrequentPatterns( processes : List[EventChain], pred : EventChain ⇒ Boolean ) : String

}