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
package net.n12n.momo.couchbase.mock

import java.text.SimpleDateFormat
import java.util.Date

import scala.collection.JavaConverters._
import com.couchbase.client.deps.io.netty.buffer.Unpooled
import com.couchbase.client.java.document.BinaryDocument
import com.typesafe.config.ConfigFactory
import net.n12n.momo.couchbase.MetricPoint
import net.n12n.momo.util.RichConfig._

import scala.concurrent.duration.Duration

/**
 * Helper functions to create mock buckets with data.
 */
object BucketData {
  private val fmt = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS Z")
  private val config = ConfigFactory.load()
  val startTime = fmt.parse("2015-09-01 00:00:00.000 +0000")
  val endTime = fmt.parse("2015-09-02 00:00:00.000 +0000")
  val documentInterval = config.getFiniteDuration("momo.document-interval")
  val keyPrefix = config.getString("momo.couchbase.series-key-prefix")
  def metricPoints(target: String, value: MetricPoint#ValueType, from: Date = startTime,
                   to: Date = endTime,
                   interval: Duration = documentInterval):
  List[(String, Seq[MetricPoint])] = {
    val points = for (t <- from.getTime until to.getTime by 1000) yield
      MetricPoint(target, t, value)
    points.groupBy(_.timestamp / interval.toMillis).map(
      t => (s"${keyPrefix}/${target}/${t._1}", t._2)).toList
  }

  def toDocument(id: String, points: Seq[MetricPoint]): BinaryDocument = {
    val content = Unpooled.buffer(MetricPoint.Size * points.size)
    points.foreach { p =>
      content.writeLong(p.timestamp)
      content.writeFloat(p.value)
    }
    content.markReaderIndex()
    BinaryDocument.create(id, content)
  }

  def createBucket(templates: Seq[(String, MetricPoint#ValueType)]):
  AsyncBucketMock = {
    val data = Map(templates.flatMap(t => metricPoints(t._1, t._2)).map(
      t => (t._1, toDocument(t._1, t._2))):_*)
    new AsyncBucketMock(data.asJava, templates.map(_._1).toArray)
  }
}
