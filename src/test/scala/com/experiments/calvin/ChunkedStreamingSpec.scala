package com.experiments.calvin

import java.util.UUID

import akka.actor.ActorSystem
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, StatusCodes}
import akka.http.scaladsl.testkit.ScalatestRouteTest
import akka.stream.ActorMaterializer
import com.experiments.calvin.chunked.http.ChunkedStreamingRoutes
import org.scalatest._
import org.scalatest.concurrent.ScalaFutures

import scala.concurrent.ExecutionContext
import scala.language.postfixOps

class ChunkedStreamingSpec extends FunSpec with MustMatchers with ScalatestRouteTest with ScalaFutures {

  describe("Chunked Streaming Tests") {
    val chunkedStreamingRoutes = createService()

    // Still pending: need to figure out how to not consume the entire stream, consume only 2 elements and discard
    // the rest
//    it("streams text when you hit /streaming-text") {
//      Get("/streaming-text") ~> chunkedStreamingRoutes.httpStreamingRoutes ~> check {
//        val future = response.entity.dataBytes.take(2).map(_.decodeString("UTF-8")).runFold("")(_ + _)
//        whenReady(future) {
//          result => println(result)
//        }
//      }
//    }

    it("consumes streaming JSON and sends you the count of the elements that were streamed") {
      val data = HttpEntity(
        ContentTypes.`application/json`,
        s"""
           |{"id": "c3f67175-5de8-401e-b269-7be8666340d3", "message": "hello"}
           |{"id": "c3f67175-5de8-401e-b269-7be8666340d3", "message": "hi there"}
           |{"id": "c3f67175-5de8-401e-b269-7be8666340d3", "message": "bye"}
        """.stripMargin
      )
      Post("/consuming-streaming-json", data) ~> chunkedStreamingRoutes.httpStreamingRoutes ~> check {
        status mustBe StatusCodes.OK
        responseAs[String] mustBe "3"
      }
    }
  }

  private def createService(): ChunkedStreamingRoutes = new ChunkedStreamingRoutes {
    override implicit val actorSystem: ActorSystem = ChunkedStreamingSpec.this.system
    override implicit val streamMaterializer: ActorMaterializer = ChunkedStreamingSpec.this.materializer
    override implicit val executionContext: ExecutionContext = actorSystem.dispatcher
  }
}
