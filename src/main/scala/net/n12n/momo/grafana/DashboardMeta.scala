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
package net.n12n.momo.grafana

import spray.json.DefaultJsonProtocol

/**
 * Dashboard meta-data.
 */
case class DashboardMeta(isStarred: Option[Boolean], isHome: Option[Boolean],
                          isSnapshot: Option[Boolean], `type`: Option[String],
                          canSave: Boolean, canEdit: Boolean, canStar: Boolean,
                          slug: String, expires: String, created: String)

object DashboardMeta extends DefaultJsonProtocol {
  implicit val toJson = jsonFormat10(DashboardMeta.apply)
  val default = DashboardMeta(isStarred = None, isHome = Some(true),
    isSnapshot = None, `type` = None,
    canSave = false, canEdit = false, canStar = false,
    slug = "", expires = "0001-01-01T00:00:00Z",
    created = "0001-01-01T00:00:00Z")
}