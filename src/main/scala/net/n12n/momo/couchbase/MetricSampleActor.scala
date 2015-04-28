package net.n12n.momo.couchbase

import akka.actor.{ActorLogging, Props, Actor}

import net.n12n.momo.util.RichConfig._

object MetricSampleActor {
  case class Action(name: String, time: Long)
  case object Tick

  def props() = Props[MetricSampleActor]
}

class MetricSampleActor extends Actor with ActorMonitoring with ActorLogging {
  import MetricSampleActor._
  import context.system
  import context.dispatcher

  val parentName = context.parent.path.toStringWithoutAddress.replaceAll("[/$.]", "_")
  val prefix = s"${seriesKeyPrefix}.${parentName}"
  var metrics = scala.collection.mutable.HashMap[String, Metric]()

  override def preStart(): Unit = {
    system.scheduler.schedule(tickInterval, tickInterval, self, Tick)
  }

  override def receive = {
    case Action(name, time) =>
      metrics.get(name) match {
        case Some(m) =>
          m.count += 1
          m.totalTime += time
        case None =>
          metrics.put(name, new Metric(1, time))
    }

    case Tick =>
      metrics.foreach { e =>
        e._2.toMetricPoints(s"${prefix}.${e._1}").foreach {
          context.parent ! MetricActor.Save(_)
        }
      }
      metrics = metrics.empty
  }

  class Metric(var count: Long, var totalTime: Long) {
    def toMetricPoints(prefix: String): Seq[MetricPoint] = {
      val now = System.currentTimeMillis()
      val c = MetricPoint(s"${prefix}-count_g", now, count)
      if (count > 0)
        Seq(c, MetricPoint(s"${prefix}-processing-time_ms", now, totalTime / count))
      else
        Seq(c)
    }
  }
}
