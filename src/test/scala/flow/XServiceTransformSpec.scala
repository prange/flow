package flow

import org.specs2.mutable.Specification
import com.codecommit.antixml.StAXParser
import flow.epcis.EpcisTransform
import flow.event.XmlEvent
import flow.epcis.ObservationTransform
import flow.epcis.ProductEnricher

object XServiceTransformSpec extends Specification {
	"When parsing XService events" should {

		"One Object event should create one typed epcisevent" in {
			
			val evnt = createObjectEvent
			EpcisTransform().apply(XmlEvent(Time.now.unsafePerformIO,"",evnt)).isRight mustEqual true
		}
		
		"One Object event should create one observation" in {
			
			val evnt = createObjectEvent
			
			(EpcisTransform() andThen ObservationTransform()).apply(XmlEvent(Time.now.unsafePerformIO,"",evnt)).size mustEqual 1
		}
		
		"One Object event should result in enriched observation" in {
			
			val evnt = createObjectEvent
			
			(EpcisTransform() andThen ObservationTransform() andThen ProductEnricher()).apply(XmlEvent(Time.now.unsafePerformIO,"",evnt)).headOption.map(_.values.get("product").isDefined).getOrElse(false) mustEqual true
		}
	}
	
	
	def createObjectEvent = new StAXParser().fromString(objEventXml)
		
		val objEventXml = """ <ObjectEvent>
                  <eventTime>2011-11-03T16:46:27.676Z</eventTime>
                  <recordTime>2011-11-03T15:39:53.641Z</recordTime>
                  <eventTimeZoneOffset>+01:00</eventTimeZoneOffset>
                  <epcList>
                     <epc>urn:epc:id:gsrn:7071576.0000138532</epc>
                  </epcList>
                  <action>ADD</action>
                  <bizStep>urn:epcglobal:cbv.disp:bizstep:commissioning</bizStep>
                  <disposition>urn:epcglobal:cbv:disp:active</disposition>
                  <readPoint>
                     <id>urn:hrafn:xservice:readpoint:imei:352961045227623</id>
                  </readPoint>
                  <bizLocation>
                     <id>urn:hrafn.xservice:bizLocation:g-sport_lefstad</id>
                  </bizLocation>
                  <hrafnxservice:chain xmlns:hrafnxservice="http://www.hrafn.com/epcis/hrafn">G-Sport</hrafnxservice:chain>
                  <hrafnxservice:services xmlns:hrafnxservice="http://www.hrafn.com/epcis/hrafn"></hrafnxservice:services>
                  <hrafnxservice:customerFirstName xmlns:hrafnxservice="http://www.hrafn.com/epcis/hrafn">Reidun</hrafnxservice:customerFirstName>
                  <hrafnxservice:customerLastName xmlns:hrafnxservice="http://www.hrafn.com/epcis/hrafn">Lie</hrafnxservice:customerLastName>
                  <hrafnxservice:customerBrand xmlns:hrafnxservice="http://www.hrafn.com/epcis/hrafn">Diamant</hrafnxservice:customerBrand>
                  <hrafnxservice:customerModel xmlns:hrafnxservice="http://www.hrafn.com/epcis/hrafn">Ex.avalanche</hrafnxservice:customerModel>
                  <hrafnxservice:customerCellPhone xmlns:hrafnxservice="http://www.hrafn.com/epcis/hrafn">90504001</hrafnxservice:customerCellPhone>
                  <hrafnxservice:customerContactOptions xmlns:hrafnxservice="http://www.hrafn.com/epcis/hrafn">sms</hrafnxservice:customerContactOptions>
                  <hrafnxservice:totalPrice xmlns:hrafnxservice="http://www.hrafn.com/epcis/hrafn">0</hrafnxservice:totalPrice>
                  <hrafnxservice:comment xmlns:hrafnxservice="http://www.hrafn.com/epcis/hrafn">har kjøpt og selv montert nytt styrlager.se hva som må gjøres.</hrafnxservice:comment>
               </ObjectEvent>"""
}