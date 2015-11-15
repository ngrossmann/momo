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
import akka.testkit.{ImplicitSender, TestActorRef, TestKit}
import com.couchbase.client.java.AsyncBucket
import com.couchbase.client.java.document.BinaryDocument
import com.couchbase.client.java.document.json.JsonArray
import com.couchbase.client.java.view.AsyncViewRow
import net.n12n.momo.couchbase.mock.AsyncBucketMock
import net.n12n.momo.couchbase.mock.AsyncBucketMock.AsyncViewRowMock
import org.scalatest.{BeforeAndAfterAll, ShouldMatchers, FlatSpecLike}

class DashboardActorSpec extends TestKit(ActorSystem("DashboardActorSpec"))
with ImplicitSender with FlatSpecLike with ShouldMatchers
with BeforeAndAfterAll {
  import DashboardActor._
  val rows: Array[AsyncViewRow] = Array(new AsyncBucketMock.AsyncViewRowMock(
    "Momo",
    JsonArray.from("dashboards/f78834f4-1cd7-4f43-a54c-ca44eaae8677")),
    new AsyncBucketMock.AsyncViewRowMock(
      "System",
      JsonArray.from("dashboards/c9a97dd2-898b-4407-95c2-0a35d01e10fc")))
  val bucket: AsyncBucket = new AsyncBucketMock(
    new java.util.HashMap[String, BinaryDocument](), rows)

  val actor = TestActorRef[DashboardActor]
  actor ! BucketActor.BucketOpened(bucket)

  "No parameters" should "return all dashboards" in {
    actor ! SearchDashboards(None, None, None)
    val dashboards = expectMsgClass(classOf[DashboardMetadataIndex])
    dashboards.dashboards.size should be(2)
  }

  "Empty query" should "return all dashboards" in {
    actor ! SearchDashboards(Some(""), None, None)
    val dashboards = expectMsgClass(classOf[DashboardMetadataIndex])
    dashboards.dashboards.size should be(2)
  }

  "title Momo" should "return 1 dashboard" in {
    actor ! SearchDashboards(Some("Momo"), None, None)
    val dashboards = expectMsgClass(classOf[DashboardMetadataIndex])
    dashboards.dashboards.size should be(1)
  }

  override def afterAll(): Unit = {
    shutdown()
  }
}
