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
import net.n12n.momo.couchbase.BucketActor.BucketOpened
import rx.schedulers.Schedulers

import scala.concurrent.duration._
import akka.actor.{ActorRef, Props, Actor, ActorLogging}
import com.couchbase.client.java.AsyncBucket
import com.couchbase.client.java.document.JsonStringDocument
import rx.Observable
import spray.httpx.marshalling._

import scala.util.matching.Regex
import net.n12n.momo.util.RichConfig._

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

  /** Internal message to fetch all targets. */
  protected[TargetActor] case object FetchTargets
  protected[TargetActor] case class TargetUpdate(targets: List[String])
}

/**
 * Manage metric names.
 */
class TargetActor extends Actor with BucketActor with ActorLogging {
  import TargetActor._
  import context.system
  implicit val ec = system.dispatcher
  val designDoc = system.settings.config.getString(
    "momo.couchbase.target.design-document")
  val nameView = system.settings.config.getString(
    "momo.couchbase.target.name-view")
  val targetUpdateInterval = system.settings.config.getFiniteDuration(
    "momo.target-update-interval")
  val stale = Stale.UPDATE_AFTER
  var targets: List[String] = Nil

  override def receive: Receive = {
    case BucketOpened(bucket: AsyncBucket) =>
      log.info("Got bucket {}.", bucket.name)
      context.become(doWithBucket(bucket))
      self ! FetchTargets
      context.system.scheduler.schedule(targetUpdateInterval,
        targetUpdateInterval, self, FetchTargets)
  }

  override def doWithBucket(bucket: AsyncBucket) = {
    case SearchTargets(pattern) =>
      log.debug("Searching for {} in {}", pattern, targets.mkString(","))
      sender ! SearchResult(targets.filter(_.contains(pattern)))

    case RegexSearchTargets(pattern) =>
      sender() ! SearchResult(targets.filter(pattern.findFirstIn(_).isDefined))

    case FetchTargets => searchTargets(bucket)

    case TargetUpdate(list) =>
      targets = list
      log.debug("Received list of {} targets", list.size)
  }

  protected def searchTargets(bucket: AsyncBucket): Unit = {
    val list: Observable[Object] =
      bucket.query(ViewQuery.from(designDoc, nameView).group().
        stale(stale)).flatMap(view2rows).map((row: AsyncViewRow) => row.key())
    list.reduce(List[String](), (list: List[String], key: Object) =>
      key.asInstanceOf[String] :: list).subscribeOn(Schedulers.io()).subscribe(
        (list: List[String]) => self ! TargetUpdate(list))
  }
}
