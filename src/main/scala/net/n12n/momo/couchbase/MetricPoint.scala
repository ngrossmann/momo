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

/**
 * Json conversion.
 */
object MetricPoint extends DefaultJsonProtocol {
  implicit val toJson = jsonFormat3(MetricPoint.apply)

  /** Size of time-stamp + value if written to byte buffer. */
  val Size = 12
}

/**
 * Data point in time-series.
 * @param name time-series name.
 * @param timestamp time-stamp.
 * @param value value.
 */
case class MetricPoint(name: String, timestamp: Long,
                       value: MetricPoint#ValueType) {
  type ValueType = Float
}
