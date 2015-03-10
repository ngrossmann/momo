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

class TimeSeriesSpec extends FlatSpecLike with ShouldMatchers {

  "downSample" should "downSample" in {
    val rawValues = Array[Long](1, 2, 6, 4, 5, 6)
    val points = for (i <- 0 until 120) yield {
      (i * 5 * 1000L, rawValues(i % rawValues.length))
    }
    val sample = TimeSeries.downSample(TimeSeries("test", points), 1 minute,
      TimeSeries.mean)
    sample.name should be("test")
    sample.points.length should be(10)
    sample.points.map(_._2).forall(_ == 4) should be(true)
  }
}
