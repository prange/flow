package flow.operator

trait RouteBuilder {
	val operators : List[Operator]
	val routes : List[PortWire]
}