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

import scala.concurrent.duration._
import org.scalatest.{ShouldMatchers, FlatSpecLike}

object TimeSeriesSpec {
  def timeSeries(start: Long, length: FiniteDuration, rate: FiniteDuration,
                name: String, values: Seq[TimeSeries.ValueType]): TimeSeries = {
    val toIndex = (t: Long) => ((t - start) / rate.toMillis % values.size).toInt
    val points = start.until(start + length.toMillis, rate.toMillis).map(
      t => (t, values(toIndex(t))))
    TimeSeries(name, points)
  }
}
class TimeSeriesSpec extends FlatSpecLike with ShouldMatchers {
  import TimeSeriesSpec._

  "downSample" should "downSample" in {
    val rawValues = Array[MetricPoint#ValueType](1, 2, 6, 4, 5, 6)
    val series = timeSeries(0L, 10 minutes, 5 seconds,
      "test", rawValues)
    val sample = TimeSeries.downSample(series, 1 minute, TimeSeries.mean)
    sample.name should be("test")
    sample.points.length should be(10)
    sample.points.map(_._2).forall(_ == 4) should be(true)
  }

  "binOp" should "apply binary operator" in {
    val ts1 = (0L until(100000L, 1000L)).map((_, 1f))
    val ts3 = (0L until(100000L, 1000L)).map((_, 3f))
    val result = TimeSeries.binOp(ts1, ts3, TimeSeries.plus)
    ts1.size should be(100)
    result.size should be(100)
    result.forall(_._2 == 4) should be (true)
  }

  "binOp" should "consume all values" in {
    val ts1 = (0L until(100000L, 1000L)).map((_, 1f))
    val ts3 = (1000L until(101000L, 1000L)).map((_, 3f))
    val result = TimeSeries.binOp(ts1, ts3, TimeSeries.plus)
    result.size should be(101)
    result.head._2 should be(1)
    result.reverse.head._2 should be(3)
  }

  "downSample mean" should "handle missing points" in {
    val series = TimeSeries.downSample(
      timeSeries(0, 2 hours, 62 seconds, "test", Seq(5)), 1 minute, TimeSeries.mean)
    series.points.forall(p => p._2 == 5) should be(true)
  }

  "downSample sum" should "handle missing points" in {
    val series = TimeSeries.downSample(
      timeSeries(0, 2 hours, 62 seconds, "test", Seq(5)), 1 minute, TimeSeries.sum)
    series.points.forall(p => p._2 == 5) should be(true)
  }

  "downSample min" should "select smallest value" in {
    val rawSeries = timeSeries(0, 4 hours, 30 seconds, "test", Seq(1, 4))
    val series = TimeSeries.downSample(rawSeries, 1 minute, TimeSeries.min)
    series.points.forall(_._2 == 1) should be(true)
  }

  "downSample max" should "select largest value" in {
    val rawSeries = timeSeries(0, 4 hours, 30 seconds, "test", Seq(1, 4))
    val series = TimeSeries.downSample(rawSeries, 1 minute, TimeSeries.max)
    series.points.forall(_._2 == 4) should be(true)
  }
}
