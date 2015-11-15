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

import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration.FiniteDuration

object TimeSeries extends DefaultJsonProtocol {
  /** Tuple timestamp/value. */
  type ValueType = MetricPoint#ValueType
  type Point = (Long, ValueType)
  type Aggregator = (Seq[ValueType]) => ValueType
  type BinOp = (Option[ValueType], Option[ValueType]) => Option[ValueType]
  implicit val toJson = jsonFormat2(TimeSeries.apply)

  def sum(values: Seq[ValueType]) = values.sum
  def mean(values: Seq[ValueType]) = values.sum / values.size

  /** Add two values with default 0 for missing values. */
  val plus: BinOp = (p1: Option[ValueType], p2: Option[ValueType]) =>
    Some(p1.getOrElse(0L) + p2.getOrElse(0L))
  /** Subtract two values if one of the values is missing the result is
    * [[scala.None]].
    */
  val minus: BinOp = (p1: Option[ValueType], p2: Option[ValueType]) =>
    if (p1.isDefined && p2.isDefined) Some(p1.get - p2.get) else None
  val avg: BinOp = (p1: Option[ValueType], p2: Option[ValueType]) =>
    (p1.toList ++ p2.toList) match {
      case List(x, y) => Some((x + y) / 2)
      case List(x) => Some(x)
      case Nil => None
    }

  val div: BinOp = (p1: Option[ValueType], p2: Option[ValueType]) =>
    if (p2.isDefined) Some(p1.getOrElse(0L) / p2.get) else None

  val mul: BinOp = (p1: Option[ValueType], p2: Option[ValueType]) =>
    Some(p1.getOrElse(1L) * p2.getOrElse(1L))

  val aggregators: Map[String, Aggregator] = Map("sum" -> sum, "mean" -> mean)

  private def sortByTime(p1: Point, p2: Point) = p1._1 < p2._1

  def downSample(timeSeries: TimeSeries, rate: FiniteDuration,
                 aggregate: Aggregator): TimeSeries = {
    val points = timeSeries.points.groupBy(
      (point) => point._1 - (point._1 % rate.toMillis))
    TimeSeries(timeSeries.name,
      points.toSeq.map(e => (e._1 - e._1 % rate.toMillis, aggregate(e._2.map(p => p._2)))).
        sortWith(sortByTime))
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

  /**
    * Apply a binary operator.
    * Input time-series must have the same sample rate, time-stamps must be
    * aligned to the sample-rate and points must be sorted by time.
    *
    * @param op1 first operand.
    * @param op2 second operand.
    * @param operator operator to apply
    * @return new time-series produced by applying
    */
  def binOp(op1: Seq[Point], op2: Seq[Point], operator: BinOp): List[Point] = {
    val p1 = op1.headOption
    val p2 = op2.headOption
    if (p1.isEmpty && p2.isEmpty) {
      Nil
    } else {
      val t1 = p1.map(_._1).getOrElse(Long.MaxValue)
      val t2 = p2.map(_._1).getOrElse(Long.MaxValue)
      val (p, newOp1, newOp2) = if (t1 == t2) {
        (operator(p1.map(_._2), p2.map(_._2)).map((t1, _)), op1.tail, op2.tail)
      } else if (t1 < t2) {
        (operator(p1.map(_._2), None).map((t1, _)), op1.tail, op2)
      } else {
        (operator(None, p2.map(_._2)).map((t2, _)), op1, op2.tail)
      }
      p.toList ::: binOp(newOp1, newOp2, operator)
    }
  }

  /**
    * Apply binary operator to time-series.
    * Input time-series must have the same sample rate, time-stamps must be
    * aligned to the sample-rate and points must be sorted by time.
    * @param op1 first time-series.
    * @param op2 second time-series
    * @param operator operation to apply
    * @param name name of new time-series.
    * @return new time-series.
    */
  def binOp(op1: TimeSeries, op2: TimeSeries, operator: BinOp, name: String): TimeSeries = {
    TimeSeries(name, binOp(op1.points, op2.points,
      operator))
  }

  /**
    * Create a time-series with the given time-range and sample rate and all
    * values set to value.
    * @param value fixed value.
    * @param from start time.
    * @param to end time.
    * @param sampleRate sample rate.
    * @param name optional name.
    * @return time-series name "name", or "value" in case name is None.
    */
  def static(value: TimeSeries.ValueType, from: Long, to: Long,
             sampleRate: FiniteDuration, name: Option[String] = None) =
    TimeSeries(name.getOrElse(value.toString),
      (from until(to, sampleRate.toMillis)).map((_, value)))
}

/**
 * Named sequence of data-points.
 * @param name target name.
 * @param points sequence of tuples time-stamp, value.
 */
case class TimeSeries(name: String, points: Seq[TimeSeries.Point])
