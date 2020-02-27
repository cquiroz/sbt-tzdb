addSbtPlugin("io.github.cquiroz" % "sbt-tzdb" % sys.props.getOrElse("plugin.version", sys.error("'plugin.version' environment variable is not set")))

addSbtPlugin("org.scala-js"      % "sbt-scalajs"  % "0.6.32")
