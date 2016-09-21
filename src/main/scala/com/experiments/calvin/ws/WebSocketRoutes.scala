package com.experiments.calvin.ws

import akka.http.scaladsl.model.ws.{Message, TextMessage}
import akka.http.scaladsl.server.Directives._
import akka.stream.scaladsl.Flow

import scala.language.postfixOps

trait WebSocketRoutes {
  val wsRoutes = websocketRoute

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
