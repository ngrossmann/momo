package net.n12n.momo

import java.io.File

import akka.util.Timeout
import spray.util.LoggingContext

import scala.concurrent.duration._
import akka.actor.ActorRef
import akka.pattern.ask
import net.n12n.momo.couchbase.Main._
import net.n12n.momo.couchbase._
import net.n12n.momo.grafana.DashboardDescription
import spray.http.HttpEntity.NonEmpty
import spray.http._
import spray.json.JsObject
import spray.routing.{HttpService, Route}
import spray.routing.directives._
import spray.http._
import spray.httpx.SprayJsonSupport._
import spray.json.{JsObject, JsString}

trait MomoWebService extends HttpService {
  def route(services: CouchbaseActor.CouchbaseServices,
            grafanaDirectory: Option[File], queryActor: ActorRef)(implicit timeout: Timeout): Route = {
    implicit def executionContext = actorRefFactory.dispatcher
    val log = LoggingContext.fromActorRefFactory
    val metricActor = services.metricActor
    val dashboardActor = services.dashboardActor
    val targetActor = services.targetActor
    val queryExecutor = new QueryExecutor(queryActor)

//    import ExecutionDirectives._
//    import PathDirectives._
//    import MethodDirectives._
//    import MarshallingDirectives._
//    import MiscDirectives._
//    import RouteDirectives._
//    import ParameterDirectives._

    val queryParser = new QueryParser

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
    val pattern = "([0-9]+)([msh]?)".r
    value match {
      case pattern(time, "h") => time.toLong hours
      case pattern(time, "m") => time.toLong minutes
      case pattern(time, "s") => time.toLong seconds
      case pattern(time, "") => time.toLong seconds
      case _ => throw new IllegalArgumentException(s"Time value not understood: ${value}")
    }
  }
}
