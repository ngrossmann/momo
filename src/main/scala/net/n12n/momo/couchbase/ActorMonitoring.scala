package net.n12n.momo.couchbase

import java.net.InetAddress

import akka.actor.Actor

import net.n12n.momo.util.RichConfig.RichConfig

trait ActorMonitoring {
  this: Actor =>

  protected val seriesKeyPrefix = context.system.settings.config.getString(
    "momo.internal-metrics.series-key-prefix") + "." +
    InetAddress.getLocalHost.getHostName

  protected val tickInterval = context.system.settings.config.getFiniteDuration(
    "momo.internal-metrics.tick-interval")

}
