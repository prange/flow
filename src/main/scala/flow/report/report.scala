package flow.report

trait CSVReportGenerator {

	def generate(table:Tuple2Table):Array[Byte]
	
}



case class Tuple2Table(
		headers:List[Tuple2[String,String]], 
		content:List[Tuple2[String,String]]
		)
		
