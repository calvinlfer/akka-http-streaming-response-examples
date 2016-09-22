package com.experiments.calvin

import java.util.UUID

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import spray.json.{DefaultJsonProtocol, JsString, JsValue, RootJsonFormat}

case class DetailedMessage(id: UUID, message: String)

object DetailMessageJsonProtocol extends SprayJsonSupport with DefaultJsonProtocol {
  implicit object UUIDJsonFormat extends RootJsonFormat[UUID] {
    override def read(json: JsValue): UUID = UUID.fromString(json.convertTo[String])
    override def write(uuid: UUID): JsValue = JsString(uuid.toString)
  }

  implicit val detailedMessageFormat = jsonFormat2(DetailedMessage.apply)
}
