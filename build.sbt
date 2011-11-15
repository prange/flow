name := "flow-core"

version := "0.1"

organization := "com.fourspark"

scalaVersion := "2.9.1"

scalacOptions := Seq("-deprecation", "-unchecked")


libraryDependencies ++= Seq(
	//"org.apache.solr" % "solr-solrj" % "3.2.0",
	//"org.apache.lucene" % "lucene-core" % "3.3.0",
	//"com.h2database" % "h2" % "1.3.160",
	//"org.neo4j" % "neo4j" % "1.5.M01",
	"ch.qos.logback" % "logback-classic" % "0.9.25" % "runtime",
	"joda-time" % "joda-time" % "1.6.2",
	"com.codecommit" %% "anti-xml" % "0.3",
	"joda-time" % "joda-time" % "1.6.2",
	"org.scalaz" %% "scalaz-core" % "6.0.2",
	"org.specs2" %% "specs2" % "1.6.1",
	"weka" % "weka" % "3.6.3",
	"se.scalablesolutions.akka" % "akka-actor" % "1.2",
	"se.scalablesolutions.akka" % "akka-amqp" % "1.2"
)
  
  
testOptions := Seq(Tests.Filter(s => Seq("Spec", "Unit").exists(s.endsWith(_))))

parallelExecution in Test := false
   
resolvers += "Scala Tools Snapshots" at "http://scala-tools.org/repo-snapshots/"

resolvers += "Sonatype Nexus" at "https://oss.sonatype.org/content/repositories/central"

resolvers += "Sonatype Nexus Snapshots" at "https://oss.sonatype.org/content/repositories/snapshots"

resolvers += "Apache Snapshots" at "https://repository.apache.org/content/repositories/snapshots"

resolvers += "Compass Labs" at "http://build.compasslabs.com/maven/content/repositories/thirdparty"

resolvers += "Maven" at "http://repo1.maven.org/maven2/"

resolvers += "Typesafe repo" at "http://repo.typesafe.com/typesafe/releases/"
