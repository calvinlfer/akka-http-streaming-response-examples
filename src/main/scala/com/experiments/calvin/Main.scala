package com.experiments.calvin

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.http.scaladsl.Http

object Main extends App with Routes {
  implicit val actorSystem = ActorSystem(name = "example-actor-system")
  implicit val streamMaterializer = ActorMaterializer()
  implicit val executionContext = actorSystem.dispatcher
  val log = actorSystem.log

  val bindingFuture = Http().bindAndHandle(allRoutes, "localhost", 9000)
  bindingFuture
    .map(_.localAddress)
    .map(addr => s"Bound to $addr")
    .foreach(log.info)
}
