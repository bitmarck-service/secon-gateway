package de.bitmarck.bms.secon

import org.http4s.{Media, _}

package object http4s {
  implicit class RequestOps[F[_]](val self: Request[F]) extends AnyVal {
    def withMedia(media: Media[F]): self.Self =
      self
        .transformHeaders(_.removePayloadHeaders)
        .putHeaders(media.headers)
        .withEntity(media.body)
  }

  implicit class ResponseOps[F[_]](val self: Response[F]) extends AnyVal {
    def withMedia(media: Media[F]): self.Self =
      self
        .transformHeaders(_.removePayloadHeaders)
        .putHeaders(media.headers)
        .withEntity(media.body)
  }
}
