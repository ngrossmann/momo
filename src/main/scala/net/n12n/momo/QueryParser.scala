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

import net.n12n.momo.couchbase.TimeSeries

import scala.util.matching.Regex
import scala.util.parsing.combinator.JavaTokenParsers

object QueryParser {
  sealed trait QueryToken
  case class Operator(name: String, fun: TimeSeries.BinOp) extends QueryToken
  case class Normalizer(name: String, fun: TimeSeries.Aggregator)
  case class Name(name: String, normalizer: Normalizer) extends QueryToken
  case class Pattern(pattern: Regex, merge: Boolean,
                     normalizer: Normalizer) extends QueryToken
  case class Static(value: TimeSeries.ValueType) extends QueryToken
}

/**
 * Parse a query into a list of [[net.n12n.momo.QueryParser.QueryToken tokens]].
 */
class QueryParser extends JavaTokenParsers {
  import QueryParser._
  val defaultNormalizer = Normalizer("mean", TimeSeries.mean)
  def name: Parser[String] = """[a-zA-Z_](\w|[.-])*""".r
  def pattern: Parser[Regex] = """/([^/]*)/""".r ^^ {
    r => r.substring(1, r.length - 1).r
  }
  def normalizer: Parser[Normalizer] = ":"~>(
    "mean" ^^ { s => Normalizer(s, TimeSeries.mean) } |
    "sum" ^^ { s => Normalizer(s, TimeSeries.sum) } |
    "max" ^^ { s => Normalizer(s, TimeSeries.max) } |
    "min" ^^ { s => Normalizer(s, TimeSeries.min) })

  def operator: Parser[Operator] = ":"~>(
    "avg" ^^ { o => Operator(o, TimeSeries.avg) } |
    "plus" ^^ { o => Operator(o, TimeSeries.plus) } |
    "minus" ^^ { o => Operator(o, TimeSeries.minus) } |
    "mul" ^^ { o => Operator(o, TimeSeries.mul) } |
    "div" ^^ { o => Operator(o, TimeSeries.div) } )

  def staticVal:Parser[Static] = decimalNumber ^^ { num => Static(num.toLong)}

  def timeSeriesSpec: Parser[QueryToken] =
    name~opt(normalizer) ^^ {
      case name~Some(normalizer) => Name(name, normalizer)
      case name~None => Name(name, defaultNormalizer)
    } |
    pattern~opt("m")~opt(normalizer) ^^ {
      case pattern~merge~Some(normalizer) =>
        Pattern(pattern, merge.isDefined, normalizer)
      case pattern~merge~None =>
        Pattern(pattern, merge.isDefined, defaultNormalizer)
    }

  def timeSeries: Parser[QueryToken] = timeSeriesSpec | staticVal

  def query: Parser[List[QueryToken]] =
    timeSeries~rep(","~>(timeSeries | operator)) ^^ {
      case query => query._1 :: query._2
    }

  def parseQuery(in: String) = parseAll(query, in)
}
