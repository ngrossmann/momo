package net.n12n.momo

import net.n12n.momo.couchbase.TimeSeries

import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration
import scala.util.matching.Regex

trait QueryService {
  import TimeSeries._

  def targets(pattern: Regex): Future[String]

  def query(name:String, from: Long, to: Long, rate: FiniteDuration,
               normalizer: Aggregator): Future[TimeSeries]

  def query(name: Seq[String], from: Long, to: Long,
                   rate: FiniteDuration, aggregator: Aggregator,
                   merge: Boolean): Future[List[Future[TimeSeries]]]

  def query(pattern: Regex, from: Long, to: Long,
            rate: FiniteDuration, aggregator: Aggregator,
            merge: Boolean): Future[List[Future[TimeSeries]]]
}
