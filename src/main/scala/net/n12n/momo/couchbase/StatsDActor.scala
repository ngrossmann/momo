package net.n12n.momo.couchbase

import java.net.InetSocketAddress

import akka.actor._
import akka.io.{IO, Udp}
import akka.util.ByteString

object StatsDActor {
  def props(bucketActor: ActorSelection) = Props(classOf[StatsDActor], bucketActor)
}

class StatsDActor(bucketActor: ActorSelection) extends Actor with ActorLogging {
  import context.system
  private val regex = "^([^:]+):([^|]+)\\|([cgs]|ms)(\\|@([0-9.]+))?".r
  private val address = system.settings.config.getString("momo.statsd.listen-address")
  private val port = system.settings.config.getInt("momo.statsd.port")
  IO(Udp) ! Udp.Bind(self, new InetSocketAddress(address, port))

  def receive = {
    case Udp.Bound(address) =>
      log.info("StatsD actor bound to {}", address)
      context.become(ready(sender()))
  }

  def ready(socket: ActorRef): Receive = {
    case Udp.Received(data, peer) =>
      parseMetrics(data).foreach(p => bucketActor ! MetricActor.Save(p))
    case Udp.Unbind =>
      log.info("StatsD actor received unbind request")
      socket ! Udp.Unbind
    case Udp.Unbound =>
      log.info("StatsD actor stopping")
      context.stop(self)
  }

  private def parseMetrics(data: ByteString): Seq[MetricPoint] = {
    val string = data.decodeString("UTF-8")
    val metrics = string.split("\n")
    val points = metrics.map(parseMetric).filter(_.isDefined).map(_.get)
    if (points.length != metrics.length) {
      log.warning("Only {} of {} metrics in >{}< parsed", points.length,
        metrics.length, string)
    }
    points
  }

  private def parseMetric(metric: String): Option[MetricPoint] = {
    try {
      regex.findFirstMatchIn(metric).map(m => MetricPoint(
        m.group(1), System.currentTimeMillis(), scala.math.round(m.group(2).toDouble)))
    } catch {
      case e: Exception =>
        log.error(e, "Conversion of {} to metric failed", metric)
        None
    }
  }
}
