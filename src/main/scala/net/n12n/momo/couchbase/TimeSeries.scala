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

package net.n12n.momo.couchbase

import spray.json.DefaultJsonProtocol
import spray.httpx.marshalling._
import spray.httpx.SprayJsonSupport._

import scala.concurrent.duration.FiniteDuration

object TimeSeries extends DefaultJsonProtocol {
  /** Tuple timestamp/value. */
  type Point = (Long, Long)
  type Aggregator = (Seq[Long]) => Long
  implicit val toJson = jsonFormat2(TimeSeries.apply)

  def sum(values: Seq[Long]) = values.sum
  def mean(values: Seq[Long]) = values.sum / values.size
  val aggregators: Map[String, Aggregator] = Map("sum" -> sum, "mean" -> mean)

  private def sortByTime(p1: Point, p2: Point) = p1._1 < p2._1

  def downSample(timeSeries: TimeSeries, rate: FiniteDuration,
                 aggregate: Aggregator): TimeSeries = {
    val points = timeSeries.points.groupBy(
      (point) => point._1 - (point._1 % rate.toMillis))
    TimeSeries(timeSeries.name,
      points.toSeq.map(e => (e._1, aggregate(e._2.map(p => p._2)))).sortWith(sortByTime))
  }

  /**
   * Get a function which converts a time-series to another time-series using
   * a given sampling rate and
   * [[net.n12n.momo.couchbase.TimeSeries.aggregators aggregator]] function and
   * sample rate. If `function` is `None` or unknown [[scala.Predef.identity()]]
   * is returned.
   *
   * @param function aggregation function name.
   * @param sampleRate sample rate.
   * @return conversion function.
   */
  def sampler(function: Option[String], sampleRate: FiniteDuration): TimeSeries => TimeSeries = {
    function.flatMap(TimeSeries.aggregators.get(_)).map(
      (a: Aggregator) =>
        (ts: TimeSeries) => TimeSeries.downSample(ts, sampleRate, a)
    ).getOrElse(identity _)
  }
}

/**
 *
 * @param name target name.
 * @param points sequence of tuples time-stamp, value.
 */
case class TimeSeries(name: String, points: Seq[TimeSeries.Point])
