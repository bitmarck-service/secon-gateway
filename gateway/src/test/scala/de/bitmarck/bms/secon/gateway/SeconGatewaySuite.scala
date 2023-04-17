package de.bitmarck.bms.secon.gateway

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import de.bitmarck.bms.secon.fs2.*
import de.bitmarck.bms.secon.http4s.SeconMetadata
import org.http4s.implicits.uri
import org.http4s.{HttpRoutes, Method, Request, Response, Status}

import scala.concurrent.duration.{Duration, DurationInt}

class SeconGatewaySuite extends CatsEffectSuite {
  private given SignEncrypt[IO] = SignEncrypt.make[IO]()

  private given DecryptVerify[IO] = DecryptVerify.make[IO]()

  private val keyStore = fs2.io.readClassLoaderResource[IO]("keystore.p12").through(loadKeyStore("secret".toCharArray)).compile.lastOrError.unsafeRunSync()
  private val identityLookup = IdentityLookup.fromKeyStore[IO](keyStore, "secret".toCharArray)
  private val certLookup = CertLookup.fromIdentityLookup(identityLookup)
  private val alice = "alice_rsa_256"
  private val bob = "bob_rsa_256"

  private val seconGatewayServerRoutes = new SeconGatewayRoutes[IO](
    identityLookup = identityLookup,
    certLookup = certLookup,
    server = true,
    uri = uri"http://example.com",
    httpApp = HttpRoutes.of[IO] {
      case request =>
        request.as[String].map { string =>
          Response(status = Status.Ok).withEntity(s"Hello $string!")
        }
    }.orNotFound
  ).toRoutes

  private val seconGatewayClientRoutes = new SeconGatewayRoutes[IO](
    identityLookup = identityLookup,
    certLookup = certLookup,
    server = false,
    uri = uri"http://example.com",
    httpApp = seconGatewayServerRoutes.orNotFound
  ).toRoutes

  test("round trip") {
    seconGatewayClientRoutes {
      Request(
        method = Method.POST
      ).putHeaders(
        SeconMetadata(
          sender = alice,
          empfaenger = bob,
          verfahren = None
        ).toHeaders
      ).withEntity("World")
    }.value.flatMap { response =>
      response.get.as[String].map { string =>
        println(string)
        assertEquals(string, "Hello World!")
      }
    }
  }
}
