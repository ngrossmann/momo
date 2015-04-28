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

import com.couchbase.client.java.view.{Stale, AsyncViewRow, AsyncViewResult, ViewQuery}
import rx.schedulers.Schedulers

import scala.concurrent.duration._
import akka.actor.{ActorRef, Props, Actor, ActorLogging}
import com.couchbase.client.java.AsyncBucket
import com.couchbase.client.java.document.JsonStringDocument
import rx.Observable
import spray.httpx.marshalling._

import scala.util.matching.Regex

object TargetActor {
  def props = Props[TargetActor]
  /** Find metrics matching pattern. */
  case class SearchTargets(pattern: String)

  /** Find targets matching a regex. */
  case class RegexSearchTargets(pattern: Regex)

  /**
   * Search reply.
   */
  case class SearchResult(names: Seq[String])
}

/**
 * Manage metric names.
 */
class TargetActor extends Actor with BucketActor with ActorLogging {
  import TargetActor._
  import context.system
  val designDoc = system.settings.config.getString("momo.couchbase.target.design-document")
  val nameView = system.settings.config.getString("momo.couchbase.target.name-view")
  val stale = Stale.UPDATE_AFTER

  override def doWithBucket(bucket: AsyncBucket) = {
    case SearchTargets(pattern) =>
      val filter = (key: Object) =>
        new java.lang.Boolean(key.isInstanceOf[String] && key.asInstanceOf[String].contains(pattern))
      searchTargets(bucket, filter, sender())

    case RegexSearchTargets(pattern) =>
      val filter = (key: Object) =>
        new java.lang.Boolean(key.isInstanceOf[String] &&
          pattern.findFirstIn(key.asInstanceOf[String]).isDefined)
      searchTargets(bucket, filter, sender())
  }

  private def searchTargets(bucket: AsyncBucket, filter: (Object) => java.lang.Boolean, replyTo: ActorRef): Unit = {
    val list: Observable[Object] =
      bucket.query(ViewQuery.from(designDoc, nameView).group().
        stale(stale)).flatMap(view2rows).map((row: AsyncViewRow) => row.key())
    list.filter(filter).reduce(List[String](), (list: List[String], key: Object) =>
      key.asInstanceOf[String] :: list).subscribeOn(Schedulers.io()).subscribe((list: List[String]) =>
      replyTo ! SearchResult(list))
  }
}
