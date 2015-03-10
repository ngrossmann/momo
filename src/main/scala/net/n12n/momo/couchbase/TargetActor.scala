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

import java.util.concurrent.TimeUnit

import scala.concurrent.duration._
import akka.actor.{Props, Actor, ActorLogging}
import com.couchbase.client.java.AsyncBucket
import com.couchbase.client.java.document.JsonStringDocument
import rx.Observable
import spray.httpx.marshalling._

import scala.util.matching.Regex

object TargetActor {
  def props(bucket: AsyncBucket) = Props(classOf[TargetActor], bucket)
  /**
   * Inform this actor that the given metric received an update.
   * There is no reply.
   * @param name metric name.
   */
  case class TargetUpdate(name: String)
  /** Find metrics matching pattern. */
  case class SearchTargets(pattern: String)

  /** Find targets matching a regex. */
  case class RegexSearchTargets(pattern: Regex)

  /**
   * Search reply.
   */
  case class SearchResult(names: Seq[String])

  case object LoadTarget
  case object SaveTargets
}

/**
 * Manage metric names.
 */
class TargetActor(bucket: AsyncBucket) extends Actor with ActorLogging {
  import TargetActor._
  import context.dispatcher
  private val documentId = "momo://"
  var metrics = new scala.collection.mutable.TreeSet[String]()
  val saveInterval = context.system.settings.config.getDuration(
    "momo.target-actor.save-interval", TimeUnit.SECONDS)
  context.system.scheduler.schedule(
    saveInterval seconds, saveInterval seconds, self, SaveTargets)

  override def preStart(): Unit = {
    load().subscribe { (doc: TargetDocument ) =>
      self ! doc
    }
  }

  def receive = {
    case TargetUpdate(name) =>
      metrics.add(name)
    case SearchTargets(pattern) =>
      sender ! TargetActor.SearchResult(metrics.filter(_.contains(pattern)).toSeq)
    case TargetDocument(targets) => targets.foreach(metrics += _.name)
    case LoadTarget => load().subscribe { (doc: TargetDocument ) =>
      self ! doc
    }
    case RegexSearchTargets(pattern) =>
      sender ! SearchResult(metrics.filter(pattern.findFirstIn(_).isDefined).toSeq)

    case SaveTargets => save(TargetDocument(metrics.map((t) => Target(t)).toSeq))
  }

  private def save(targets: TargetDocument): Unit = {
    import TargetDocument._
    import spray.json._
    val document = JsonStringDocument.create(documentId, targets.toJson.compactPrint)
    bucket.upsert(document)
  }

  private def load(): Observable[TargetDocument] = {
    import TargetDocument._
    import spray.json._
    bucket.get(documentId, classOf[JsonStringDocument]).map(
      (doc: JsonStringDocument) => {
      doc.content().parseJson.convertTo[TargetDocument]
    })
  }
}

