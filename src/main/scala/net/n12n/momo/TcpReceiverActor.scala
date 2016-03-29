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
import akka.io.{Tcp, IO}
import net.n12n.momo.couchbase.MetricActor

class TcpReceiverActor(metricActor: ActorRef, configPath: String,
                       val parseMetric: ReceiverActor.MetricParser)
  extends Actor with ActorLogging {
  import Tcp._
  import context.system
  val address = system.settings.config.getString(s"${configPath}.listen-address")
  val port = system.settings.config.getInt(s"${configPath}.port")
  IO(Tcp) ! Tcp.Bind(self, new InetSocketAddress(address, port))
  log.info("Binding to TCP {}:{}", address, port)

  def receive = {
    case Bound(address) =>
      log.info("TcpReceiverActor actor bound to {}", address)
    case Connected(remote, local) =>
      log.info("TcpReceiver got connection form {}", remote)
      val handler = context.actorOf(Props(classOf[TcpConnectionActor],
        metricActor, parseMetric))
      sender() ! Register(handler)
  }
}

class TcpConnectionActor(metricActor: ActorRef,
                         val parseMetric: ReceiverActor.MetricParser)
  extends Actor with ActorLogging with ReceiverActor {
  import Tcp._

  override def receive = {
    case Received(data) => parseMetrics(data).foreach { p =>
      metricActor ! MetricActor.Save(p)
    }
    case PeerClosed => context stop self
  }
}
