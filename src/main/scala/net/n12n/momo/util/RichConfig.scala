package net.n12n.momo.util

import java.util.concurrent.TimeUnit

import com.typesafe.config.Config

import scala.concurrent.duration.FiniteDuration

object RichConfig {

  implicit class RichConfig(config: Config) {
    def getFiniteDuration(key: String): FiniteDuration = {
      val unit = TimeUnit.MILLISECONDS
      FiniteDuration(config.getDuration(key, unit), unit)
    }
  }
}
