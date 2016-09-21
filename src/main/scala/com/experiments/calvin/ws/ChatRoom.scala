package com.experiments.calvin.ws

import akka.actor.{Actor, ActorLogging, ActorRef, Terminated}
import com.experiments.calvin.ws.ChatRoom.{ChatMessage, JoinRoom}

/**
  * An actor that represents a chat hub to which all messages that come over
  * websockets will be sent. This is essentially broadcasting any incoming messages
  * to everyone who is connected
  */
class ChatRoom extends Actor with ActorLogging {
  var users: Set[ActorRef] = Set.empty

  override def receive: Receive = {
    case JoinRoom =>
      log.info(s"Received a JoinRoom message from ${sender()}")
      users += sender()
      // we want to remove the user if it's actor is stopped
      context.watch(sender())

    case cm: ChatMessage =>
      log.info(s"Received a ChatMessage message from ${sender()}")
      users.foreach(_ ! cm)

    case Terminated(user) =>
      log.info(s"${sender()} is leaving")
      users -= user
  }
}

object ChatRoom {
  case object JoinRoom
  case class ChatMessage(message: String)
}
