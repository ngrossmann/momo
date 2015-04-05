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
import com.couchbase.client.java.AsyncBucket
import com.couchbase.client.java.document.json.JsonArray
import com.couchbase.client.java.document.{RawJsonDocument}
import com.couchbase.client.java.view.{AsyncViewRow, ViewQuery}
import rx.Observable
import spray.json._
import DefaultJsonProtocol._
import spray.httpx.marshalling._

object DashboardActor {
  def props(bucket: AsyncBucket) = Props(classOf[DashboardActor], bucket)
  case class UpdateDashboard(dashboard: JsObject)
  case class DashBoardSaved(title: String, id: String)
  case class GetDashboard(id: String)
  object DashboardDeleted extends DefaultJsonProtocol {
    implicit val toJson = jsonFormat2(DashboardDeleted.apply)
  }
  case class DeleteDashboard(id: String)
  case class DashboardDeleted(id: String, title: String)
  case class Dashboard(dashboard: JsValue)

  object DashboardMetadataIndex extends DefaultJsonProtocol {
    implicit val toJson = jsonFormat1(DashboardMetadataIndex.apply)
  }
  case class DashboardMetadataIndex(dashboards: Seq[DashboardMetadata])

  case class SearchDashboards(query: String)
}

class DashboardActor(bucket: AsyncBucket) extends Actor with ActorLogging {
  import DashboardActor._
  import context.system
  val designDoc = system.settings.config.getString("momo.couchbase.dashboard.design-document")
  val titleIndex = system.settings.config.getString("momo.couchbase.dashboard.title-index")
  val idPrefix = "dashboards/"

  override def receive = {
    case UpdateDashboard(dashboard) =>
      val replyTo = sender
      val title = dashboard.fields("title").convertTo[String]
      val (id: String, d: JsObject) = dashboard.fields.get("id").flatMap(
        v => if (v == JsNull) None else Some(v)) match {
        case Some(id) =>
          (id.asInstanceOf[JsString].value, dashboard)
        case None =>
          val id = UUID.randomUUID().toString()
          (id, dashboard.copy(fields = dashboard.fields.updated("id", JsString(id))))
      }
      val documentId = s"${idPrefix}${id}"
      val document = JsObject(("id", JsString(documentId)), ("dashboard", d))
      bucket.upsert(RawJsonDocument.create(documentId, document.toJson.compactPrint)).subscribe{
        document: RawJsonDocument =>
          log.info("Saved dashboard {} {}", title, documentId)
          replyTo ! DashBoardSaved(title, id)
      }

    case GetDashboard(id) =>
      val replyTo = sender
      val documentId = s"${idPrefix}${id}"
      bucket.get(documentId, classOf[RawJsonDocument]).subscribe(replyDashboard(replyTo)_)

    case SearchDashboards(query) =>
      val replyTo = sender()
      val filter = (t: (String, Object)) =>
        new java.lang.Boolean(t._1.toLowerCase.contains(query.toLowerCase))
      val reduce = (list: List[DashboardMetadata], t: (String, Object)) => {
        val array = t._2.asInstanceOf[JsonArray]
        val ids = for (i <- (0 until array.size()).toList) yield
          DashboardMetadata(array.getString(i).substring(idPrefix.length), t._1, Nil)
        ids ::: list
      }

      val list: Observable[(String, Object)] =
        bucket.query(ViewQuery.from(designDoc, titleIndex).group()).
        flatMap(view2rows).map((row: AsyncViewRow) => (row.key.toString, row.value))

      list.filter(filter).reduce(Nil, reduce).subscribe(
        (result: List[DashboardMetadata]) => replyTo ! DashboardMetadataIndex(result))

    case DeleteDashboard(id) =>
      val replyTo = sender()
      bucket.remove(id, classOf[RawJsonDocument]).subscribe {
        document: RawJsonDocument =>
          val title = if (document != null) document.content() else null
          replyTo ! DashboardDeleted(id, title)
      }
  }

  private def replyDashboard(replyTo: ActorRef)(document: RawJsonDocument): Unit = {
    val jsObject = document.content().parseJson.asJsObject
    replyTo ! Dashboard(jsObject.fields("dashboard"))
  }
}
