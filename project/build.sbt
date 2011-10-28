resolvers += "Web plugin repo" at "http://siasia.github.com/maven2"

resolvers += "Scala Tools Nexus" at "http://nexus.scala-tools.org/content/repositories/releases/"

libraryDependencies <+= sbtVersion(v => "com.github.siasia" %% "xsbt-web-plugin" % (v+"-0.2.7"))

libraryDependencies +=  "org.eclipse.jetty" % "jetty-webapp" % "7.3.0.v20110203"


