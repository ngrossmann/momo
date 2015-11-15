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

import java.io.{FileInputStream, File}

import com.typesafe.config.Config

import scala.util.{Try, Failure, Success}

import net.n12n.momo.util.IO

/**
 * Grafana specific classes and functions
 */
package object grafana {
  def grafanaRoot(config: Config): Option[File] =
    if (config.getString("momo.grafana-root").equals("classpath"))
      None else Some(new File(config.getString("momo.grafana-root")))

  def granfanStaticJson(path: String, config: Config): Try[spray.json.JsValue] = {
    import spray.json._
    val in = grafanaRoot(config) match {
      case Some(root) =>
        val file = new File(root, path)
        if (file.canRead)
          Success(new FileInputStream(file))
        else
          Failure(new NoSuchElementException(file.getAbsolutePath))
      case None =>
        val stream = Thread.currentThread().getContextClassLoader.
          getResourceAsStream(path)
        if (stream != null)
          Success(stream)
        else
          Failure(new NoSuchElementException(path))
    }
    in.map(IO.toString(_, "utf-8")).map(_.parseJson)
  }
}
