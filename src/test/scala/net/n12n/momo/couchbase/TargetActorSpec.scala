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

import scala.concurrent.duration._
import akka.actor.ActorSystem
import akka.testkit.{EventFilter, ImplicitSender, TestActorRef, TestKit}
import com.couchbase.client.java.document.json.JsonObject
import com.couchbase.client.java.document.{JsonDocument, Document, JsonStringDocument}
import com.couchbase.client.java.view.{AsyncViewRow, AsyncViewResult, ViewQuery}
import com.typesafe.config.{ConfigFactory, Config}
import net.n12n.momo.couchbase.mock.{AsyncBucketMock, BucketData}
import org.scalatest.{ShouldMatchers, BeforeAndAfterAll, FlatSpecLike}
import rx.Observable

object TargetActorSpec {
  val config = ConfigFactory.parseString(
    """
      |akka.loggers = ["akka.testkit.TestEventListener"]
    """.stripMargin)
}

class TargetActorSpec extends TestKit(ActorSystem("TargetActorSpec",
  config = TargetActorSpec.config)) with ImplicitSender with
  FlatSpecLike with ShouldMatchers with BeforeAndAfterAll {


  "TestActor" should "read existing documents at start-up" in {
    val bucket = new AsyncBucketMock() {
      override def query(viewQuery: ViewQuery): Observable[AsyncViewResult] =
        Observable.just(
          new AsyncViewResult {
            override def rows(): Observable[AsyncViewRow] = Observable.just(
              new AsyncViewRow {

                override def key(): AnyRef = "server.metric1"

                override def document(): Observable[JsonDocument] = null

                override def document[D <: Document[_]](aClass: Class[D]): Observable[D] = null

                override def value(): AnyRef = null

                override def id(): String = null
              }
            )

            override def success(): Boolean = true

            override def error(): Observable[JsonObject] = Observable.just(null)

            override def totalRows(): Int = 1

            override def debug(): JsonObject = null
          }
        )
    }
    EventFilter.debug(message = "Received list of 1 targets", occurrences = 1).
      intercept {
      val actor = TestActorRef(TargetActor.props)
      actor ! BucketActor.BucketOpened(bucket)
    }
  }

  override def afterAll(): Unit = {
    shutdown()
  }
}
