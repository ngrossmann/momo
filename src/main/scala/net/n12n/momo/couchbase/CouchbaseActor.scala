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

import java.net.InetAddress
import java.util.concurrent.{TimeUnit, ThreadPoolExecutor}

import net.n12n.momo.couchbase.MetricActor.Save

import scala.concurrent.duration._

import akka.actor.{Actor, ActorLogging}
import akka.routing.{Broadcast, FromConfig}
import com.couchbase.client.java.{CouchbaseCluster, AsyncBucket, CouchbaseAsyncCluster}
import rx.functions.Action1

import net.n12n.momo.util.RichConfig.RichConfig

object CouchbaseActor {
  case object OpenBucket
  case object CollectMetrics
}

class CouchbaseActor extends Actor with ActorLogging with ActorMonitoring {
  import net.n12n.momo.couchbase.CouchbaseActor._
  import context.system
  import context.dispatcher

  private val retryDelay = system.settings.config.getFiniteDuration(
    "momo.couchbase.bucket-open-retry-delay")
  private val cluster = CouchbaseCluster.create(
    system.settings.config.getStringList("couchbase.cluster"))
  private val bucketName = system.settings.config.getString("couchbase.bucket")

  private val targetActor = context.actorOf(TargetActor.props, "target")
  private val dashboardActor = context.actorOf(DashboardActor.props, "dashboard")

  private val executor = new PooledScheduler(seriesKeyPrefix,
    system.settings.config.getInt("momo.couchbase.scheduler-threads.core-pool-size"),
    system.settings.config.getInt("momo.couchbase.scheduler-threads.max-pool-size"),
    system.settings.config.getInt("momo.couchbase.scheduler-threads.queue-size"))
  private val metricActor = context.actorOf(FromConfig.props(
    MetricActor.props(executor.threadPool)), "metric")
  private val queryActor = context.actorOf(
    QueryActor.props(targetActor, metricActor), "query")

  log.info("Created actor {}", metricActor.path)
  self ! OpenBucket

  override def preRestart(reason: Throwable, message: Option[Any]): Unit = {
    log.error(reason, "CouchbaseActor restarting: %s", reason.getMessage)
    cluster.disconnect()
  }

  override def postStop(): Unit = {
    cluster.disconnect()
  }

  override def receive = {
    case OpenBucket => try {
      val bucket = cluster.openBucket(bucketName).async()
      metricActor ! Broadcast(BucketActor.BucketOpened(bucket))
      targetActor ! BucketActor.BucketOpened(bucket)
      dashboardActor ! BucketActor.BucketOpened(bucket)
      context.system.scheduler.schedule(tickInterval, tickInterval, self, CollectMetrics)
    } catch {
      case e: Exception =>
        log.error(e, "Could not open bucket {}, retrying in {}.", bucketName, retryDelay)
        context.system.scheduler.scheduleOnce(retryDelay, self, OpenBucket)
    }
    case CollectMetrics => executor.metrics().foreach(metricActor ! Save(_))
  }
}
