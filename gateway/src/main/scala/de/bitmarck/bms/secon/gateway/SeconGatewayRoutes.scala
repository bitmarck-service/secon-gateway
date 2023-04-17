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

    if (server) HttpRoutes[F] { request =>
      for {
        incomingSeconRequest <- OptionT.liftF(request.as[SeconMessage[F]])
        outgoingSeconRequest = incomingSeconRequest.decryptAndVerify(identityLookup, certLookup)
        requestMedia <- OptionT.liftF(SeconMessage.seconOctetStreamEncoder.toMedia(outgoingSeconRequest))
        response <- routes(request.withMedia(requestMedia))
        incomingSeconResponse = outgoingSeconRequest.withMedia(response)
        outgoingSeconResponse = incomingSeconResponse.signAndEncrypt(identityLookup, certLookup, request = false)
        responseMedia <- OptionT.liftF(incomingSeconRequest.encoder.toMedia(outgoingSeconResponse))
      } yield
        response.withMedia(responseMedia)
    } else HttpRoutes[F] { request =>
      for {
        incomingSeconRequest <- OptionT.liftF(request.as[SeconMessage[F]])
        outgoingSeconRequest = incomingSeconRequest.signAndEncrypt(identityLookup, certLookup, request = true)
        requestEncoder = if (multipart) SeconMessage.seconMultipartEncoder else SeconMessage.seconOctetStreamEncoder
        requestMedia <- OptionT.liftF(requestEncoder.toMedia(outgoingSeconRequest))
        response <- routes(request.withMedia(requestMedia))
        incomingSeconResponse <- OptionT.liftF(response.as[SeconMessage[F]])
        outgoingSeconResponse = incomingSeconResponse.decryptAndVerify(identityLookup, certLookup)
        responseMedia <- OptionT.liftF(incomingSeconRequest.encoder.toMedia(outgoingSeconResponse))
      } yield
        response.withMedia(responseMedia)
    }
  }
}
