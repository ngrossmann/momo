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

import scala.concurrent.duration._

import akka.actor.ActorSystem
import akka.testkit.{ImplicitSender, TestActorRef, TestKit}
import com.couchbase.client.java.CouchbaseCluster
import org.scalatest.FlatSpecLike

object BucketActorSpec {
  val config = ConfigFactory.parseString(
    """
      |akka.loggers = ["akka.event.slf4j.Slf4jLogger"]
      |akka.loglevel = DEBUG
    """.stripMargin)
}
class BucketActorSpec extends TestKit(ActorSystem("BucketActorSpec",
  config = BucketActorSpec.config)) with FlatSpecLike with ImplicitSender {

  "BucketActor.Save" should "insert metric point" in {
    val cluster = CouchbaseCluster.create()
    val actor = TestActorRef(new BucketActor(cluster.openBucket("default").async(),
      this.testActor))
    val now = System.currentTimeMillis()
    actor ! BucketActor.Save(MetricPoint("metric", now, 1L))
    actor ! BucketActor.Get("metric", now - 10000, now + 10000)
    expectMsgPF(5 second) {
      case ts: TimeSeries if ts.points.length > 0 =>
        println(ts.points)
    }
  }
}
