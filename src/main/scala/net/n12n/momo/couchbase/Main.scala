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

import akka.util.Timeout
import spray.http.{AllOrigins, HttpOrigin, HttpHeaders}

import scala.concurrent.duration._
import akka.actor.{Props, ActorSystem}
import akka.pattern.ask
import com.typesafe.config.ConfigFactory
import spray.routing.SimpleRoutingApp
import spray.httpx.SprayJsonSupport._

object Main extends App with SimpleRoutingApp {
  val config = ConfigFactory.load()
  implicit val system = ActorSystem("momo", config)
  val couchbaseActor = system.actorOf(Props[CouchbaseActor], "db")
  val bucketActor = system.actorSelection("akka://momo/user/db/bucket")
  val metricActor = system.actorSelection("akka://momo/user/db/metric")
  val queryActor = system.actorSelection("akka://momo/user/db/query")
  implicit val executionContext = system.dispatcher

  startServer(interface = "0.0.0.0", port = 8080) {
    val corsHeader = HttpHeaders.`Access-Control-Allow-Origin`(AllOrigins)
    pathPrefix("series") {
      pathEndOrSingleSlash {
        post {
          import MetricPoint._
          entity(as[MetricPoint]) { point =>
            complete {
              bucketActor ! BucketActor.Save(point)
              "ok"
            }
          }
        } ~
        get {
          parameters('from.as[Long], 'to.as[Long], 'series.as[String],
            'function.as[Option[String]], 'interval.as[Option[String]]) {
            (from, to, series, function, interval) => respondWithHeader(corsHeader) {
              val sampleRate = interval.map(s2duration).getOrElse(1 minute)
              complete {
                implicit val timeout = new Timeout(5 seconds)
                if (series.startsWith("/") && series.endsWith("/")) {
                  val pattern = series.substring(1, series.length - 1)
                  (queryActor ? QueryActor.QueryRegex(pattern.r, from, to,
                    interval.map(s2duration).getOrElse(1 minute),
                    function.flatMap(TimeSeries.aggregators.get(_)).getOrElse(TimeSeries.mean))).
                    mapTo[TimeSeries].map(net.n12n.momo.grafana.TimeSeries(_))
                } else {
                  val sampler = TimeSeries.sampler(function, sampleRate)
                  (bucketActor ? BucketActor.Get(series, from, to)).mapTo[TimeSeries].
                    map((d) => net.n12n.momo.grafana.TimeSeries(sampler(d)))
                }
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
            (series) => respondWithHeader(corsHeader) {
              complete {
                import spray.json.DefaultJsonProtocol._
                implicit val timeout = new Timeout(5 seconds)
                (metricActor ? TargetActor.SearchTargets(series)).
                  mapTo[TargetActor.SearchResult].map(_.names)
              }
            }
          }
        }
      //}
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
