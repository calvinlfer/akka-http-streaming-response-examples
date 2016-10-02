package com.experiments.calvin

import akka.NotUsed
import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.ws.{Message, TextMessage}
import akka.http.scaladsl.server.Directives._
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{BroadcastHub, Flow, Keep, MergeHub, Sink, Source}

import scala.concurrent.Future

/**
  * A much simpler WebSocket chat system using only Akka Streams with the help of MergeHubSource and BroadcastHub Sink
  * Credits: https://markatta.com/codemonkey/blog/2016/10/02/chat-with-akka-http-websockets/
  */
object WebsocketStreamsMain extends App {
  implicit val actorSystem = ActorSystem(name = "example-actor-system")
  implicit val streamMaterializer = ActorMaterializer()
  implicit val executionContext = actorSystem.dispatcher
  val log = actorSystem.log

  /*
  many clients -> Merge Hub -> Broadcast Hub -> many clients
  Visually
                                                                                                         Akka Streams Flow
                  ________________________________________________________________________________________________________________________________________________________________________________________
  c1 -------->\  |                                                                                                                                                                                        |  /->----------- c1
               \ |                                                                                                                                                                                        | /
  c2 ----------->| Sink ========================(feeds data to)===========> MergeHub Source ->-->-->--> BroadcastHub Sink ======(feeds data to)===========> Source                                        |->->------------ c2
                /| that comes from materializing the                                        connected to                                                    that comes from materializing the             | \
               / | MergeHub Source                                                                                                                          BroadcastHub Sink                             |  \
  c3 -------->/  |________________________________________________________________________________________________________________________________________________________________________________________|   \->---------- c3


  Runnable Flow (MergeHubSource -> BroadcastHubSink)

  Materializing a MergeHub Source yields a Sink that collects all the emitted elements and emits them in the MergeHub Source (the emitted elements that are collected in the Sink are coming from all WebSocket clients)
  Materializing a BroadcastHub Sink yields a Source that broadcasts all elements being collected by the MergeHub Sink (the elements that are emitted/broadcasted in the Source are going to all WebSocket clients)

  This example relies on a materializer
   */
  val (chatSink: Sink[String, NotUsed], chatSource: Source[String, NotUsed]) =
  MergeHub.source[String].toMat(BroadcastHub.sink[String])(Keep.both).run()

  private val userFlow: Flow[Message, Message, NotUsed] =
    Flow[Message].mapAsync(1) {
      case TextMessage.Strict(text) => Future.successful(text)
      case streamed: TextMessage.Streamed => streamed.textStream.runFold("") {
        (acc, next) => acc ++ next
      }
    }
    .via(Flow.fromSinkAndSource(chatSink, chatSource))
    .map[Message](string => TextMessage.Strict(string))

  def wsChatStreamsOnlyRoute =
    path("ws-chat-streams-only") {
      handleWebSocketMessages(userFlow)
    }

  val bindingFuture = Http().bindAndHandle(wsChatStreamsOnlyRoute, "localhost", 9000)
  bindingFuture
    .map(_.localAddress)
    .map(addr => s"Bound to $addr")
    .foreach(log.info)
}
