package com.experiments.calvin.ws
import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import com.experiments.calvin.ws.ChatRoom.{ChatMessage, JoinRoom}
import com.experiments.calvin.ws.ConnectedUser.{Connected, IncomingMessage, OutgoingMessage}

/**
  * This actor represents a connected user over a WebSocket connection and acts as a bridge between the WS Actor and
  * the Chat Room. It is responsible for managing incoming messages from the WS actor and messaging being delivered
  * from the Chat Room to the WS actor
  *
  * Additional Notes:
  * This actor is the direct recipient of the Sink inside the WebSocket Flow. This actor will get a PoisonPill when
  * the WebSocket Client (sending portion) terminates the Stream
  * This actor behaves as an intermediary only when it comes to sending messages to the WS client, The BackPressured
  * Actor Publisher is the direct integration point for publishing messages into the Stream. We obtain an ActorRef to
  * that Actor Publisher when that portion of the Stream is materialized via the mapMaterializedValue. Both this actor
  * and the BackPressured Actor Publisher live for the duration of a connected WS Client
  */
class ConnectedUser(chatRoom: ActorRef) extends Actor with ActorLogging {
  override def receive: Receive = waiting

  def waiting: Receive = {
    // When the user connects, tell the chat room about it so messages
    // sent to the chat room are routed here
    case Connected(wsActor) =>
      log.info(s"WS user: $wsActor has connected")
      context become connected(wsActor)
      chatRoom ! JoinRoom
  }

  def connected(wsUser: ActorRef): Receive = {
    // any messages coming from the WS client will come here and will be sent to the chat room
    case IncomingMessage(text) =>
      log.debug("Intermediate Actor sending message to chat room")
      chatRoom ! ChatMessage(text)

    // any messages coming from the chat room need to be sent to the WS Client
    // remember that in this case we are the intermediate bridge and we have to send the message to the ActorPublisher
    // in order for the WS client to receive the message
    case ChatMessage(message) =>
      log.debug(s"Intermediate Actor sending message that came from the chat room to $wsUser")
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