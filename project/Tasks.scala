import sbt._
import Keys._

object CustomTasks {
  val jsSource = settingKey[File]("JavaScript source directory")
  val copy = taskKey[Unit]("Copy Grafana files to target directory.")

  val buildGrafana = taskKey[File]("Run grunt to build Grafana.")

  lazy val settings = Seq(
    jsSource := new File(baseDirectory.value, "src/main/js"),
    buildGrafana := buildGrafana(streams.value, jsSource.value),
    copy := copyTask(streams.value, buildGrafana.value, (classDirectory in Compile).value)
  )

  private def buildGrafana(stream: TaskStreams, grafanaSource: File): File = {
    val nodeModules = new File(grafanaSource, "node_modules")
    if (!nodeModules.exists()) {
      Process("npm" :: "install" :: Nil, grafanaSource) ! stream.log
    }
    Process("grunt" :: "build" :: Nil, grafanaSource) ! stream.log
    new File(grafanaSource, "dist")
  }

  private def copyTask(stream: TaskStreams, grafanaDist: File, target: File): Unit = {
    val grafanaDir = new File(target, "grafana")
    stream.log.info(s"Copying Grafana to ${grafanaDir}")
    IO.copyDirectory(grafanaDist, grafanaDir, overwrite = true)
  }

  def gitVersion(logger: Logger, baseDirectory: File): String = {
    try {

      val desc = Process("git" :: "describe" :: "--match=v*" :: Nil, baseDirectory).!!
      desc.substring(1).split("-").toList match {
        case version :: commit :: rest => s"${version}+${commit}"
        case version :: Nil => version
        case Nil => "0.0.0~unknown"
      }
    } catch {
      case e: RuntimeException =>
        logger.warn("Cannot build version from git, `git describe --match=v*' failed" +
          ", using default 0.0.0~unknown")
        "0.0.0~unknown"
    }
  }
}
