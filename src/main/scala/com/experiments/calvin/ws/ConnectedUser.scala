package com.experiments.calvin.ws
import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import com.experiments.calvin.ws.ChatRoom.{ChatMessage, JoinRoom}
import com.experiments.calvin.ws.ConnectedUser.{Connected, IncomingMessage, OutgoingMessage}

/**
  * This actor represents a connected user over a websocket connection and acts as a bridge between the WS Actor and
  * the Chat Room. It is responsible for managing incoming messages from the WS actor and messaging being delivered
  * from the Chat Room to the WS actor
  */
class ConnectedUser(chatRoom: ActorRef) extends Actor with ActorLogging {
  override def receive: Receive = waiting

  def waiting: Receive = {
    // When the user connects, tell the chat room about it so messages
    // sent to the chat room are routed here
    case Connected(outgoing) =>
      log.info(s"WS user: $outgoing has connected")
      context become connected(outgoing)
      chatRoom ! JoinRoom
  }

  def connected(wsUser: ActorRef): Receive = {
    // any messages coming from the user on the websocket will be sent to the chat room
    case IncomingMessage(text) =>
      chatRoom ! ChatMessage(text)

    // any messages coming from the chat room need to be sent to the websocket user
    case ChatMessage(message) =>
      wsUser ! OutgoingMessage(message)
  }
}

object ConnectedUser {
  sealed trait UserMessage
  case class Connected(outgoing: ActorRef)
  case class IncomingMessage(text: String) extends UserMessage
  case class OutgoingMessage(text: String) extends UserMessage

  def props(chatRoom: ActorRef) = Props(new ConnectedUser(chatRoom))
}