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
package net.n12n.momo

import org.scalatest.matchers.ShouldMatchers

import scala.concurrent.{Future, Await}
import scala.concurrent.duration._

import akka.actor.{Props, Actor, ActorSystem}
import akka.testkit.TestKit
import net.n12n.momo.couchbase.{TimeSeries, QueryActor}
import org.scalatest.{BeforeAndAfterAll, Matchers, FlatSpecLike}

object QueryExecutorSpec {
  class QueryActorMock(values: Map[String, TimeSeries.ValueType]) extends Actor {
    override def receive = {
      case QueryActor.QueryList(series, from, to, rate, normalizer, merge) =>
        val ts = series.map { name =>
          val value = values(name)
          TimeSeries.static(value, from, to, rate, Some(name))
        }
        sender ! QueryActor.Result(ts)
      case QueryActor.QueryRegex(pattern, from, to, rate, normalizer, true) =>
        sender ! QueryActor.Result(List(
          TimeSeries.static(175, from, to, rate, Some("name"))))
      case QueryActor.QueryRegex(pattern, from, to, rate, normalizer, false) =>
        sender ! QueryActor.Result(List(
          TimeSeries.static(100, from, to, rate, Some("disk.total")),
          TimeSeries.static(75, from, to, rate, Some("disk.used"))))
    }
  }

  def props(values: Map[String, TimeSeries.ValueType]) =
    Props(new QueryActorMock(values))
}

class QueryExecutorSpec extends TestKit(ActorSystem("QueryExecutorSpec")) with
  FlatSpecLike with Matchers with BeforeAndAfterAll {
  import QueryExecutor._
  import QueryExecutorSpec._
  import QueryParser._

  val queryActor = system.actorOf(props(
    Map("cpu.cpu-total" -> 3, "disk.total" -> 100, "disk.used" -> 75)),
    "QueryActorMock")
  val executor = new QueryExecutor(queryActor, system.dispatcher)
  val parser = new QueryParser
  val start = System.currentTimeMillis
  val queryContext = QueryContext(start - 1000 * 60 * 60 * 6, start, 1 minute)

  "cpu.cpu-total:mean" should "return a single time-series" in {
    val future = executor.execute(
      parser.parseQuery("cpu.cpu-total:mean").get,
      queryContext)
    val result = Await.result(future, 1 second)
    result.size should be(1)
    result.head.points.size should be(60 * 6)
  }

  "plus" should "add two time series" in {
    val q = "cpu.cpu-total:mean,cpu.cpu-total:mean,:plus"
    val future = executor.execute(parser.parseQuery(q).get, queryContext)
    val result = Await.result(future, 1 second)
    result.size should be(1)
    result.head.points.size should be(60 * 6)
    result.head.points.forall(_._2 == 6) should be(true)
  }

  "calculate % disk free" should "return all values 25" in {
    val q = "disk.total:mean,disk.used:mean,:minus,100,:mul,disk.total:mean,:div"
    val future = executor.execute(parser.parseQuery(q).get, queryContext)
    val result = Await.result(future, 1 second)
    result.size should be(1)
    result.head.points.size should be(60 * 6)
    result.head.points.forall(_._2 == 25) should be(true)
  }

  "/disk.*/" should "return two series" in {
    val q = "/disk.*/:mean"
    val future = executor.execute(parser.parseQuery(q).get, queryContext)
    val result = Await.result(future, 1 second)
    result.size should be(2)
  }

  "/disk.*/m" should "return one series" in {
    val q = "/disk.*/m:mean"
    val future = executor.execute(parser.parseQuery(q).get, queryContext)
    val result = Await.result(future, 1 second)
    result.size should be(1)
  }

  override def afterAll(): Unit = {
    shutdown()
  }
}
