package com.experiments.calvin.chunked.http

import akka.actor.ActorSystem
import akka.http.scaladsl.model.ContentTypes._
import akka.http.scaladsl.model.HttpEntity
import akka.http.scaladsl.server.Directives._
import akka.stream.actor.ActorPublisher
import akka.stream.scaladsl.Source
import akka.stream.{ActorMaterializer, ThrottleMode}
import akka.util.ByteString
import com.experiments.calvin.BackpressuredActor
import com.experiments.calvin.BackpressuredActor.{SplitString, StringHasBeenSplit}

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._
import scala.util.Random

trait ChunkedStreamingRoutes {
  implicit val actorSystem: ActorSystem
  implicit val executionContext: ExecutionContext
  implicit val streamMaterializer: ActorMaterializer
  val httpStreamingRoutes = streamingTextRoute ~ actorStreamingTextRoute ~ altActorStreamingTextRoute ~ actorStreamingTextRouteWithLiveActor

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
}