package com.experiments.calvin

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.stream.ActorMaterializer
import akka.http.scaladsl.server.Directives._
import com.experiments.calvin.chunked.http.ChunkedStreamingRoutes
import com.experiments.calvin.ws.WebSocketRoutes

object ServerMain extends App with ChunkedStreamingRoutes with WebSocketRoutes {
  implicit val actorSystem = ActorSystem(name = "example-actor-system")
  implicit val streamMaterializer = ActorMaterializer()
  implicit val executionContext = actorSystem.dispatcher
  val log = actorSystem.log
  val allRoutes = httpStreamingRoutes ~ wsRoutes

  val bindingFuture = Http().bindAndHandle(allRoutes, "localhost", 9000)
  bindingFuture
    .map(_.localAddress)
    .map(addr => s"Bound to $addr")
    .foreach(log.info)
}
