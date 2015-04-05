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

import java.net.URLEncoder
import java.util.NoSuchElementException

import com.couchbase.client.deps.io.netty.buffer.{Unpooled, ByteBuf}
import com.couchbase.client.java.error.DocumentDoesNotExistException
import rx.Observable
import rx.functions.Func1

import scala.collection.JavaConverters._
import scala.collection.mutable.ArrayBuffer
import scala.concurrent.duration._
import akka.actor.{ActorRef, Props, ActorLogging, Actor}
import com.couchbase.client.java.AsyncBucket
import com.couchbase.client.java.document.BinaryDocument

import net.n12n.momo.util.RichConfig._

object BucketActor {
  type PointSeq = Seq[(Long, Long)]
  case class Save(point: MetricPoint)
  private[BucketActor] case class CreateAndSave(doc: BinaryDocument)

  /**
   * Get time-series data.
   * The reply is a [[net.n12n.momo.couchbase.TimeSeries]] object.
   * @param name time series name.
   * @param from start time.
   * @param to end time
   */
  case class Get(name: String, from: Long, to: Long)
  def props(bucket: AsyncBucket, metricActor: ActorRef) =
    Props(classOf[BucketActor], bucket, metricActor)

  private[BucketActor] def doc2seq(doc: BinaryDocument): PointSeq = {
    val v = new ArrayBuffer[(Long, Long)]((doc.content.capacity / 16).toInt)
    while (doc.content.isReadable(16)) {
      v.append((doc.content.readLong(), doc.content.readLong()))
    }
    v
  }
}

class BucketActor(bucket: AsyncBucket, metricActor: ActorRef) extends Actor
  with ActorLogging {
  import BucketActor._
  import context.system
  val documentInterval = system.settings.config.getFiniteDuration("momo.document-interval")
  val metricTtl = system.settings.config.getFiniteDuration("momo.metric-ttl").toSeconds.toInt
  val keyPrefix = system.settings.config.getString("momo.couchbase.series-key-prefix")

  override def receive = {
    case Save(point) =>
      val doc = document(point)
      bucket.append(document(point)).subscribe(
        (document: BinaryDocument) => log.debug("Updated document {}", document.id),
        (error: Throwable) => error match {
          case e: DocumentDoesNotExistException =>
            metricActor ! TargetActor.TargetUpdate(point.name)
            self ! CreateAndSave(doc)
          case _ => log.error(error, "Update failed") // TODO actor should die
        }
      )
    case CreateAndSave(doc) =>
      bucket.upsert(doc).subscribe(
        (inserted: BinaryDocument) =>
          log.debug("Created new document {} (expires {})", inserted.id, doc.expiry()),
        (error: Throwable) => log.error("Creating document {} failed", doc.id)
      )
    case Get(name, from, to) =>
      val replyTo = sender
      val replies = documentIds(name, from, to).map(
        id => BinaryDocument.create(id)).map((doc: BinaryDocument) => bucket.get(doc))
      log.debug("Waiting for {} replies", replies.length)
      val result = Observable.merge(replies.asJava).
        map[PointSeq]((doc: BinaryDocument) => doc2seq(doc)).
        reduce((s1: PointSeq, s2: PointSeq) => s1 ++ s2).
        map((s: PointSeq) => s.filter((e) => e._1 >= from && e._1 < to)).
        map[TimeSeries]((s: Any) => TimeSeries(name, s.asInstanceOf[PointSeq]))

      result.subscribe((ts: TimeSeries) => replyTo ! ts,
        (t: Throwable) => t match {
      case t: NoSuchElementException =>
        replyTo ! TimeSeries(name, Seq())
      case any =>
          log.error(any, "Get {} from {} to {} failed ", name, from, to)
        }
      )
  }

  def document(point: MetricPoint): BinaryDocument = {
    val id = toId(point.name, point.timestamp / documentInterval.toMillis)
    val content = Unpooled.copyLong(point.timestamp, point.value)
    BinaryDocument.create(id, metricTtl, content)
  }

  def documentIds(name: String, from: Long, to: Long): Seq[String] = {
    (from / documentInterval.toMillis to to / documentInterval.toMillis).
      map(toId(URLEncoder.encode(name, "utf-8"), _))
  }

  def toId(name: String, time: Long) = s"${keyPrefix}/${name}/${time}"
}
