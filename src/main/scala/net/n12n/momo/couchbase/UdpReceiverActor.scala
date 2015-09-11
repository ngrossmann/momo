package net.n12n.momo.couchbase

import java.net.InetSocketAddress

import akka.actor._
import akka.event.LoggingAdapter
import akka.io.{IO, Udp}
import akka.util.ByteString
import spray.util.LoggingContext

import scala.util.matching.Regex

object UdpReceiverActor {
  def propsStatsD(metricActor: ActorSelection) = Props(classOf[UdpReceiverActor],
    metricActor, "momo.statsd", parseMetric)

  def propsGraphite(metricActor: ActorSelection) = Props(classOf[UdpReceiverActor],
    metricActor, "momo.graphite", parseGraphiteMetric)

  private val statsDRegex = "^([^:]+):([^|]+)\\|([cgs]|ms)(\\|@([0-9.]+))?".r
  private val parseMetric =
    (metric: String) => statsDRegex.findFirstMatchIn(metric).map {
          m => val path =
            if (m.groupCount < 3) m.group(1) else s"${m.group(1)}_${m.group(3)}"
        MetricPoint(path, System.currentTimeMillis(),
          scala.math.round(m.group(2).toDouble))
    }

  private val parseGraphiteMetric = (metric: String) => {
    val parts = metric.trim().split(" ")

    if (parts.length == 3)
      MetricPoint(parts(0), parts(2).toLong * 1000,
        scala.math.round(nanSave(parts(1))))
    else
      throw new IllegalArgumentException(
        s"`${metric}` is not a valid Graphite metric")
  }

  def nanSave(s: String) =
    if (s.equalsIgnoreCase("nan")) Double.NaN else s.toDouble
}

class UdpReceiverActor(metricActor: ActorSelection, configPath: String,
                  parseMetric: (String) => MetricPoint)
  extends Actor with ActorLogging {
  import context.system
  private val address = system.settings.config.getString(
    s"${configPath}.listen-address")
  private val port = system.settings.config.getInt(s"${configPath}.port")
  IO(Udp) ! Udp.Bind(self, new InetSocketAddress(address, port))

  def receive = {
    case Udp.Bound(address) =>
      log.info("StatsD actor bound to {}", address)
      context.become(ready(sender()))
  }

  def ready(socket: ActorRef): Receive = {
    case Udp.Received(data, peer) =>
      parseMetrics(data).foreach(p => metricActor ! MetricActor.Save(p))
    case Udp.Unbind =>
      log.info("StatsD actor received unbind request")
      socket ! Udp.Unbind
    case Udp.Unbound =>
      log.info("StatsD actor stopping")
      context.stop(self)
  }

  private def parseMetrics(data: ByteString): Seq[MetricPoint] = {
    val string = data.decodeString("UTF-8")
    try {
      val metrics = string.split("\n")
      val points = metrics.map(parseMetric)
      points
    } catch {
      case e: Exception =>
        log.error(e, "Failed to parse metrics: {}", string)
        Nil
    }
  }
}
