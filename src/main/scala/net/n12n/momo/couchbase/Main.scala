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

import java.io.File

import akka.util.Timeout
import kamon.Kamon
import spray.http._
import spray.json.{JsString, JsObject}

import scala.concurrent.duration._
import akka.actor.{Props, ActorSystem}
import akka.pattern.{AskTimeoutException, ask}
import com.typesafe.config.ConfigFactory
import spray.routing.{ExceptionHandler, Route, SimpleRoutingApp}
import spray.httpx.SprayJsonSupport._

import net.n12n.momo.util.RichConfig.RichConfig

object Main extends App with SimpleRoutingApp {
  val config = ConfigFactory.load()
  Kamon.start(config)
  implicit val system = ActorSystem("momo", config)
  val queryTimeout = system.settings.config.getFiniteDuration("momo.query-timeout")
  val couchbaseActor = system.actorOf(Props[CouchbaseActor], "db")
  val metricActor = system.actorSelection("akka://momo/user/db/metric")
  val targetActor = system.actorSelection("akka://momo/user/db/target")
  val queryActor = system.actorSelection("akka://momo/user/db/query")
  val dashboardActor = system.actorSelection("akka://momo/user/db/dashboard")
  val statsdActor = system.actorOf(UdpReceiverActor.propsStatsD(metricActor), "statsd")
  val graphiteActor = system.actorOf(
    UdpReceiverActor.propsGraphite(metricActor), "graphite")
  implicit val executionContext = system.dispatcher

  val exceptionHandler = ExceptionHandler {
    case e: IllegalArgumentException => complete(StatusCodes.BadRequest, e.getMessage)
    case e: NoSuchElementException => complete(StatusCodes.NotFound, e.getMessage)
    case e: AskTimeoutException => complete(StatusCodes.ServiceUnavailable,
      "Backend query timed out, try again later.")
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
            parameters('from.as[Long], 'to.as[Long], 'series.as[String],
              'function.as[Option[String]], 'interval.as[Option[String]],
              'merge.as[Option[Boolean]]) {
              (from, to, series, function, interval, merge) =>
              val doMerge = merge.getOrElse(false)
              val sampleRate = interval.map(s2duration).getOrElse(1 minute)
              complete {
                if (series.startsWith("/") && series.endsWith("/")) {
                  val pattern = series.substring(1, series.length - 1)
                  (queryActor ? QueryActor.QueryRegex(pattern.r, from, to,
                    interval.map(s2duration).getOrElse(1 minute),
                    function.flatMap(TimeSeries.aggregators.get(_)).getOrElse(TimeSeries.mean),
                    doMerge)).mapTo[QueryActor.Result].map(_.series.map(net.n12n.momo.grafana.TimeSeries(_)))
                } else {
                  val sampler = TimeSeries.sampler(function, sampleRate)
                  (metricActor ? MetricActor.Get(series, from, to)).mapTo[TimeSeries].
                    map((d) => Seq(net.n12n.momo.grafana.TimeSeries(sampler(d))))
                }
              }
            }
          }
        }
      } ~
      path("metrics") {
        //path("search") {
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
        //}
      } ~
      pathPrefix("grafana") {
        get {
          pathEndOrSingleSlash {
            readFile(grafanaDirectory, "index.html")
          } ~
          path(Rest) { path =>
            readFile(grafanaDirectory, path)
          }
        }
      } ~
      pathPrefix("dashboard") {
        pathEndOrSingleSlash {
          post {
            import spray.json.DefaultJsonProtocol._
            requestUri { uri =>
              entity(as[JsObject]) { dashboard =>
                complete {
                  (dashboardActor ? DashboardActor.UpdateDashboard(dashboard)).
                    mapTo[DashboardActor.DashBoardSaved].map(
                      reply => JsObject(("title", JsString(reply.title)),
                        ("id", JsString(reply.id))))
                }
              }
            }
          } ~
          get {
            parameter('query.as[String]) { query =>
              complete {
                (dashboardActor ? DashboardActor.SearchDashboards(query)).
                  mapTo[DashboardActor.DashboardMetadataIndex]
              }
            }
          }
        } ~
        path(Segment) { path =>
          import spray.json.DefaultJsonProtocol._
          get {
            complete {
              (dashboardActor ? DashboardActor.GetDashboard(path)).
                mapTo[DashboardActor.Dashboard].map(_.dashboard.asInstanceOf[JsObject])
            }
          } ~
          delete {
            complete {
              (dashboardActor ? DashboardActor.DeleteDashboard(path)).
                mapTo[DashboardActor.DashboardDeleted]
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
