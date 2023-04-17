package de.bitmarck.bms.secon.http4s

import cats.data.OptionT
import cats.effect.Async
import de.bitmarck.bms.secon.fs2.{CertLookup, DecryptVerify, IdentityLookup, SignEncrypt}
import org.http4s.HttpRoutes

class SeconDecryptMiddleware[F[_] : Async : DecryptVerify : SignEncrypt](
                                                                          identityLookup: IdentityLookup[F],
                                                                          certLookup: CertLookup[F]
                                                                        ) {
  def apply(routes: HttpRoutes[F]): HttpRoutes[F] = HttpRoutes[F] { request =>
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
  }
}
