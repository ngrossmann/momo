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

import java.net.InetSocketAddress

import akka.actor._
import akka.io.{Udp, IO}
import net.n12n.momo.couchbase.MetricActor

class UdpReceiverActor(metricActor: ActorSelection, configPath: String,
                      val parseMetric: ReceiverActor.MetricParser)
  extends Actor with ActorLogging with ReceiverActor {
  import context.system
  val address = system.settings.config.getString(s"${configPath}.listen-address")
  val port = system.settings.config.getInt(s"${configPath}.port")
  IO(Udp) ! Udp.Bind(self, new InetSocketAddress(address, port))

  def receive = {
    case Udp.Bound(address) =>
      log.info("UdpReceiverActor actor bound to {}", address)
      context.become(ready(sender()))
  }

  def ready(socket: ActorRef): Receive = {
    case Udp.Received(data, peer) =>
      parseMetrics(data).foreach { p =>
        metricActor ! MetricActor.Save(p)
      }
    case Udp.Unbind =>
      log.info("UdpReceiverActor received unbind request")
      socket ! Udp.Unbind
    case Udp.Unbound =>
      log.info("UdpReceiverActor stopping")
      context.stop(self)
  }
}
