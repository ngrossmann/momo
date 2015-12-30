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

import java.io.{InputStream, FileInputStream, File}
import java.nio.file.Files

import akka.actor.{ActorSystem, Props}
import akka.pattern.{AskTimeoutException, ask}
import akka.util.Timeout
import com.typesafe.config.ConfigFactory
import net.n12n.momo.{QueryParser, QueryExecutor, UdpReceiverActor}
import net.n12n.momo.grafana.DashboardDescription
import net.n12n.momo.util.RichConfig.RichConfig
import spray.http.HttpEntity.NonEmpty
import spray.http._
import spray.httpx.SprayJsonSupport._
import spray.json.{JsObject, JsString}
import spray.routing.{ExceptionHandler, Route, SimpleRoutingApp}
import spray.util.LoggingContext

import scala.concurrent.duration._

object Main extends App with SimpleRoutingApp {
  val config = ConfigFactory.load()
  implicit val system = ActorSystem("momo", config)
  val queryTimeout = system.settings.config.getFiniteDuration("momo.query-timeout")
  val couchbaseActor = system.actorOf(Props[CouchbaseActor], "db")
  val metricActor = system.actorSelection("akka://momo/user/db/metric")
  val targetActor = system.actorSelection("akka://momo/user/db/target")
  val dashboardActor = system.actorSelection("akka://momo/user/db/dashboard")
  val queryActor = system.actorOf(
    QueryActor.props(targetActor, metricActor), "query")

  val queryExecutor = new QueryExecutor(queryActor, system.dispatcher)
  val queryParser = new QueryParser
  if (config.getBoolean("momo.statsd.enabled"))
    system.actorOf(UdpReceiverActor.propsStatsD(metricActor), "statsd")
  if (config.getBoolean("momo.graphite.enabled"))
    system.actorOf(UdpReceiverActor.propsGraphite(metricActor), "graphite")
  implicit val executionContext = system.dispatcher
  val log = LoggingContext.fromActorSystem(system)

  val exceptionHandler = ExceptionHandler {
    case e: IllegalArgumentException =>
      log.error(e, "Bad Request")
      complete(StatusCodes.BadRequest, e.getMessage)
    case e: NoSuchElementException => complete(StatusCodes.NotFound, e.getMessage)
    case e: AskTimeoutException => complete(StatusCodes.ServiceUnavailable,
      "Backend query timed out, try again later.")
    case e =>
      complete(StatusCodes.InternalServerError, e.getMessage)
  }

  startServer(interface = config.getString("momo.http.listen-address"),
    port = config.getInt("momo.http.port")) {
    implicit val timeout = new Timeout(queryTimeout)
    val grafanaDirectory: Option[File] =
      if (config.getString("momo.grafana-root").equals("classpath"))
        None else Some(new File(config.getString("momo.grafana-root")))
    // val corsHeader = HttpHeaders.`Access-Control-Allow-Origin`(AllOrigins)
    handleExceptions(exceptionHandler) {
      pathPrefix("series") {
        pathEndOrSingleSlash {
          post {
            import MetricPoint._
            entity(as[MetricPoint]) { point =>
              complete {
                metricActor ! MetricActor.Save(point)
                "ok"
              }
            }
          } ~
          get {
            parameters('from.as[Option[Long]], 'to.as[Option[Long]],
              'q.as[String], 'interval.as[Option[String]]) { (from, to, q, interval) =>
              val sampleRate = interval.map(s2duration).getOrElse(1 minute)
              val now = System.currentTimeMillis()
              val fromMs = convertMs(from.getOrElse(now - (1 hour).toMillis))
              val toMs = convertMs(to.getOrElse(now))
              log.debug("Parsing query {}", q)
              val query = queryParser.parseQuery(q)
              complete {
                val result = queryExecutor.execute(query.get,
                  QueryExecutor.QueryContext(fromMs, toMs, sampleRate))
                result.map(_.map(net.n12n.momo.grafana.TimeSeries(_)))
              }
            }
          }
        }
      } ~
      path("metrics") {
        get {
          parameter('series.as[String]) {
            (series) =>
              complete {
                import spray.json.DefaultJsonProtocol._
                (targetActor ? TargetActor.SearchTargets(series)).
                  mapTo[TargetActor.SearchResult].map(_.names)
              }
          }
        }
      } ~
      pathPrefix("grafana") {
        get {
          pathEndOrSingleSlash {
            readTemplate(grafanaDirectory, "/views/index.html")
          } ~
          path("public" / Rest) { path =>
              readTemplate(grafanaDirectory, path)
          } ~
          path("plugins" / Rest) { path =>
            log.info("getting plugin {}", path)
            readFile(grafanaDirectory, "app/plugins/" + path)
          } ~
          path(Rest) { path =>
            readTemplate(grafanaDirectory, path)
          }
        }
      } ~
      pathPrefix("api") {
        pathPrefix("dashboards") {
          pathPrefix("db") {
            pathEndOrSingleSlash {
              post {
                requestUri { uri =>
                  import spray.json.DefaultJsonProtocol._
                  entity(as[JsObject]) { request =>
                    request.fields.get("dashboard") match {
                      case Some(dashboard) =>
                        complete {
                          (dashboardActor ? DashboardActor.UpdateDashboard(
                            dashboard.asJsObject)).
                            mapTo[DashboardActor.DashboardSaved].map(
                            reply => DashboardDescription(
                              reply.id, "success", reply.version))
                        }
                      case None => throw new IllegalArgumentException()
                    }
                  }
                }
              }
            } ~
            path(Segment) { path =>
              get {
                complete {
                  import spray.json.DefaultJsonProtocol._
                  (dashboardActor ? DashboardActor.GetDashboard(path)).
                    mapTo[DashboardActor.Dashboard].map(_.dashboard.asInstanceOf[JsObject])
                }
              } ~
              delete {
                complete {
                  import DashboardDescription._
                  (dashboardActor ? DashboardActor.DeleteDashboard(path)).
                    mapTo[DashboardActor.DashboardDeleted].map(
                    reply => DashboardDescription(reply.id, "success", 0))
                }
              }
            }
          } ~
          path("home") {
            complete {
              import spray.json.DefaultJsonProtocol._
              (dashboardActor ? DashboardActor.GetDashboard("home")).
                mapTo[DashboardActor.Dashboard].map(
                  _.dashboard.asInstanceOf[JsObject])
            }
          }
        } ~
        path("search") {
          get {
            parameters('query.as[Option[String]], 'starred.as[Option[Boolean]],
              'limit.as[Option[Int]]) { (query, starred, limit) =>
              complete  {
                (dashboardActor ? DashboardActor.SearchDashboards(
                  query, starred, limit)).mapTo[DashboardActor.DashboardMetadataIndex].map(_.dashboards)
              }
            }
          }
        }
      } ~
      pathEndOrSingleSlash {
        get {
          respondWithSingletonHeader(HttpHeaders.Location("/grafana/")) {
            respondWithStatus(StatusCodes.TemporaryRedirect) {
              complete("")
            }
          }
        }
      }
    }
  }

  private def readFile(root: Option[File], path: String): Route = {
    root match {
      case Some(root) => getFromFile(new File(root, path))
      case None => getFromResource(s"grafana/${path}")
    }
  }

  private def readTemplate(root: Option[File], path: String): Route = ctx => {
    readFile(root, path).apply(ctx.withHttpResponseEntityMapped(mapTemplate))
  }

  private def mapTemplate(entity: HttpEntity): HttpEntity = {
    entity match {
      case NonEmpty(contentType, data)
        if contentType.mediaType == MediaTypes.`text/html` =>
        log.info("Mapping {}", contentType)
        HttpEntity(contentType,
          HttpData(data.asString.replace("[[.AppSubUrl]]", "/grafana")))
      case e => e
    }
  }

  private def convertMs(value: Long) =
    if (value < 1000000000000L) value * 1000 else value

  /**
   * Convert string like 2s, 2m, 2h to [[scala.concurrent.duration.FiniteDuration]].
   *
   * @param value string representation of duration.
   * @return duration.
   * @throws IllegalArgumentException if pattern doesn't match expected format.
   */
  private def s2duration(value: String): FiniteDuration = {
    val pattern = "([0-9]+)([msh])".r
    value match {
      case pattern(time, "h") => time.toLong hour
      case pattern(time, "m") => time.toLong minute
      case pattern(time, "s") => time.toLong second
      case _ => throw new IllegalArgumentException(s"Time value not understood: ${value}")
    }
  }
}
