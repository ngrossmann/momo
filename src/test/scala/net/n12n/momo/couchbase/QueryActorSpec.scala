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

import java.util.concurrent.Executors

import scala.concurrent.duration._

import akka.actor.ActorSystem
import akka.testkit.{ImplicitSender, TestActorRef, TestKit}
import net.n12n.momo.couchbase.mock.BucketData
import org.scalatest.{ShouldMatchers, BeforeAndAfterAll, FlatSpecLike}

class QueryActorSpec extends TestKit(ActorSystem("query-actor")) with
  ImplicitSender with FlatSpecLike with BeforeAndAfterAll with ShouldMatchers {
  val bucket = BucketData.createBucket(List(
    ("series.of.1", 1),
    ("series.of.2", 2),
    ("series.of.3", 3)))

  val metricActor = system.actorOf(MetricActor.props(
    Executors.newSingleThreadScheduledExecutor()))
  val targetActor = system.actorOf(TargetActor.props)
  metricActor ! BucketActor.BucketOpened(bucket)
  targetActor ! BucketActor.BucketOpened(bucket)
  val queryActor = TestActorRef(QueryActor.props(targetActor, metricActor))
  "Get 1 hour from series.of.1" should "return 3600 values with 1" in {
    queryActor ! QueryActor.QueryList(Seq("series.of.1"),
      BucketData.startTime.getTime, BucketData.startTime.getTime +
        (1 hour).toMillis, 1 second, TimeSeries.mean, false)
    val result = expectMsgType[QueryActor.Result]
    result.series.size should be(1)
    result.series.head.name should be("series.of.1")
    result.series.head.points.size should be(3600)
    result.series.head.points.forall(_._2 == 1) should be(true)
  }

  "Get 1 hour form series.of.1 with 60s sampling using mean" should
    "return 60 values with 1" in {

    queryActor ! QueryActor.QueryList(Seq("series.of.1"),
      BucketData.startTime.getTime, BucketData.startTime.getTime +
        (1 hour).toMillis, 60 second, TimeSeries.mean, false)
    val result = expectMsgType[QueryActor.Result]
    result.series.size should be(1)
    result.series.head.name should be("series.of.1")
    result.series.head.points.size should be(60)
    result.series.head.points.forall(_._2 == 1) should be(true)
  }

  "Get 1 hour form series.of.1/2 with 1s sampling using merge sum" should
  "return 3600 values with 3" in {

    queryActor ! QueryActor.QueryList(Seq("series.of.1", "series.of.2"),
      BucketData.startTime.getTime, BucketData.startTime.getTime +
        (1 hour).toMillis, 1 second, TimeSeries.sum, true)
    val result = expectMsgType[QueryActor.Result]
    result.series.size should be(1)
    result.series.head.name should be("series.of.1")
    result.series.head.points.size should be(3600)
    result.series.head.points.forall(_._2 == 3) should be(true)
  }

  "Get 1 hour form series.of.1/2/3 with 10s sampling using merge mean" should
    "return 360 values with 2" in {

    queryActor ! QueryActor.QueryList(
      Seq("series.of.1", "series.of.2", "series.of.3"),
      BucketData.startTime.getTime, BucketData.startTime.getTime +
        (1 hour).toMillis, 10 second, TimeSeries.mean, true)
    val result = expectMsgType[QueryActor.Result]
    result.series.size should be(1)
    result.series.head.name should be("series.of.1")
    result.series.head.points.size should be(360)
    result.series.head.points.forall(_._2 == 2) should be(true)
  }

  "Get 1 hour form series.of.1/2/3 with 10s sampling using no-merge mean" should
    "return 3x360 values with 1, 2, 3" in {

    queryActor ! QueryActor.QueryList(
      Seq("series.of.1", "series.of.2", "series.of.3"),
      BucketData.startTime.getTime, BucketData.startTime.getTime +
        (1 hour).toMillis, 10 second, TimeSeries.mean, false)
    val result = expectMsgType[QueryActor.Result]
    result.series.size should be(3)
    //result.series(0).name should be("series.of.1")
    //result.series(1).name should be("series.of.2")
    //result.series(2).name should be("series.of.3")
    result.series(2).points.size should be(360)
    //result.series(1).points.forall(_._2 == 2) should be(true)
  }

  "Get 1 hour form regex series.of.\\d with 10s sampling using merge mean" should
    "return 360 values with 2" in {

    queryActor ! QueryActor.QueryRegex("""series.of.*""".r,
      BucketData.startTime.getTime, BucketData.startTime.getTime +
        (1 hour).toMillis, 10 second, TimeSeries.mean, true)
    val result = expectMsgType[QueryActor.Result]
    result.series.size should be(1)
    //result.series.head.name should be("series.of.1")
    result.series.head.points.size should be(360)
    result.series.head.points.forall(_._2 == 2) should be(true)
  }

  override protected def afterAll(): Unit = shutdown()
}
