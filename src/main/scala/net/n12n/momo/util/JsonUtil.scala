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
package net.n12n.momo.util

import spray.json.{JsValue, JsNull, JsString, JsObject, JsonFormat}

object JsonUtil {
  /**
   * Extract field value from Json document, if the value was not set,
   * apply a default value and update the document.
   *
   * @param document Json document.
   * @param name field to extract.
   * @param defaultValue code block to create default value in case the field
   *                     does not exist.
   * @return tuple with extracted value and original or updated document.
   */
  def extractSetField[T](document: JsObject, name: String,
                      defaultValue: => T)(implicit $ev0: JsonFormat[T]):
  (T, JsObject) = {
    document.fields.get(name).flatMap(
      v => if (v == JsNull) None else Some(v)) match {
      case Some(id) =>
        ($ev0.read(id), document)
      case None =>
        val id = defaultValue
        (id, document.copy(
          fields = document.fields.updated(name, $ev0.write(id))))
    }
  }

  def extractWithDefault[T](document: JsObject, name: String,
                         defaultValue: => T)(implicit $ev0: JsonFormat[T]): T = {
    document.fields.get(name) match {
      case Some(value) => $ev0.read(value)
      case None => defaultValue
    }
  }
}
