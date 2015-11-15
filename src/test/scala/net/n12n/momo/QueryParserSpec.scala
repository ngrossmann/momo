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

import org.scalatest.{Matchers, FlatSpec}

class QueryParserSpec extends FlatSpec with Matchers {
  import QueryParser._
  "`servers.localhost.cpu.system,servers.localhost.cpu.user:sum,:plus`" should
    "parse as name, name, operator" in {
    val parser = new QueryParser
    val query = parser.parseQuery(
      "servers.localhost.cpu.system,servers.localhost.cpu.user:sum,:plus")
    query.successful should be(true)
    query.get match {
      case List(Name(t1, Normalizer("mean", _)), Name(t2, Normalizer("sum", _)),
        Operator("plus", _)) =>
      case result => this.fail(s"$result doesn't match expected List(...)")
    }
  }

  """`/servers.localhost.cpu-(user|system|wait)/m:sum`""" should
    "parse as regex" in {
    val parser = new QueryParser
    val query = parser.parseQuery("/servers.localhost.cpu-(user|system|wait)/m:sum")
    query.successful should be(true)
    query.get match {
      case List(Pattern(pattern, true, Normalizer("sum", _))) =>
      case result => fail(s"$result doesn't match expected List(...)")
    }
  }
}
