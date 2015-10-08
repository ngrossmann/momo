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

import java.util.NoSuchElementException

import akka.actor.{ActorRef, Status, ActorLogging, Actor}
import com.couchbase.client.java.AsyncBucket
import com.couchbase.client.java.error.DocumentDoesNotExistException

object BucketActor {
  case class BucketOpened(bucket: AsyncBucket)

  /**
   * Error handler to convert common Couchbase errors to
   * [[akka.actor.Status.Failure]] instances with more generic
   * error messages.
   */
  def onCouchbaseError(sender: ActorRef)(e: Throwable): Unit = {
    val status = e match {
      case e: DocumentDoesNotExistException =>
        // using e.getMessage returns null NoSuchElementException
        // doesn't accept that.
        akka.actor.Status.Failure(new NoSuchElementException(""))
      case e: Throwable =>
        akka.actor.Status.Failure(e)
    }
    sender ! status
  }
}

trait BucketActor {
  this: Actor with ActorLogging =>

  import BucketActor._

  override def receive: Receive = {
    case BucketOpened(bucket: AsyncBucket) =>
      log.info("Got bucket {}.", bucket.name)
      context.become(doWithBucket(bucket))
  }

  def doWithBucket(bucket: AsyncBucket): Receive
}
