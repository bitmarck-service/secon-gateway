package de.bitmarck.bms.secon.gateway

import cats.data.OptionT
import cats.effect.Async
import de.bitmarck.bms.secon.fs2.{CertLookup, DecryptVerify, IdentityLookup, SignEncrypt}
import de.bitmarck.bms.secon.http4s.{SeconDecryptMiddleware, SeconEncryptMiddleware, SeconMessage}
import org.http4s.Uri.Authority
import org.http4s.headers.Host
import org.http4s.{HttpApp, HttpRoutes, HttpVersion, Request, Uri}

class SeconGatewayRoutes[F[_] : Async : DecryptVerify : SignEncrypt]
(
  identityLookup: IdentityLookup[F],
  certLookup: CertLookup[F],
  server: Boolean,
  multipart: Boolean,
  uri: Uri,
  httpApp: HttpApp[F]
) {
  val toRoutes: HttpRoutes[F] = {
    def routes: HttpRoutes[F] = HttpRoutes[F] { request =>
      OptionT.liftF(httpApp(
        request
          .withHttpVersion(HttpVersion.`HTTP/1.1`)
          .withDestination(
            request.uri
              .withSchemeAndAuthority(uri)
              .withPath({
                if (request.pathInfo.isEmpty) uri.path
                else uri.path.concat(request.pathInfo)
              }.toAbsolute)
          )
      ))
    }

    if (server) {
      new SeconDecryptMiddleware[F](identityLookup, certLookup)
        .apply(routes)
    } else {
      new SeconEncryptMiddleware[F](identityLookup, certLookup, multipart = multipart)
        .apply(routes)
    }
  }
}
