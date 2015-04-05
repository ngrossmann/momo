import NativePackagerKeys._

net.virtualvoid.sbt.graph.Plugin.graphSettings

organization  := "net.n12n.momo"

name := "momo"

version       := "0.1.0-SNAPSHOT"

scalaVersion  := "2.11.6"

scalacOptions := Seq("-unchecked", "-deprecation", "-encoding", "utf8", "-feature", "-language:postfixOps")

fork in Test := true

packageArchetype.java_server

resolvers ++= Seq(
  "spray repo" at "http://repo.spray.io/"
)

fullClasspath in Runtime += new File(baseDirectory.value, "etc")

makeBatScript := None

scriptClasspath += "../etc"

// javaOptions := Seq("-Dconfig.trace=loads")


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

Revolver.settings

CustomTasks.settings