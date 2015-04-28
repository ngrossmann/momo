package net.n12n.momo.couchbase

import java.util.concurrent._
import java.util.concurrent.atomic.AtomicLong

class PooledScheduler(metricPrefix: String, corePoolSize: Int, maxPoolSize: Int,
                       queueSize: Int) {
  private val rejected = new AtomicLong(0L)
  private val queue = new LinkedBlockingDeque[Runnable](queueSize)
  private val factory = new ThreadFactory {
    override def newThread(runnable: Runnable): Thread =
      new Thread(runnable, "pooled-scheduler")
  }

  private val rejectHandler = new RejectedExecutionHandler {
    override def rejectedExecution(runnable: Runnable,
                                   threadPoolExecutor: ThreadPoolExecutor): Unit = {
      rejected.incrementAndGet()
    }
  }

  val threadPool = new ThreadPoolExecutor(corePoolSize, maxPoolSize,
    1L, TimeUnit.MINUTES, queue, factory, rejectHandler)

  def metrics(): Seq[MetricPoint] = List(
    metricPoint("active-count", threadPool.getActiveCount),
    metricPoint("pool-size", threadPool.getPoolSize),
    metricPoint("largest-pool-size", threadPool.getLargestPoolSize),
    metricPoint("queue-size", queue.size()),
    metricPoint("rejected", rejected.getAndSet(0)))

  protected def metricPoint(metric: String, value: Long) =
    MetricPoint(s"${metricPrefix}.pooled-scheduler.${metric}_g",
      System.currentTimeMillis, value)
}
