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

import org.scalatest.{ShouldMatchers, FlatSpec}

import spray.json._

/**
 *
 *
 * @author niklas
 */
class JsonUtilSpec extends FlatSpec with ShouldMatchers {
  import DefaultJsonProtocol._
  import JsonUtil._


  val document =
    """
      |{ "astring": "a string value",
      |  "aint": 5
      |}
    """.stripMargin.parseJson.asJsObject

  "extractSetField" should "extract existing string field" in {
    val (value, doc) = extractSetField(document, "astring", "other string")
    value should be("a string value")
  }

  "extractSetField" should "return and set default value" in {
    val (value, doc) = extractSetField(document, "bstring", "b string value")
    value should be("b string value")
    val bstring = doc.fields.get("bstring")
    bstring should be(Some(JsString("b string value")))
  }

  "extractSetField" should "return existing Int field" in {
    val (value, doc) = extractSetField(document, "aint", -1)
    value should be(5)
  }
}
