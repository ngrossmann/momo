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
import com.couchbase.client.java.document.JsonStringDocument
import rx.Observable
import spray.json._
import DefaultJsonProtocol._
import spray.httpx.marshalling._

object DashboardActor {
  def props(bucket: AsyncBucket) = Props(classOf[DashboardActor], bucket)
  case class UpdateDashboard(dashboard: JsObject)
  case class DashBoardSaved(title: String)
  case class GetDashboard(title: String)
  object DashboardDeleted extends DefaultJsonProtocol {
    implicit val toJson = jsonFormat2(DashboardDeleted.apply)
  }
  case class DeleteDashboard(title: String)
  case class DashboardDeleted(id: String, title: String)
  case class Dashboard(dashboard: JsValue)
  private[DashboardActor] case object SaveMetadata

  object DashboardMetadataIndex extends DefaultJsonProtocol {
    implicit val toJson = jsonFormat1(DashboardMetadataIndex.apply)
  }
  case class DashboardMetadataIndex(dashboards: Seq[DashboardMetadata])

  case class SearchDashboards(query: String)
}

class DashboardActor(bucket: AsyncBucket) extends Actor with ActorLogging {
  import DashboardActor._
  val metadataId = "dashboard-metadata"
  var dashboards = Map[String, DashboardMetadata]()

  override def preStart(): Unit = {
    load().subscribe((metadata: DashboardMetadataIndex) => self ! metadata)
  }

  override def receive = {
    case UpdateDashboard(dashboard) =>
      val replyTo = sender
      val title = dashboard.fields("title").convertTo[String]
      val id = dashboard.fields.get("originalTitle").map(_.convertTo[String]).
        flatMap(dashboards.get(_)) match {
        case Some(metadata) if metadata.title != title => // Change title
          val id = metadata.id
          dashboards = dashboards - metadata.title +
            ((title, DashboardMetadata(id, title, Seq())))
          self ! SaveMetadata
          id
        case Some(metadata) => metadata.id
        case None =>
          val id = s"dashboards/${UUID.randomUUID().toString()}"
          dashboards = dashboards + ((title, DashboardMetadata(id, title, Seq())))
          self ! SaveMetadata
          id
      }

      val document = JsObject(("id", JsString(id)), ("dashboard", dashboard))
      bucket.upsert(JsonStringDocument.create(id, document.toJson.compactPrint)).subscribe{
        document: JsonStringDocument =>
          replyTo ! DashBoardSaved(title)
      }

    case GetDashboard(title) =>
      val replyTo = sender
      dashboards.get(title).map(_.id) match {
        case Some(id) =>
          log.info(s"Getting Dashboard {} with ID {}", title, id)
          bucket.get(id, classOf[JsonStringDocument]).subscribe(replyDashboard(sender)_)
        case None => replyTo ! Status.Failure(new NoSuchElementException(title))
      }

    case SaveMetadata =>
      val document = JsonStringDocument.create(metadataId,
        DashboardMetadataIndex(dashboards.values.toList).toJson.compactPrint)
      bucket.upsert(document).subscribe {
        document: JsonStringDocument =>
        log.info("Saved dashboard metadata")
      }

    case DashboardMetadataIndex(metadata) =>
      dashboards = dashboards ++ Map(metadata.map((d) => (d.title, d)):_*)

    case SearchDashboards(query) =>
      sender ! DashboardMetadataIndex(
        dashboards.values.filter(d => d.filter(query)).toSeq)
    case DeleteDashboard(title) =>
      dashboards.get(title).map(_.id) match {
        case Some(id) =>
          log.info("Deleting dashboard {} {}", title, id)
          val replyTo = sender
          dashboards = dashboards - title
          self ! SaveMetadata
          bucket.remove(id, classOf[JsonStringDocument]).subscribe {
            document: JsonStringDocument =>
              replyTo ! DashboardDeleted(id, title)
          }
        case None => sender ! Status.Failure(new NoSuchElementException(title))
      }
  }

  private def load(): Observable[DashboardMetadataIndex] = {
    bucket.get(metadataId, classOf[JsonStringDocument]).map {
      (document: JsonStringDocument) =>
        document.content().parseJson.convertTo[DashboardMetadataIndex]
    }
  }

  private def replyDashboard(replyTo: ActorRef)(document: JsonStringDocument): Unit = {
    val jsObject = document.content().parseJson.asJsObject
    replyTo ! Dashboard(jsObject.fields("dashboard"))
  }
}
