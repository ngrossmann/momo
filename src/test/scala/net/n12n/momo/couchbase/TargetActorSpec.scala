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

import akka.actor.ActorSystem
import akka.testkit.{TestActorRef, TestKit}
import com.couchbase.client.java.document.{Document, JsonStringDocument}
import com.typesafe.config.{ConfigFactory, Config}
import net.n12n.momo.couchbase.mock.AsyncBucketMock
import org.scalatest.{ShouldMatchers, BeforeAndAfterAll, FlatSpecLike}
import rx.Observable

object TargetActorSpec {
  val config = ConfigFactory.parseString(
    """
      |momo {
      |  target-actor.save-interval = 30 s
      |}
    """.stripMargin)
}

class TargetActorSpec extends TestKit(ActorSystem("TargetActorSpec",
  config = TargetActorSpec.config)) with
  FlatSpecLike with ShouldMatchers with BeforeAndAfterAll {


  "TestActor" should "read existing documents at start-up" in {
    val bucket = new AsyncBucketMock() {
      override def get[T <: Document[_]](id: String, klass: Class[T]): Observable[T] = {
        Observable.just(
        JsonStringDocument.create(id,
          """
            |{"targets": [ {"name": "server.metric.1"}, {"name": "server.metric2"},
            |{"name": "server.metric3"}]}
          """.stripMargin).asInstanceOf[T])
      }
    }
    val actor = TestActorRef(TargetActor.props(bucket))
    val metrics = actor.underlyingActor.asInstanceOf[TargetActor].metrics
    metrics.size should be(3)
    metrics.contains("server.metric.1") should be(true)

  }

  override def afterAll(): Unit = {
    shutdown()
  }
}
