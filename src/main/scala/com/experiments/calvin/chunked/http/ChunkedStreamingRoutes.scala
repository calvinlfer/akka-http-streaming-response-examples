package com.experiments.calvin.chunked.http

import java.util.UUID

import akka.actor.ActorSystem
import akka.http.scaladsl.common.EntityStreamingSupport
import akka.http.scaladsl.model.ContentTypes._
import akka.http.scaladsl.model.HttpEntity
import akka.http.scaladsl.server.Directives._
import akka.stream.actor.ActorPublisher
import akka.stream.scaladsl.{Flow, Source}
import akka.stream.{ActorMaterializer, ThrottleMode}
import akka.util.ByteString
import com.experiments.calvin.{BackpressuredActor, DetailedMessage}
import com.experiments.calvin.BackpressuredActor.{SplitString, StringHasBeenSplit}

import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration._
import scala.language.postfixOps
import scala.util.Random

trait ChunkedStreamingRoutes {
  implicit val actorSystem: ActorSystem
  implicit val streamMaterializer: ActorMaterializer
  implicit val executionContext: ExecutionContext
  lazy val httpStreamingRoutes =
    streamingTextRoute ~ actorStreamingTextRoute ~ altActorStreamingTextRoute ~ actorStreamingTextRouteWithLiveActor ~
      streamingJsonRoute ~ consumingStreamingJson

  def streamingTextRoute =
    path("streaming-text") {
      get {
        val sourceOfInformation = Source("Prepare to scroll!")
        val sourceOfNumbers = Source(1 to 1000000)
        val byteStringSource = sourceOfInformation.concat(sourceOfNumbers) // merge the two sources
          .throttle(elements = 1000, per = 1 second, maximumBurst = 1, mode = ThrottleMode.Shaping)
          .map(_.toString)
          .map(s => ByteString(s + "\n"))

        complete(HttpEntity(`text/plain(UTF-8)`, byteStringSource))
      }
    }

  // Credits:
  // http://stackoverflow.com/questions/35634283/pushing-messages-via-web-sockets-with-akka-http/
  // http://stackoverflow.com/questions/29072963/how-to-add-elements-to-source-dynamically
  def actorStreamingTextRoute =
  path("actor-text") {
    get {
      val source = Source.actorPublisher[StringHasBeenSplit](BackpressuredActor.props)
        .mapConcat(ss => ss.list)
        .map(s => ByteString(s + "\n"))
      complete(HttpEntity(`text/plain(UTF-8)`, source))
    }
  }

  def altActorStreamingTextRoute =
    path("alt-actor-text") {
      get {
        // in addition to sending events through the scheduler inside the actor
        val source = Source.actorPublisher[StringHasBeenSplit](BackpressuredActor.props)
          .map(s => ByteString(s + "\n"))
          .mapMaterializedValue(ref => {
            actorSystem.scheduler.schedule(0 seconds, 500 milliseconds, ref, SplitString("Calvin says hi"))
            ref
          })
        complete(HttpEntity(`text/plain(UTF-8)`, source))
      }
    }

  def actorStreamingTextRouteWithLiveActor =
    path("live-actor-text") {
      get {
        // create our Backpressured actor
        val actorRef = actorSystem.actorOf(BackpressuredActor.props)
        // wrap it in a ActorPublisher that will act as a Source[StringHasBeenSplit, _]
        val publisher = ActorPublisher[StringHasBeenSplit](actorRef)
        val source = Source.fromPublisher(publisher).map(s => ByteString(s + "\n"))
        // send messages into the Stream by sending messages to the actor ref will translate into downstream messages
        actorSystem.scheduler.schedule(0 seconds, 100 milliseconds, actorRef, SplitString(s"Hello! ${Random.nextString(10)}"))

        complete(HttpEntity(`text/plain(UTF-8)`, source))
      }
    }


  val start = ByteString.empty
  val sep = ByteString("\n")
  val end = ByteString.empty

  import com.experiments.calvin.DetailMessageJsonProtocol._
  implicit val jsonStreamingSupport = EntityStreamingSupport.json()
    .withFramingRenderer(Flow[ByteString].intersperse(start, sep, end))

  // More customization:
  // http://doc.akka.io/docs/akka/2.4.10/scala/http/routing-dsl/source-streaming-support.html#Customising_response_rendering_mode
  def streamingJsonRoute =
    path("streaming-json") {
      get {
        val sourceOfNumbers = Source(1 to 1000000)
        val sourceOfDetailedMessages =
          sourceOfNumbers.map(num => DetailedMessage(UUID.randomUUID(), s"Hello $num"))
            .throttle(elements = 1000, per = 1 second, maximumBurst = 1, mode = ThrottleMode.Shaping)

        complete(sourceOfDetailedMessages)
      }
    }

  def consumingStreamingJson =
    path("consuming-streaming-json") {
      post {
        entity(asSourceOf[DetailedMessage]) { detailedMessageSource =>
          val messagesSubmitted: Future[Int] = detailedMessageSource.runFold(0){(acc, _) => acc + 1}
          complete(messagesSubmitted.map(_.toString))
        }
      }
    }
}