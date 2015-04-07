enablePlugins(DebianPlugin)

enablePlugins(JavaServerAppPackaging)

net.virtualvoid.sbt.graph.Plugin.graphSettings

organization  := "net.n12n.momo"

name := "momo"

maintainer := "Niklas Grossmann <ngrossmann@gmx.net>"

packageSummary := "Time-series database unsing Couchbase"

packageDescription := """Store all your metrics in Couchbase and display them with Grafana"""

version       := "0.1.0-SNAPSHOT"

scalaVersion  := "2.11.6"

scalacOptions := Seq("-unchecked", "-deprecation", "-encoding", "utf8", "-feature", "-language:postfixOps")

fork in Test := true

resolvers ++= Seq(
  "spray repo" at "http://repo.spray.io/"
)

fullClasspath in Runtime += new File(baseDirectory.value, "etc")

libraryDependencies ++= {
  val akkaV = "2.3.9"
  val sprayV = "1.3.2"
  Seq(
    "io.spray"            %%   "spray-caching"   % sprayV,
    "io.spray"            %%   "spray-can"     % sprayV,
    "io.spray"            %%   "spray-routing" % sprayV,
    "io.spray"            %%  "spray-json" % "1.3.1",
    "com.typesafe.akka"   %%  "akka-actor"    % akkaV,
    "com.typesafe.akka"   %%  "akka-testkit"  % akkaV,
    "com.typesafe.akka"   %%  "akka-slf4j"  % akkaV,
    "com.couchbase.client" % "java-client" % "2.0.3",
    "org.slf4j" % "slf4j-api" % "1.7.5",
    "org.slf4j" % "jcl-over-slf4j" % "1.7.5",
    "ch.qos.logback" % "logback-classic" % "1.0.13",
    "org.scalatest" %% "scalatest" % "2.2.4" % "test"
  )
}

bashScriptExtraDefines += """addJava "-Dconfig.file=/etc/momo/application.conf""""
bashScriptExtraDefines += """addJava "-Dlogback.configurationFile=/etc/momo/logback.xml""""

linuxPackageMappings += packageMapping(
  (new File(baseDirectory.value, "etc/application.conf"), "/etc/momo/application.conf")).
  withPerms("640").withConfig("true").withGroup("momo")

linuxPackageMappings += packageMapping(
  (new File(baseDirectory.value, "etc/logback-production.xml"), "/etc/momo/logback.xml")).
  withPerms("644").withConfig("true")

debianPackageDependencies in Debian ++= Seq("java7-runtime-headless", "bash")

Revolver.settings

CustomTasks.settings
