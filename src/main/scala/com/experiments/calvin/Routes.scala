package com.experiments.calvin

import akka.http.scaladsl.model.ContentTypes._
import akka.http.scaladsl.model.HttpEntity
import akka.http.scaladsl.server.Directives._
import akka.stream.ThrottleMode
import akka.stream.scaladsl.Source
import akka.util.ByteString

import scala.concurrent.duration._
import scala.language.postfixOps

trait Routes {
  val allRoutes = streamingTextRoute

  def streamingTextRoute =
    path("streaming-text") {
      get {
        val sourceOfInformation = Source("Prepare to scroll!")
        val sourceOfNumbers = Source(1 to 1000000)
        val byteStringSource = sourceOfInformation.concat(sourceOfNumbers)   // merge the two sources
            .throttle(elements = 1000, per = 1 second, maximumBurst = 1, mode = ThrottleMode.Shaping)
            .map(_.toString)
            .map(s => ByteString(s + "\n"))

        complete(HttpEntity(`text/plain(UTF-8)`, byteStringSource))
      }
    }
}
