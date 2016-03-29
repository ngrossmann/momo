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
import java.util.concurrent.{Executor, Executors}

import com.couchbase.client.deps.io.netty.buffer.{Unpooled, ByteBuf}
import com.couchbase.client.java.error.DocumentDoesNotExistException
import rx.Observable
import rx.functions.Func1
import rx.schedulers.Schedulers

import scala.collection.JavaConverters._
import scala.collection.mutable.ArrayBuffer
import scala.concurrent.duration._
import akka.actor._
import com.couchbase.client.java.AsyncBucket
import com.couchbase.client.java.document.BinaryDocument

import net.n12n.momo.util.RichConfig._

import scala.util.{Success, Failure, Try}

object MetricActor {
  type PointSeq = Seq[(Long, MetricPoint#ValueType)]

  sealed trait MetricActorQuery
  /**
   * Save data-point.
   * This message is not replied to.
   * @param point data point.
   * @param received time when data point was received, default is now.
   */
  case class Save(point: MetricPoint, received: Long = System.currentTimeMillis())
    extends MetricActorQuery
  private[MetricActor] case class CreateAndSave(doc: BinaryDocument, received: Long)

  /**
   * Get time-series data.
   * The reply is a [[net.n12n.momo.couchbase.TimeSeries]] object.
   * @param name time series name.
   * @param from start time.
   * @param to end time
   */
  case class Get(name: String, from: Long, to: Long,
                 received: Long = System.currentTimeMillis()) extends MetricActorQuery

  def props(executor: Executor) = Props(classOf[MetricActor], executor)

  private[MetricActor] def doc2seq(doc: BinaryDocument): PointSeq = {
    val v = new ArrayBuffer[(Long, MetricPoint#ValueType)](
      (doc.content.capacity / MetricPoint.Size).toInt)
    while (doc.content.isReadable(MetricPoint.Size)) {
      v.append((doc.content.readLong(), doc.content.readFloat()))
    }
    doc.content().release()
    v
  }
}

/**
 * Manage time-series with by name and time-range.
 *
 * @param executor
 */
class MetricActor(executor: Executor) extends Actor with BucketActor with ActorLogging {
  import MetricActor._
  import context.system
  val documentInterval = system.settings.config.getFiniteDuration("momo.document-interval")
  val metricTtl = system.settings.config.getFiniteDuration("momo.metric-ttl").toSeconds.toInt
  val keyPrefix = system.settings.config.getString("momo.couchbase.series-key-prefix")
  val maxDocuments = system.settings.config.getLong("momo.couchbase.max-documents-per-query")
  val simpleActorName = self.path.elements.last.replace('.', '_')
  val sampleActor = context.actorOf(MetricSampleActor.props(), "monitor")

  override def doWithBucket(bucket: AsyncBucket) = {
    case Save(point, received) =>
      val doc = document(point)
      bucket.append(document(point)).subscribeOn(Schedulers.from(executor)).subscribe(
        (document: BinaryDocument) => {
          sampleActor ! MetricSampleActor.Action("save",
            System.currentTimeMillis() - received)
          //log.debug("Appended Save({}) to {}", point, doc.id())
        },
        (error: Throwable) => error match {
          case e: DocumentDoesNotExistException =>
            self ! CreateAndSave(doc, received)
          case _ => log.error(error, "Update failed") // TODO actor should die
        }
      )
    case CreateAndSave(doc, received) =>
      bucket.upsert(doc).subscribeOn(Schedulers.from(executor)).subscribe(
        (inserted: BinaryDocument) => {
          log.debug("Created new document {} (expires {})", inserted.id, doc.expiry())
          sampleActor ! MetricSampleActor.Action("create",
            System.currentTimeMillis() - received)
        },
        (error: Throwable) => log.error("Creating document {} failed", doc.id)
      )
    case Get(name, from, to, received) =>
      val replyTo = sender
      log.debug("Received get {} from {}", name, replyTo.path)
      documentIds(name, from, to) match {
        case Success(ids) =>
          val replies = ids.map(id => BinaryDocument.create(id)).map(
            (doc: BinaryDocument) => bucket.get(doc))
          val result = Observable.merge(replies.asJava).
            map[PointSeq]((doc: BinaryDocument) => doc2seq(doc)).
            reduce((s1: PointSeq, s2: PointSeq) => s1 ++ s2).
            map((s: PointSeq) => s.filter((e) => e._1 >= from && e._1 < to)).
            map[TimeSeries]((s: Any) => TimeSeries(name, s.asInstanceOf[PointSeq]))
            result.subscribeOn(Schedulers.from(executor)).subscribe({
              (ts: TimeSeries) =>
                replyTo ! ts
                sampleActor ! MetricSampleActor.Action("get",
                  System.currentTimeMillis() - received)
                log.debug("Found {} with {} entries.", ts.name, ts.points.size)
            },
            (t: Throwable) => t match {
              case t: NoSuchElementException =>
                replyTo ! TimeSeries(name, Seq())
                log.debug("Target {} not found: {}", name, t.getMessage)
              case t: Throwable =>
                log.error(t, "Get {} from {} to {} failed ", name, from, to)
                replyTo ! Status.Failure(t)
            })
        case Failure(t) => sender ! Status.Failure(t)
      }
  }

  override def postStop(): Unit = {
    log.info("{} stopped.", self.path)
  }

  override def preRestart(error: Throwable, message: Option[Any]): Unit = {
    log.info("{} restarting due to {}", self.path, error.getMessage)
  }

  def document(point: MetricPoint): BinaryDocument = {
    val id = toId(point.name, point.timestamp / documentInterval.toMillis)
    val content = Unpooled.copiedBuffer(Unpooled.copyLong(point.timestamp),
      Unpooled.copyFloat(point.value))
    BinaryDocument.create(id, metricTtl, content)
  }

  def documentIds(name: String, from: Long, to: Long): Try[Seq[String]] = {
    val docCount = (to - from) / documentInterval.toMillis
    if (docCount < 0) {
      Failure(new IllegalArgumentException(
        s"Time range ${from} to ${to} is negative"))
    } else if (docCount > maxDocuments) {
      Failure(new IllegalArgumentException(
        s"""Time range ${from} to ${to} would query more than ${maxDocuments}.
           |Increase momo.couchbase.max-documents-per-query to allow this
           |query.""".stripMargin))
    } else {
      val ids = (from / documentInterval.toMillis to to / documentInterval.toMillis).
        map(toId(URLEncoder.encode(name, "utf-8"), _))
      log.debug("ids: {}", ids.mkString(", "))
      Success(ids)
    }
  }

  def toId(name: String, time: Long) = s"${keyPrefix}/${name}/${time}"
}
