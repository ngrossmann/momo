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

import akka.actor.{Actor, ActorLogging, Props}
import akka.io.IO
import net.n12n.momo.couchbase.CouchbaseActor
import spray.can.Http

/**
  * Root actor coordinating others.
  */
class RootActor extends Actor with ActorLogging {
  import CouchbaseActor._
  import context.system
  val config = context.system.settings.config
  val address = config.getString("momo.http.listen-address")
  val port = config.getInt("momo.http.port")
  val couchbaseActor = context.actorOf(Props[CouchbaseActor], "db")
  var couchbaseServices: Option[CouchbaseServices] = None

  override def receive = {
    case services: CouchbaseServices =>
      couchbaseServices = Some(services)
      val webServiceActor = context.actorOf(MomoWebServiceActor.props(services))
      log.info("Binding Momo HTTP service at {}:{}", address, port)
      IO(Http) ! Http.Bind(webServiceActor, address, port = port)
      if (config.getBoolean("momo.statsd.enabled"))
        context.actorOf(ReceiverActor.propsStatsD(
          services.metricActor), "statsd")
      if (config.getBoolean("momo.graphite-tcp.enabled"))
        context.actorOf(ReceiverActor.propsTcpGraphite(
          services.metricActor), "graphite-tcp")
      if (config.getBoolean("momo.graphite-udp.enabled"))
        context.actorOf(ReceiverActor.propsUdpGraphite(
          services.metricActor), "graphite-udp")
  }
}
