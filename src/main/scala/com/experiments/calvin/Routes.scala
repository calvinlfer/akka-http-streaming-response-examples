package com.experiments.calvin

import akka.actor.ActorSystem
import akka.http.scaladsl.model.ContentTypes._
import akka.http.scaladsl.model.HttpEntity
import akka.http.scaladsl.model.ws.{Message, TextMessage}
import akka.http.scaladsl.server.Directives._
import akka.stream.actor.ActorPublisher
import akka.stream.scaladsl.{Flow, Source}
import akka.stream.{ActorMaterializer, ThrottleMode}
import akka.util.ByteString
import com.experiments.calvin.BackpressuredActor.{SplitString, StringHasBeenSplit}

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._
import scala.language.postfixOps
import scala.util.Random

trait Routes {
  implicit val actorSystem: ActorSystem
  implicit val executionContext: ExecutionContext
  implicit val streamMaterializer: ActorMaterializer
  val allRoutes = streamingTextRoute ~ actorStreamingTextRoute ~ websocketRoute

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
      // create our Backpressured actor
      val actorRef = actorSystem.actorOf(BackpressuredActor.props)
      // wrap it in a ActorPublisher that will act as a Source[StringHasBeenSplit, _]
      val publisher = ActorPublisher[StringHasBeenSplit](actorRef)
      val source = Source.fromPublisher(publisher).mapConcat(ss => ss.list).map(s => ByteString(s + "\n"))
      // send messages into the Stream by sending messages to the actor ref will translate into downstream messages
      actorSystem.scheduler.schedule(0 seconds, 100 milliseconds, actorRef, SplitString(s"Hello! ${Random.nextString(10)}"))

      complete(HttpEntity(`text/plain(UTF-8)`, source))
    }
  }

  // Note: see http://blog.scalac.io/2015/07/30/websockets-server-with-akka-http.html for something way more complex
  def websocketRoute =
  path("ws-simple") {
    get {
      val echoFlow: Flow[Message, Message, _] = Flow[Message].map {
        case TextMessage.Strict(text) => TextMessage(s"I got your message: $text!")
        case _ => TextMessage(s"Sorry I didn't quite get that")
      }
      handleWebSocketMessages(echoFlow)
    }
  }
}
