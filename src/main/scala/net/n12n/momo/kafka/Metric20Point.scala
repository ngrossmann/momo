/*
 * momo
 *
 * Copyright (c) 2015 Amadeus Data Processing.
 */

package net.n12n.momo.kafka

import spray.json.DefaultJsonProtocol

case class Metric20Point(timestamp: Long, value: Float, tags: Map[String, String])

object Metric20Point extends DefaultJsonProtocol {
  implicit def toJson = jsonFormat3(Metric20Point.apply)
}
