package net.n12n.momo.couchbase

import spray.json.DefaultJsonProtocol

object DashboardMetadata extends DefaultJsonProtocol {
  implicit val toJson = jsonFormat3(DashboardMetadata.apply)
}

case class DashboardMetadata(id: String, title: String, tags: Seq[String]) {
  def filter(query: String): Boolean = {
    val q = query.toLowerCase
    title.toLowerCase.contains(q) || tags.exists(_.toLowerCase.contains(q))
  }
}
