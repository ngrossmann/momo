import sbt._
import Keys._

object CustomTasks {
  val copy = taskKey[Unit]("Copy Grafana files to target directory.")

  lazy val settings = Seq(
    copy := copyTask(streams.value, (classDirectory in Compile).value)
  )

  private def copyTask(stream: TaskStreams, target: File): Unit = {
    val grafanaDir = new File(target, "grafana")
    stream.log.info(s"Copying Grafana to ${grafanaDir}")
    IO.copyDirectory(file("src/main/js/dist/"), grafanaDir, overwrite = true)
  }
}
