package de.bitmarck.bms.secon.http4s

import cats.data.OptionT
import cats.effect.Async
import de.bitmarck.bms.secon.fs2.{CertLookup, DecryptVerify, IdentityLookup, SignEncrypt}
import org.http4s.{ContextRequest, ContextRoutes, HttpRoutes}

class SeconEncryptMiddleware[F[_] : Async : DecryptVerify : SignEncrypt](
                                                                          identityLookup: IdentityLookup[F],
                                                                          certLookup: CertLookup[F],
                                                                          multipart: Boolean = true
                                                                        ) {
  def context(routes: ContextRoutes[SeconMetadata, F]): HttpRoutes[F] = HttpRoutes[F] { request =>
    for {
      incomingSeconRequest <- OptionT.liftF(request.as[SeconMessage[F]])
      outgoingSeconRequest = incomingSeconRequest.signAndEncrypt(identityLookup, certLookup, request = true)
      requestEncoder = if (multipart) SeconMessage.seconMultipartEncoder else SeconMessage.seconOctetStreamEncoder
      requestMedia <- OptionT.liftF(requestEncoder.toMedia(outgoingSeconRequest))
      response <- routes(ContextRequest(outgoingSeconRequest.metadata, request.withMedia(requestMedia)))
      incomingSeconResponse <- OptionT.liftF(response.as[SeconMessage[F]])
      outgoingSeconResponse = incomingSeconResponse.decryptAndVerify(identityLookup, certLookup)
      responseMedia <- OptionT.liftF(incomingSeconRequest.encoder.toMedia(outgoingSeconResponse))
    } yield
      response.withMedia(responseMedia)
  }

  def apply(routes: HttpRoutes[F]): HttpRoutes[F] = context(ContextRoutes { case ContextRequest(_, request) =>
    routes(request)
  })
}
