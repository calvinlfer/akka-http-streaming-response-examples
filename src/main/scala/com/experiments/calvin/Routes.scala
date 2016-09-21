package com.experiments.calvin

import akka.http.scaladsl.model.ContentTypes._
import akka.http.scaladsl.model.HttpEntity
import akka.http.scaladsl.model.ws.{Message, TextMessage}
import akka.http.scaladsl.server.Directives._
import akka.stream.scaladsl.{Flow, Sink, Source}
import akka.stream.{ActorMaterializer, ThrottleMode}
import akka.util.ByteString
import com.experiments.calvin.BackpressuredActor.StringHasBeenSplit

import scala.concurrent.duration._
import scala.language.postfixOps

trait Routes {
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

  def actorStreamingTextRoute =
    path("actor-text") {
      get {
        val source = Source.actorPublisher[StringHasBeenSplit](BackpressuredActor.props)
          .mapConcat(ss => ss.list)
          .map(s => ByteString(s + "\n"))

        val actorRef = Flow[StringHasBeenSplit]
          .mapConcat(ss => ss.list)
          .map(s => ByteString(s + "\n"))
          .to(Sink.ignore)
          .runWith(Source.actorPublisher[StringHasBeenSplit](BackpressuredActor.props))

        // You cannot actually do this, in order for this to work, you need to have a Source
        // Yes, we do have a Source but nothing will ever be published since it requires us to connect it to a Sink
        // to get a Runnable Graph and then we can materialize it and get the actorRef and when we send messages to
        // the Actor Ref, then messages show up but as of now, this does not work
        // It will just show nothing, because we can't put any messages in the Stream
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
