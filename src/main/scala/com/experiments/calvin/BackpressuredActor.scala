package com.experiments.calvin

import akka.actor.{ActorLogging, Props}
import akka.stream.actor.ActorPublisher
import akka.stream.actor.ActorPublisherMessage.{Cancel, Request}
import com.experiments.calvin.BackpressuredActor._

import scala.concurrent.duration._
import scala.annotation.tailrec
import scala.language.postfixOps
import scala.util.Random

object BackpressuredActor {
  sealed trait Command
  case class SplitString(payload: String) extends Command

  sealed trait Event
  case object CommandAccepted extends Event
  case object CommandDenied extends Event
  case class StringHasBeenSplit(list: List[String]) extends Event

  def props = Props[BackpressuredActor]
}

/**
  * An actor that publishes StringHasBeenSplit messages and respects backpressure
  * This actor is meant to be created when using a Source.fromActorPublisher to allow integration between Actors
  * and Streams.
  *
  * The idea is that if you want to push some messages from your existing Actors into a Stream, you would
  * create a Publisher and feed your existing ActorRef into the Publisher and have the Publisher constantly ask your
  * Actor for messages which it emits into the Stream (this is not done here). When the Stream is cancelled, only
  * this Actor dies leaving your Actors intact without causing any disruptions. This is really an intermediate actor
  * that you use when you want to push data from your Actors into Streams.
  *
  * Credits: http://doc.akka.io/docs/akka/2.4.10/scala/stream/stream-integrations.html#ActorPublisher
  */
class BackpressuredActor extends ActorPublisher[StringHasBeenSplit] with ActorLogging {
  val MaxBufferSize = 100
  var buffer = Vector.empty[StringHasBeenSplit]
  implicit val ec = context.dispatcher
  val cancellable = context.system.scheduler.schedule(0 seconds, 100 milliseconds, self, SplitString(s"Hello! ${Random.nextString(10)}"))

  @tailrec
  private def deliverBuffer(): Unit =
    if (totalDemand > 0 && isActive) {
      // You are allowed to send as many elements as have been requested by the stream subscriber
      // total demand is a Long and can be larger than what the buffer has
      if (totalDemand <= Int.MaxValue) {
        val (sendDownstream, holdOn) = buffer.splitAt(totalDemand.toInt)
        buffer = holdOn
        // send the stuff downstream
        sendDownstream.foreach(onNext)
      } else {
        val (sendDownStream, holdOn) = buffer.splitAt(Int.MaxValue)
        buffer = holdOn
        sendDownStream.foreach(onNext)
        // recursive call checks whether is more demand before repeating the process
        deliverBuffer()
      }
    }


  override def receive: Receive = {
    case ss: SplitString if buffer.size == MaxBufferSize =>
      log.warning("received a SplitString message when the buffer is maxed out")
      sender() ! CommandDenied

    case ss: SplitString =>
      sender() ! CommandAccepted
      val result = StringHasBeenSplit(ss.payload.split("").toList)
      if (buffer.isEmpty && totalDemand > 0 && isActive) {
        // send elements to the stream immediately since there is demand from downstream and we
        // have not buffered anything so why bother buffering, send immediately
        // You send elements to the stream by calling onNext
        onNext(result)
      }
      else {
        // there is no demand from downstream so we will store the result in our buffer
        // Note that :+= means add to end of Vector
        buffer :+= result
      }

    // A request from down stream to send more data
    // When the stream subscriber requests more elements the ActorPublisherMessage.Request message is
    // delivered to this actor, and you can act on that event. The totalDemand is updated automatically.
    case Request(_) => deliverBuffer()

    // When the stream subscriber cancels the subscription the ActorPublisherMessage.Cancel message is
    // delivered to this actor. If the actor is stopped the stream will be completed, unless it was not
    // already terminated with failure, completed or canceled.
    case Cancel =>
      log.info("Stream was cancelled")
      cancellable.cancel()
      context.stop(self)

    case CommandAccepted => ()

    case other =>
      log.warning(s"Unknown message $other received")
  }
}
