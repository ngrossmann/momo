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

import akka.actor.ActorRef
import akka.pattern.ask
import akka.util.Timeout
import net.n12n.momo.couchbase.{QueryActor, TimeSeries}

import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration._

object QueryExecutor {
  case class QueryContext(from: Long, to: Long, rate: FiniteDuration)
}

/**
 * Execute a query.
 */
class QueryExecutor(queryActor: ActorRef)(
                    implicit val executionContext: ExecutionContext) {
  import QueryExecutor._
  import QueryParser._

  protected def executeImpl(query: List[QueryToken], context: QueryContext,
                           stack: Future[List[TimeSeries]] = Future.successful(Nil)):
  Future[List[TimeSeries]] = {
    if (query.isEmpty) {
      stack
    } else {
      implicit val timeout = Timeout(1 second)
      val newStack: Future[List[TimeSeries]] = query.head match {
        case Name(name, normalizer) =>
          val reply = (queryActor ? QueryActor.QueryList(
            Seq(name), context.from, context.to, context.rate, normalizer.fun,
            true)).mapTo[QueryActor.Result]
          reply.flatMap(t => stack.map(t.series.head :: _))
        case Operator(name, fun) =>
          stack.map {
            case op2 :: op1 :: rest =>
              TimeSeries.binOp(op1, op2, fun, s"${op1.name},${op2.name},:$name") :: rest
            case list => throw new IllegalArgumentException(
              s"$name cannot be applied to $list")
          }
        case Pattern(pattern, merge, normalizer) =>
          val reply = (queryActor ? QueryActor.QueryRegex(pattern, context.from, context.to,
            context.rate, normalizer.fun, merge)).mapTo[QueryActor.Result]
          reply.flatMap(r => stack.map(r.series.toList ::: _))
        case Static(value) =>
          stack.map(TimeSeries.static(value, context.from, context.to,
            context.rate) :: _)
      }
      executeImpl(query.tail, context, newStack)
    }
  }

  def execute(query: List[QueryToken], context: QueryContext):
    Future[List[TimeSeries]] = {
    executeImpl(query, context)
  }
}
