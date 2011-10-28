name := "Flow"

version := "0.1"

organization := "com.fourspark"

scalaVersion := "2.9.0-1"

scalacOptions := Seq("-deprecation", "-unchecked")

seq(webSettings :_*)

libraryDependencies ++= Seq(
	// Pick your favorite slf4j binding
	"ch.qos.logback" % "logback-classic" % "0.9.25" % "runtime",
	//Solr, not yet..."org.apache.solr" % "solr-solrj" % "3.2.0",
	"joda-time" % "joda-time" % "1.6.2",
	"com.h2database" % "h2" % "1.3.160",
	"org.neo4j" % "neo4j" % "1.5.M01",
	"org.apache.lucene" % "lucene-core" % "3.3.0",
	"com.codecommit" %% "anti-xml" % "0.3",
	"net.databinder" %% "unfiltered-filter" % "0.5.0",
	"net.databinder" %% "unfiltered-spec" % "0.5.0" % "test",
	"net.databinder" %% "unfiltered-json" % "0.5.0",
	"javax.servlet" % "servlet-api" % "2.3" % "provided",
	"org.eclipse.jetty" % "jetty-webapp" % "7.3.0.v20110203" % "container",
	"joda-time" % "joda-time" % "1.6.2",
	"org.scalaz" %% "scalaz-core" % "6.0.2",
	"org.specs2" %% "specs2" % "1.4",
	"org.apache.mahout" % "mahout-core" % "0.5"
)
  
  
testOptions := Seq(Tests.Filter(s => Seq("Spec", "Unit").exists(s.endsWith(_))))

parallelExecution in Test := false
   
// Only needed to track snapshot releases, SBT automatically includes the releases repository.
resolvers += "Scala Tools Snapshots" at "http://scala-tools.org/repo-snapshots/"

resolvers += "Sonatype Nexus" at "https://oss.sonatype.org/content/repositories"

resolvers += "Sonatype Nexus Snapshots" at "https://oss.sonatype.org/content/repositories/snapshots"

resolvers += "Maven" at "http://repo1.maven.org/maven2/"
