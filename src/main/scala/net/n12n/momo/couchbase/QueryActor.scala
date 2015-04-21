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

import akka.actor.{Props, ActorRef, ActorLogging, Actor}
import akka.pattern.{pipe, ask}
import akka.util.Timeout
import net.n12n.momo.couchbase.TimeSeries.Aggregator

import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration
import scala.util.matching.Regex

object QueryActor {
  def props(targetActor: ActorRef, bucketActor: ActorRef) =
    Props(classOf[QueryActor], targetActor, bucketActor)

  case class QueryRegex(pattern: Regex, from: Long, to: Long,
                        rate: FiniteDuration, aggregator: Aggregator, merge: Boolean)
  case class QueryList(series: Seq[String],from: Long, to: Long,
                       rate: FiniteDuration, aggregator: Aggregator, merge: Boolean)

  case class Result(series: Seq[TimeSeries])
}

class QueryActor(targetActor: ActorRef, bucketActor: ActorRef)
  extends Actor with ActorLogging {
  import QueryActor._
  implicit val timeout = Timeout(context.system.settings.config.getDuration(
    "momo.query-timeout", TimeUnit.MILLISECONDS), TimeUnit.MILLISECONDS)
  implicit val executionContext = context.dispatcher
  
  override def receive = {
    case QueryRegex(pattern, from, to, rate, aggregator, merge) =>
      val replyTo = sender
      (targetActor ? TargetActor.RegexSearchTargets(pattern)).
        mapTo[TargetActor.SearchResult].flatMap(
          r => self ? QueryList(r.names, from, to, rate, aggregator, merge)).pipeTo(replyTo)

    case QueryList(series, from, to, rate, aggregator, merge) =>
      val replyTo = sender
      val futures = series.map(MetricActor.Get(_, from, to)).map(bucketActor ? _).
        map(_.mapTo[TimeSeries])
      if (merge) {
        Future.reduce(futures.map(_.map(_.points)))(_ ++ _).map(TimeSeries(series.head, _)).
          map(TimeSeries.downSample(_, rate, aggregator)).map(List(_)).map(Result(_)).
          pipeTo(replyTo)
      } else {
        Future.fold(futures)(List[TimeSeries]())(
          (r, t) => TimeSeries.downSample(t, rate, aggregator) :: r).map(Result(_)).
          pipeTo(replyTo)
      }
  }
}
