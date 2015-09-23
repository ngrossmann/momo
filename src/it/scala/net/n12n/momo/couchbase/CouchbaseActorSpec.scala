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

import akka.actor.{ActorSystem, Props}
import akka.testkit.{EventFilter, TestActorRef, TestKit}
import com.typesafe.config.ConfigFactory
import org.scalatest.{BeforeAndAfterAll, FlatSpecLike}

object CouchbaseActorSpec {
  val config = ConfigFactory.parseString(
    """
      |akka.loggers = ["akka.testkit.TestEventListener"]
    """.stripMargin)
}
class CouchbaseActorSpec extends TestKit(ActorSystem("CouchbaseActorSpec",
  config = CouchbaseActorSpec.config)) with FlatSpecLike
  with BeforeAndAfterAll {

  "CouchbaseActor" should "open bucket" in {
    EventFilter.info(message = "Opened bucket default", occurrences = 1).
      intercept {
      val actor = TestActorRef(Props[CouchbaseActor], "db")
    }
  }

  override protected def afterAll(): Unit = {
    shutdown()
  }
}
