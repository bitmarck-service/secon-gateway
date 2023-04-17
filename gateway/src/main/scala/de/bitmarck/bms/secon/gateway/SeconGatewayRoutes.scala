package de.bitmarck.bms.secon.gateway

import cats.data.OptionT
import cats.effect.Async
import de.bitmarck.bms.secon.fs2.{CertLookup, DecryptVerify, IdentityLookup, SignEncrypt}
import de.bitmarck.bms.secon.http4s.SeconMessage
import org.http4s.Uri.Authority
import org.http4s.headers.Host
import org.http4s.{HttpApp, HttpRoutes, HttpVersion, Request, Uri}

class SeconGatewayRoutes[F[_] : Async : DecryptVerify : SignEncrypt]
(
  identityLookup: IdentityLookup[F],
  certLookup: CertLookup[F],
  decrypt: Boolean,
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

    HttpRoutes[F] { request =>
      for {
        incomingSeconRequest <- OptionT.liftF(request.as[SeconMessage[F]])
        outgoingSeconRequest = if (decrypt) {
          incomingSeconRequest.decryptAndVerify(
            identityLookup = identityLookup,
            certLookup = certLookup
          )
        } else {
          incomingSeconRequest.signAndEncrypt(
            identityLookup = identityLookup,
            certLookup = certLookup,
            request = true
          )
        }
        requestMedia <- OptionT.liftF(SeconMessage.seconMultipartEncoder.toMedia(outgoingSeconRequest))
        response <- routes(request.withMedia(requestMedia))
        incomingSeconResponse <- OptionT.liftF(response.as[SeconMessage[F]])
        outgoingSeconResponse = if (decrypt) {
          incomingSeconRequest.signAndEncrypt(
            identityLookup = identityLookup,
            certLookup = certLookup,
            request = false
          )
        } else {
          incomingSeconResponse.decryptAndVerify(
            identityLookup = identityLookup,
            certLookup = certLookup
          )
        }
        responseMedia <- OptionT.liftF(incomingSeconRequest.encoder.toMedia(outgoingSeconResponse))
      } yield
        response.withMedia(responseMedia)
    }
  }
}
