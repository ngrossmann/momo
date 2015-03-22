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

import akka.actor.{Actor, ActorLogging}
import com.couchbase.client.java.{CouchbaseCluster, AsyncBucket, CouchbaseAsyncCluster}
import rx.functions.Action1


object CouchbaseActor {
  case class SetBucket(bucket: AsyncBucket)
}

class CouchbaseActor extends Actor with ActorLogging {
  import net.n12n.momo.couchbase.CouchbaseActor._

  private val cluster = CouchbaseCluster.create()
    //context.system.settings.config.getStringList("couchbase.cluster"))
  private val bucketName = context.system.settings.config.getString("couchbase.bucket")
  private val bucket = cluster.openBucket(bucketName).async()
  private val metricActor = context.actorOf(TargetActor.props(bucket), "metric")
  private val dashboardActor = context.actorOf(DashboardActor.props(bucket), "dashboard")
  private val bucketActor = context.actorOf(
    BucketActor.props(bucket, metricActor), "bucket")
  private val queryActor = context.actorOf(
    QueryActor.props(metricActor, bucketActor), "query")
  log.info("Created actor {}", bucketActor.path)

  override def preRestart(reason: Throwable, message: Option[Any]): Unit = {
    log.error(reason, "CouchbaseActor restarting: %s", reason.getMessage)
    cluster.disconnect()
  }

  override def postStop(): Unit = {
    cluster.disconnect()
  }

  override def receive = {
    case any => bucketActor.forward(any)
  }
}
