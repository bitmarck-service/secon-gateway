package de.bitmarck.bms.secon.http4s

import org.http4s.Header
import org.http4s.headers.`Content-Type`
import org.typelevel.ci._

object SeconHeaders {
  case class SeconContentType(contentType: `Content-Type`)

  object SeconContentType {
    implicit val headerInstance: Header[SeconContentType, Header.Single] =
      Header.createRendered(
        ci"SECON-Content-Type",
        e => `Content-Type`.headerInstance.value(e.contentType),
        `Content-Type`.headerInstance.parse(_).map(SeconContentType(_)),
      )
  }

  case class SeconSender(string: String)

  object SeconSender {
    implicit val headerInstance: Header[SeconSender, Header.Single] =
      Header.createRendered(
        ci"SECON-Sender",
        e => e.string,
        e => Right(SeconSender(e))
      )
  }

  case class SeconEmpfaenger(string: String)

  object SeconEmpfaenger {
    implicit val headerInstance: Header[SeconEmpfaenger, Header.Single] =
      Header.createRendered(
        ci"SECON-Empfaenger",
        e => e.string,
        e => Right(SeconEmpfaenger(e))
      )
  }

  case class SeconVerfahren(string: String)

  object SeconVerfahren {
    implicit val headerInstance: Header[SeconVerfahren, Header.Single] =
      Header.createRendered(
        ci"SECON-Verfahren",
        e => e.string,
        e => Right(SeconVerfahren(e))
      )
  }
}
