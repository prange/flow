name := "Flow"

version := "0.1"

organization := "com.fourspark"

scalaVersion := "2.9.0-1"


libraryDependencies ++= Seq(
"org.scalatra" %% "scalatra-specs2" % "2.0.0.M4" % "test",
// Pick your favorite slf4j binding
"ch.qos.logback" % "logback-classic" % "0.9.25" % "runtime",
//Solr, not yet..."org.apache.solr" % "solr-solrj" % "3.2.0",
"org.scalaz" %% "scalaz-core" % "6.0.1",
"joda-time" % "joda-time" % "1.6.2",
"com.h2database" % "h2" % "1.3.160",
"org.neo4j" % "neo4j" % "1.5.M01",
"org.apache.lucene" % "lucene-core" % "3.3.0",
 "com.codecommit" %% "anti-xml" % "0.3"
)
  
  
testOptions := Seq(Tests.Filter(s => Seq("Spec", "Unit").exists(s.endsWith(_))))

parallelExecution in Test := false
   
// Only needed to track snapshot releases, SBT automatically includes the releases repository.
resolvers += "Scala Tools Snapshots" at "http://scala-tools.org/repo-snapshots/"

resolvers += "Sonatype Nexus Snapshots" at "https://oss.sonatype.org/content/repositories/snapshots"

resolvers += "Maven" at "http://repo1.maven.org/maven2/"
