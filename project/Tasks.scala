import java.nio.file.{StandardCopyOption, Files}

import com.typesafe.sbt.packager.archetypes.ServerLoader
import sbt._
import Keys._

object CustomTasks {

  val jsSource = settingKey[File]("JavaScript source directory")
  val packageGrafana = taskKey[Unit]("Copy Grafana files to target directory.")

  val buildGrafana = taskKey[File]("Run grunt to build Grafana.")

  lazy val settings = Seq(
    jsSource := new File(baseDirectory.value, "src/main/js"),
    buildGrafana := buildGrafana(streams.value, jsSource.value, target.value),
    products in Compile := {
      buildGrafana.value :: (products in Compile).value.toList
    }
  )

  private def buildGrafana(stream: TaskStreams, grafanaSource: File, target: File): File = {
    val jsTargetDir = new File(target, "js")
    val grafanaDir = new File(jsTargetDir, "grafana")
    if (!grafanaDir.isDirectory) {
      grafanaDir.mkdirs()
      val nodeModules = new File(grafanaSource, "node_modules")
      if (!nodeModules.exists()) {
        Process("npm" :: "install" :: Nil, grafanaSource) ! stream.log
      }
      Process("grunt" :: "build" :: Nil, grafanaSource) ! stream.log
      val distFolder = new File(grafanaSource, "dist")
      stream.log.info(s"Moving Grafana from ${distFolder} to ${grafanaDir}")
      Files.move(distFolder.toPath, grafanaDir.toPath, StandardCopyOption.REPLACE_EXISTING)

    } else {
      stream.log.info(s"${grafanaDir.getPath} exists skipping grunt build")
    }
    jsTargetDir
  }

  def gitVersion(logger: Logger, baseDirectory: File): String = {
    try {

      val desc = Process("git" :: "describe" :: "--match=v*" :: Nil, baseDirectory).!!
      desc.trim.substring(1).split("-").toList match {
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

  def packageSystemd(state: State, debVersion: String): File = {
    import com.typesafe.sbt.SbtNativePackager.Debian
    import com.typesafe.sbt.packager.Keys.serverLoading
    val extracted = Project.extract(state)
    val newState = extracted.append(Seq(
      version in Debian := s"${debVersion}-sd",
      serverLoading in Debian := ServerLoader.Systemd
    ), state)
    Project.extract(newState).runTask(packageBin in Debian, newState)._2
  }
}
