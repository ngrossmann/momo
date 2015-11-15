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

import java.util.UUID

import akka.actor._
import akka.pattern.pipe
import com.couchbase.client.java.AsyncBucket
import com.couchbase.client.java.document.json.JsonArray
import com.couchbase.client.java.document.{RawJsonDocument}
import com.couchbase.client.java.view.{Stale, AsyncViewRow, ViewQuery}
import rx.Observable
import spray.json._
import DefaultJsonProtocol._
import spray.httpx.marshalling._

import scala.concurrent.Future
import scala.util.{Success, Failure}

import net.n12n.momo.grafana._
import net.n12n.momo.util.JsonUtil._

object DashboardActor {
  def props = Props[DashboardActor]
  case class UpdateDashboard(dashboard: JsObject)
  case class DashboardSaved(id: String, version: Int)
  case class GetDashboard(id: String)
  case class DeleteDashboard(id: String)
  case class DashboardDeleted(id: String)
  case class Dashboard(dashboard: JsValue)

  object DashboardMetadataIndex extends DefaultJsonProtocol {
    implicit val toJson = jsonFormat1(DashboardMetadataIndex.apply)
  }
  case class DashboardMetadataIndex(dashboards: Seq[DashboardMetadata])

  case class SearchDashboards(query: Option[String], starred: Option[Boolean],
                               limit: Option[Int])
}

class DashboardActor extends Actor with BucketActor with ActorLogging {
  import DashboardActor._
  import context.system
  import context.dispatcher

  val designDoc = system.settings.config.getString("momo.couchbase.dashboard.design-document")
  val titleIndex = system.settings.config.getString("momo.couchbase.dashboard.title-index")
  val idPrefix = "dashboards/"

  override def doWithBucket(bucket: AsyncBucket) = {

    case UpdateDashboard(dashboard) =>
      val replyTo = sender

      val (id, d) = extractSetField(dashboard, "id", UUID.randomUUID().toString)
      val (version, doc) = extractSetField(d, "version", 0)
      val documentId = s"${idPrefix}${id}"
      val document = JsObject(("id", JsString(documentId)), ("dashboard", doc))
      bucket.upsert(RawJsonDocument.create(documentId,
        document.toJson.compactPrint)).subscribe{
        document: RawJsonDocument =>
          log.info("Saved dashboard {} {}", id, documentId)
          replyTo ! DashboardSaved(id, version)
      }

    case GetDashboard(id) if id == "home" =>
      val replyTo = sender
      Future {
        granfanStaticJson("dashboards/home.json",
          context.system.settings.config) match {
          case Success(dashboard) =>
            Dashboard(withMetadata(dashboard))
          case Failure(t) => Status.Failure(t)
        }
      }.pipeTo(replyTo)
    case GetDashboard(id) =>
      val replyTo = sender
      val documentId = s"${idPrefix}${id}"
      bucket.get(documentId, classOf[RawJsonDocument]).subscribe(replyDashboard(replyTo)_)

    case SearchDashboards(query, starred, limit) =>
      val replyTo = sender()
      val filter: ((String, Object)) => Boolean = t => {
        val opt = query.map(s => t._1.toLowerCase.contains(s.toLowerCase))
        opt.getOrElse(true)
      }

      val reduce = (list: List[DashboardMetadata], t: (String, Object)) => {
        val array = t._2.asInstanceOf[JsonArray]
        val ids = for (i <- (0 until array.size()).toList) yield {
          val id = array.getString(i).substring(idPrefix.length)
          DashboardMetadata(id, t._1,
            Nil, s"db/$id")
        }
        ids ::: list
      }

      // TODO: Get rid of Stale.FALSE
      val list: Observable[(String, Object)] =
        bucket.query(ViewQuery.from(designDoc, titleIndex).
          stale(Stale.FALSE).group()).
          flatMap(view2rows).map((row: AsyncViewRow) => (row.key.toString, row.value))


      list.filter(filter.andThen(new java.lang.Boolean(_))).
        reduce(Nil, reduce).take(limit.getOrElse(Int.MaxValue)).
        subscribe((result: List[DashboardMetadata]) => {
          replyTo ! DashboardMetadataIndex(result)
        }, (t: Throwable) => replyTo ! Status.Failure(t))

    case DeleteDashboard(id) =>
      val documentId = s"${idPrefix}${id}"
      val replyTo = sender()

      val replyDeleted = (document: RawJsonDocument) => {
        log.info("Deleted dashboard {}", id)
        replyTo ! DashboardDeleted (id)
      }
      val onError: (Throwable) => Unit = BucketActor.onCouchbaseError(replyTo)

      bucket.remove(documentId, classOf[RawJsonDocument]).subscribe(
        replyDeleted, onError)
  }

  private def replyDashboard(replyTo: ActorRef)(document: RawJsonDocument): Unit = {
    val jsObject = document.content().parseJson.asJsObject
    val dashboard = jsObject.fields("dashboard")
    val meta = DashboardMeta(
      isStarred = Some(extractWithDefault(jsObject, "isStarred", false)),
      isHome = Some(false),
      isSnapshot = Some(false),
      `type` = Some("momo"),
      canSave = true,
      canEdit = true,
      canStar = true,
      slug = document.id.substring(idPrefix.length),
      expires = "0001-01-01T00:00:00Z",
      created = "0001-01-01T00:00:00Z"
    )
    replyTo ! Dashboard(withMetadata(dashboard, meta))
  }

  /**
   * Create Json object with `dashbaord` and `meta` fields.
   * @param dashboard dashboard document.
   * @return
   */
  private def withMetadata(dashboard: JsValue,
                           meta: DashboardMeta = DashboardMeta.default) =
    JsObject(Map("meta" -> meta.toJson, "dashboard" -> dashboard))
}
