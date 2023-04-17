package de.bitmarck.bms.secon.gateway

import org.http4s.Uri.Authority
import org.http4s.headers.Host
import org.http4s.{Media, Message, Request, Uri}

extension (self: Host.type) {
  def fromAuthority(authority: Authority): Host =
    Host(authority.host.value, authority.port)
}

extension[F[_]] (self: Request[F]) {
  def withDestination(destination: Uri): Request[F] =
    self
      .withUri(destination)
      .putHeaders(destination.authority.fold(Host(""))(Host.fromAuthority))
}

extension (self: Uri) {
  def withSchemeAndAuthority(uri: Uri): Uri = self.copy(
    scheme = uri.scheme,
    authority = uri.authority
  )
}

extension[F[_]] (self: Message[F]) {
  def withMedia(media: Media[F]): self.Self =
    self
      .putHeaders(media.headers)
      .withEntity(media.body)
}
