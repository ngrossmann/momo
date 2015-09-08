enablePlugins(DebianPlugin)

enablePlugins(RpmPlugin)

enablePlugins(UniversalPlugin)

enablePlugins(JavaServerAppPackaging)


organization  := "net.n12n.momo"

name := "momo"

maintainer := "Niklas Grossmann <ngrossmann@gmx.net>"

packageSummary := "Time-series database unsing Couchbase"

packageDescription := """Store all your metrics in Couchbase and display them with Grafana"""

licenses := List(("Apache License Version 2.0", url("http://www.apache.org/licenses/")))

version       := "0.1.0-SNAPSHOT"

scalaVersion  := "2.11.7"

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
    "com.softwaremill.reactivekafka" %% "reactive-kafka-core" % "0.8.0" exclude("log4j", "log4j"),
    "org.slf4j" % "slf4j-api" % "1.7.5",
    "org.slf4j" % "jcl-over-slf4j" % "1.7.5",
    "org.slf4j" % "log4j-over-slf4j" % "1.7.5",
    "ch.qos.logback" % "logback-classic" % "1.0.13",
    "org.scalatest" %% "scalatest" % "2.2.4" % "test",
    "io.kamon" %% "kamon-core" % "0.3.5",
    "io.kamon" %% "kamon-statsd" % "0.3.5",
    "io.kamon" %% "kamon-spray" % "0.3.5",
    "org.aspectj" % "aspectjweaver" % "1.8.5"
  )
}

linuxPackageMappings ++= Seq(
  packageMapping(
    (new File(baseDirectory.value, "etc/application.conf"), "/etc/momo/application.conf")).
    withPerms("640").withConfig("true").withGroup("momo"),
  packageMapping(
    (new File(baseDirectory.value, "etc/logback-production.xml"), "/etc/momo/logback.xml")).
    withPerms("644").withConfig("true"),
  packageMapping(
    (new File(baseDirectory.value, "src/main/couchbase/dashboards.json"), "/usr/share/momo/dashboards.json")).
    withPerms("644"),
  packageMapping(
    (new File(baseDirectory.value, "src/main/couchbase/targets.json"), "/usr/share/momo/targets.json")).
    withPerms("644"),
  packageMapping(
    (new File(baseDirectory.value, "src/main/shell/momo-create-views"), "/usr/sbin/momo-create-views")).
    withPerms("755")
)

bashScriptExtraDefines ++= Seq(
  "addJava -Dconfig.file=\"$([ -d /etc/momo ] && echo /etc/momo || echo ${app_home}/../conf)/application.conf\"",
  "addJava -Dlogback.configurationFile=\"$([ -d /etc/momo ] && echo /etc/momo || echo ${app_home}/../conf)/logback.xml\"",
  "addJava -javaagent:${lib_dir}/org.aspectj.aspectjweaver-1.8.5.jar"
)

mappings in Universal in packageZipTarball ++= Seq(
  file("etc/application.conf") -> "conf/application.conf",
  file("etc/logback.xml") -> "conf/logback.xml",
  file("src/main/couchbase/dashboards.json") -> "share/dashboards.json",
  file("src/main/couchbase/targets.json") -> "share/targets.json",
  file("src/main/shell/momo-create-views") -> "bin/momo-create-views"
)

version in Universal := (version in Linux).value

version in Linux := CustomTasks.gitVersion(Keys.sLog.value, baseDirectory.value)

version in Rpm := (version in Linux).value

rpmVendor := (maintainer in Linux).value

rpmLicense := licenses.value.headOption.map(_._1)

debianPackageDependencies in Debian ++= Seq("java7-runtime-headless", "bash")

Revolver.settings

Revolver.reForkOptions := Revolver.reForkOptions.value.copy(
  runJVMOptions = Seq(s"-javaagent:${System.getProperty("user.home")}/.ivy2/cache/org.aspectj/aspectjweaver/jars/aspectjweaver-1.8.5.jar"))

CustomTasks.settings
