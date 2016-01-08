package net.n12n.momo

import java.net.{InetAddress, DatagramPacket, DatagramSocket}
import com.typesafe.config.ConfigFactory

import scala.concurrent.duration._

import akka.actor.{Props, ActorSelection, ActorSystem}
import akka.testkit._
import net.n12n.momo.couchbase.{CouchbaseActor, MetricActor}
import org.scalatest.{BeforeAndAfterAll, ShouldMatchers, FlatSpecLike}

object UdpReceiverActorSpec {
  val config = ConfigFactory.parseString(
    """
      |akka.loggers = ["akka.testkit.TestEventListener"]
    """.stripMargin)
}

class UdpReceiverActorSpec extends TestKit(ActorSystem("UdpReceiverActorSpec",
  config = UdpReceiverActorSpec.config))
with FlatSpecLike with ShouldMatchers with BeforeAndAfterAll {
  val statsdData =
    """kamon.nelson.akka-actor.momo_user_db_metric_$c.mailbox-size:0|ms|@0.00320
      |kamon.nelson.akka-actor.momo_user_db_metric_$c.processing-time:133120|ms
      |kamon.nelson.akka-actor.momo_user_db_metric_$c.time-in-mailbox:28416|ms
      |kamon.nelson.akka-actor.momo_user_db_metric_$c.errors:0|c""".stripMargin

  val graphiteData = Array(
    "kamon.nelson.akka-actor.momo_user_db_metric_$c.mailbox-size 0 1443635353",
    "kamon.nelson.akka-actor.momo_user_db_metric_$c.processing-time 133120 1443635353",
    "kamon.nelson.akka-actor.momo_user_db_metric_$c.time-in-mailbox 28416 1443635353",
    "kamon.nelson.akka-actor.momo_user_db_metric_$c.errors 0 1443635353")

  "UdpReceiverActor" should "process StatsD metrics" in {
    EventFilter.info(
      message = "UdpReceiverActor actor bound to /0:0:0:0:0:0:0:0:8125",
      occurrences = 1).intercept {
      TestActorRef(ReceiverActor.propsStatsD(
        ActorSelection(testActor, Seq())))
    }

    val port = system.settings.config.getInt("momo.statsd.port")
    val socket = new DatagramSocket()
    val data = statsdData.getBytes("utf-8")
    val datagram = new DatagramPacket(data, data.length,
      InetAddress.getLoopbackAddress, port)
    socket.send(datagram)


    val points = receiveN(4, 1 second)
    points.size should be(4)
    socket.close()
  }

  "UdpReceiverActor" should "process Graphite UDP metrics" in {
    EventFilter.info(
      message = "UdpReceiverActor actor bound to /0:0:0:0:0:0:0:0:2003",
      occurrences = 1).intercept {
      TestActorRef(ReceiverActor.propsUdpGraphite(
        ActorSelection(testActor, Seq())))
    }

    val port = system.settings.config.getInt("momo.graphite-udp.port")
    val socket = new DatagramSocket()
    for (msg <- graphiteData) {
      val data = msg.getBytes("utf-8")
      val datagram = new DatagramPacket(data, data.length,
        InetAddress.getLoopbackAddress, port)
      socket.send(datagram)
    }

    val points = receiveN(4, 1 second)
    points.size should be(4)
    socket.close()

  }

  override protected def afterAll(): Unit = shutdown()
}
