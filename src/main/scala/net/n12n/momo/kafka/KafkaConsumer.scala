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

package net.n12n.momo.kafka

import scala.collection.JavaConversions._
import akka.actor._
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Sink, Source}
import com.softwaremill.react.kafka.KafkaMessages.StringKafkaMessage
import com.softwaremill.react.kafka.{ConsumerProperties, ReactiveKafka}
import kafka.serializer.StringDecoder
import net.n12n.momo.couchbase.{MetricActor, MetricPoint}
import org.reactivestreams.Publisher
import spray.json._

object KafkaConsumer {
  def props(metricActor: ActorSelection) = Props(classOf[KafkaConsumer], metricActor)
  case object StreamEnd
}

/**
 * Consume messages from Kafka.
 */
class KafkaConsumer(metricActor: ActorSelection) extends Actor with ActorLogging {
  import KafkaConsumer._

  val kafka = new ReactiveKafka()
  val consumerProperties = ConsumerProperties(
    brokerList = context.system.settings.
      config.getStringList("kafka.broker-list").mkString(","),
    zooKeeperHost = context.system.settings.
      config.getStringList("kafka.zookeeper-list").mkString(","),
    topic = context.system.settings.config.getString("kafka.topic"),
    groupId = context.system.settings.config.getString("kafka.group-id"),
    decoder = new StringDecoder()).readFromEndOfStream()
  val publisher: Publisher[StringKafkaMessage] = kafka.consume(consumerProperties)(context.system)

  override def preStart(): Unit = {
    Source(publisher).map(_.message().parseJson.convertTo[Metric20Point]).to(
      Sink.actorRef(self, StreamEnd)).run()(ActorMaterializer())
    log.info("Connected to Kafka {}, {}", consumerProperties.brokerList,
      consumerProperties.zookeeperConnect)
  }

  override def receive = {
    case StreamEnd =>
      log.warning("Kafka stream ended!")
    case Metric20Point(timestamp, value, tags) =>
      metricActor ! MetricActor.Save(MetricPoint(tags("path"), timestamp * 1000,
        value.toLong))
    case Status.Failure(t) =>
      log.error(t, "Connection to Kafka broken")
    case msg =>
      log.error("Unexpected message {}", msg)
  }
}
