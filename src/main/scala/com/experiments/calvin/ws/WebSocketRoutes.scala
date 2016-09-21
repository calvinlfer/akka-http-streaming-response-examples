package com.experiments.calvin.ws

import akka.NotUsed
import akka.actor.{ActorSystem, PoisonPill, Props}
import akka.http.scaladsl.model.ws.{Message, TextMessage}
import akka.http.scaladsl.server.Directives._
import akka.stream.OverflowStrategy
import akka.stream.scaladsl.{Flow, Sink, Source}

import scala.language.postfixOps

/**
  * WebSocket routes
  * Note: if you do not send anything for over 1 minute then the connection will be closed
  */
trait WebSocketRoutes {
  val actorSystem: ActorSystem
  lazy val chatRoom = actorSystem.actorOf(Props[ChatRoom], "chat-room")
  lazy val wsRoutes = websocketRoute ~ wsChatRoute

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

  private def newUser(): Flow[Message, Message, NotUsed] = {
    // create a user actor per webSocket connection that is able to talk to the chat room
    val connectedWsActor = actorSystem.actorOf(ConnectedUser.props(chatRoom))

    // Think about this differently - in the normal case for a stateless Flow, we have incoming messages coming from
    // the WS client and outgoing messages going to the WS client
    // Here incomingMessages appears to be modelled as a Sink, the idea is that any messages being sent on a Source
    // connected to this Sink need to go to an actor (the Source being the WebSocket client)
    val incomingMessages: Sink[Message, NotUsed] =
      Flow[Message].map {
        case TextMessage.Strict(text) => ConnectedUser.IncomingMessage(text)
      }.to(Sink.actorRef(connectedWsActor, PoisonPill))

    // Here outgoingMessages appear to be modelled as a Source, the idea is that any messages being sent by the actor
    // (representing the WS client) needs to be sent to the actual WS client over the websocket (being the Sink)
    val outgoingMessages: Source[Message, NotUsed] =
      Source
        .actorRef[ConnectedUser.OutgoingMessage](10, OverflowStrategy.fail)
        .mapMaterializedValue { outgoingActor =>
          // you need to send a Connected message to get the actor in a state
          // where it's ready to receive and send messages, we used the mapMaterialized value
          // so we can get access to it as soon as this is materialized
          connectedWsActor ! ConnectedUser.Connected(outgoingActor)
          NotUsed
        }
        .map {
          // Domain Model => WebSocket Message
          case ConnectedUser.OutgoingMessage(text) => TextMessage(text)
        }

    /*
    We create a flow from the Sink and Source - it's this that makes sense because here incoming messages are coming
    from the WS Client and being sent to the WS Actor and eventually the chat room and the out going messages that are
    coming from the WS actor are being sent over the WebSocket this follows the same idea as the stateless Flow
    (incoming from WebSocket client -> outgoing to WebSocket client)

    Above, we see that messages coming into the flow are received by the Sink and we use actors to emit messages to
    the source

    To me, the inside of a Flow looks like this:
                                                                                                                      Flow
                                                    ______________________________________________________________________________________________________________________________________
                                                   |                                                                                                                                      |
     WebSocket Source   ->->->->->->->->->->->->->-| Sink (WebSocket Actor sending message to ConnectedUserActor)           Source (ConnectedUserActor sending message to WebSocket Actor |->->->->->->->->->->->-> WebSocket Sink
                                                   |                                                                                                                                      |
                                                   | Accomplished using Sink.actorRef, any information emitted on           Accomplished using Source.actorRef, any OutgoingMessage sent  |
                                                   | the Stream will end up at the ConnectedUserActor                       to the materialized Actor will be emitted on the Stream       |
    (incoming messages)                            |______________________________________________________________________________________________________________________________________|                        (outgoing messages)
     */
    Flow.fromSinkAndSource(incomingMessages, outgoingMessages)
  }

  // Credits: http://markatta.com/codemonkey/blog/2016/04/18/chat-with-akka-http-websockets/
  def wsChatRoute =
    path("ws-chat") {
      handleWebSocketMessages(newUser())
    }
}
