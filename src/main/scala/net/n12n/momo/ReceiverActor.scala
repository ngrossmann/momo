/*
 * Copyright 2015 Niklas Grossmann
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package net.n12n.momo

import akka.actor._
import akka.util.ByteString
import net.n12n.momo.couchbase.{MetricActor, MetricPoint}

object ReceiverActor {
  type MetricParser = (String) => Either[String, MetricPoint]

  def propsStatsD(metricActor: ActorSelection) = Props(classOf[UdpReceiverActor],
    metricActor, "momo.statsd", parseMetric)

  def propsUdpGraphite(metricActor: ActorSelection) = Props(classOf[UdpReceiverActor],
    metricActor, "momo.graphite-udp", parseGraphiteMetric)
  def propsTcpGraphite(metricActor: ActorSelection) = Props(classOf[TcpReceiverActor],
    metricActor, "momo.graphite-tcp", parseGraphiteMetric)
  val statsDRegex = "^([^:]+):([^|]+)\\|([cgs]|ms)(\\|@([0-9.]+))?".r

  private val parseMetric: MetricParser =
    (metric: String) => metric match {
      case statsDRegex(path, value) =>
        Right(MetricPoint(path, System.currentTimeMillis(),
          scala.math.round(value.toDouble)))
      case statsDRegex(path, value, spec, _*) =>
        Right(MetricPoint(s"${path}_${spec}", System.currentTimeMillis(),
          scala.math.round(value.toDouble)))
      case m =>
        Left(metric)
    }

  private val parseGraphiteMetric: MetricParser = (metric: String) => {
    val parts = metric.trim().split(" ")

    if (parts.length == 3)
      Right(MetricPoint(parts(0), parts(2).toLong * 1000,
        scala.math.round(nanSave(parts(1)))))
    else
      Left(metric)
  }

  def nanSave(s: String) =
    if (s.equalsIgnoreCase("nan")) Double.NaN else s.toDouble
}

trait ReceiverActor {
  this: ActorLogging =>
  protected val parseMetric: ReceiverActor.MetricParser

  protected def parseMetrics(data: ByteString): Seq[MetricPoint] = {
    val string = data.decodeString("UTF-8")
    try {
      val metrics = string.split("\n")
      val points = metrics.map(parseMetric)
      if (log.isDebugEnabled) {
        log.debug("The following metrics were not processed: {}",
          points.flatMap(_.left.toSeq).mkString(", "))
      }
      points.flatMap(_.right.toSeq)
    } catch {
      case e: Exception =>
        log.error(e, "Failed to parse metrics: {}", string)
        Nil
    }
  }
}

