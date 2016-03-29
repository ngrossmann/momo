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
package net.n12n.momo

import java.io.File

import akka.actor.{Actor, Props}
import akka.pattern.AskTimeoutException
import akka.util.Timeout
import net.n12n.momo.couchbase.{CouchbaseActor, QueryActor}
import spray.http.StatusCodes
import spray.routing.{ExceptionHandler, Route}
import net.n12n.momo.util.RichConfig.RichConfig

object MomoWebServiceActor {
  def props(services: CouchbaseActor.CouchbaseServices) =
    Props(classOf[MomoWebServiceActor], services)
}

/**
  * Actor handling web-service requests.
  */
class MomoWebServiceActor(services: CouchbaseActor.CouchbaseServices)
  extends Actor with MomoWebService {
  def actorRefFactory = context
  val config = context.system.settings.config
  //val log = LoggingContext.fromActorSystem(context.system)
  val queryActor = context.actorOf(
    QueryActor.props(services.targetActor, services.metricActor), "query")
  val grafanaDirectory: Option[File] =
    if (config.getString("momo.grafana-root").equals("classpath"))
      None else Some(new File(config.getString("momo.grafana-root")))

  implicit val exceptionHandler = ExceptionHandler {
    case e: IllegalArgumentException =>
      //log.error(e, "Bad Request")
      complete(StatusCodes.BadRequest, e.getMessage)
    case e: NoSuchElementException => complete(StatusCodes.NotFound, e.getMessage)
    case e: AskTimeoutException => complete(StatusCodes.ServiceUnavailable,
      "Backend query timed out, try again later.")
    case e =>
      complete(StatusCodes.InternalServerError, e.getMessage)
  }

  implicit val timeout = new Timeout(config.getFiniteDuration("momo.query-timeout"))
  val route: Route = route(services, grafanaDirectory, queryActor)
  override def receive = runRoute(route)
}
