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

import com.typesafe.config.ConfigFactory

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._

import akka.actor.ActorSystem
import akka.testkit.{ImplicitSender, TestActorRef, TestKit}
import com.couchbase.client.java.CouchbaseCluster
import org.scalatest.{BeforeAndAfterAll, FlatSpecLike}

object MetricActorSpec {
  val config = ConfigFactory.parseString(
    """
      |akka.loggers = ["akka.event.slf4j.Slf4jLogger"]
      |akka.loglevel = DEBUG
    """.stripMargin)
}
class MetricActorSpec extends TestKit(ActorSystem("BucketActorSpec",
  config = MetricActorSpec.config)) with FlatSpecLike with ImplicitSender
  with BeforeAndAfterAll {
  val couchbase = CouchbaseCluster.create("127.0.0.1:8092")
  val bucket = couchbase.openBucket("default").async()

  "MetricActor.Save" should "insert metric point" in {
    val actor = TestActorRef(new MetricActor(ExecutionContext.global))
    actor ! BucketActor.BucketOpened(bucket)
    val now = System.currentTimeMillis()
    actor ! MetricActor.Save(MetricPoint("metric", now, 1L))
    actor ! MetricActor.Get("metric", now - 10000, now + 10000)
    expectMsgPF(5 second) {
      case ts: TimeSeries if ts.points.length > 0 =>
        println(ts.points)
    }
  }

  override protected def afterAll(): Unit = {
    couchbase.disconnect()
    shutdown()
  }
}
