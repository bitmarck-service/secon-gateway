package de.bitmarck.bms.secon.http4s

import io.circe.Codec
import io.circe.generic.semiauto._
import org.http4s.Headers

case class SeconMetadata(
                          sender: String,
                          empfaenger: String,
                          verfahren: Option[String],
                        ) {
  final def toHeaders: Headers = Headers(
    SeconHeaders.SeconSender(sender),
    SeconHeaders.SeconEmpfaenger(empfaenger),
    verfahren.map(SeconHeaders.SeconVerfahren(_))
  )
}

object SeconMetadata {
  implicit val codec: Codec[SeconMetadata] = deriveCodec

  def fromHeaders(headers: Headers): Option[SeconMetadata] =
    for {
      sender <- headers.get[SeconHeaders.SeconSender]
      empfaenger <- headers.get[SeconHeaders.SeconEmpfaenger]
      verfahren = headers.get[SeconHeaders.SeconVerfahren]
    } yield SeconMetadata(
      sender = sender.string,
      empfaenger = empfaenger.string,
      verfahren = verfahren.map(_.string)
    )
}
