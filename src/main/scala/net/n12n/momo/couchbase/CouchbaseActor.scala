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

import net.n12n.momo.couchbase.MetricActor.Save


import akka.actor.{ActorRef, Actor, ActorLogging}
import akka.routing.{Broadcast, FromConfig}
import com.couchbase.client.java.{CouchbaseCluster}

import net.n12n.momo.util.RichConfig.RichConfig

object CouchbaseActor {
  case object OpenBucket
  case object CollectMetrics
  case class CouchbaseServices(dashboardActor: ActorRef, metricActor: ActorRef,
                               targetActor: ActorRef)
}

class CouchbaseActor extends Actor with ActorLogging with ActorMonitoring {
  import net.n12n.momo.couchbase.CouchbaseActor._
  import context.dispatcher

  private val config =  context.system.settings.config
  private val retryDelay = config.getFiniteDuration(
    "momo.couchbase.bucket-open-retry-delay")
  private val bucketName = config.getString("couchbase.bucket")
  private val bucketPassword = config.getStringOption("couchbase.password")

  private val cluster = CouchbaseCluster.create(
    config.getStringList("couchbase.cluster"))

  private val executor = new PooledScheduler(seriesKeyPrefix,
    config.getInt("momo.couchbase.scheduler-threads.core-pool-size"),
    config.getInt("momo.couchbase.scheduler-threads.max-pool-size"),
    config.getInt("momo.couchbase.scheduler-threads.queue-size"))

  override def preStart(): Unit = {
    val children = new CouchbaseServices(
      context.actorOf(DashboardActor.props, "dashboard"),
      context.actorOf(MetricActor.props(executor.threadPool)),
      context.actorOf(TargetActor.props, "target"))
    context.parent ! children
    self ! OpenBucket
  }

  /**
    * Don't call `preStart()` to avoid recreation of children, but
    * send [[net.n12n.momo.couchbase.CouchbaseActor.OpenBucket]] to `self`.
    */
  override def postRestart(reason: Throwable): Unit = {
    self ! OpenBucket
  }

  override def preRestart(reason: Throwable, message: Option[Any]): Unit = {
    super.preRestart(reason, message)
    log.error(reason, "CouchbaseActor restarting: %s", reason.getMessage)
    shutdown()
  }

  override def postStop(): Unit = {
    shutdown()
  }

  override def receive = {
    case OpenBucket => try {
      val bucket = if (bucketPassword.isDefined)
        cluster.openBucket(bucketName, bucketPassword.get).async()
      else
        cluster.openBucket(bucketName).async()
      log.info("Opened bucket {}", bucket.name())
      context.children.foreach(_ ! BucketActor.BucketOpened(bucket))
      context.system.scheduler.schedule(tickInterval, tickInterval, self, CollectMetrics)
    } catch {
      case e: Exception =>
        log.error(e, "Could not open bucket {}, retrying in {}.", bucketName, retryDelay)
        context.system.scheduler.scheduleOnce(retryDelay, self, OpenBucket)
    }
    case CollectMetrics => executor.metrics().foreach(context.parent ! Save(_))
  }

  /**
    * Disconnect from cluster and shutdown executor thread pool.
    */
  private def shutdown(): Unit = {
    try {
      executor.threadPool.shutdown()
      cluster.disconnect()
    } catch {
      case e: Exception => log.error(e, "Failed to release CouchbaseActor resources")
    }
  }
}
