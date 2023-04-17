package de.bitmarck.bms.secon.gateway

import cats.effect.*
import cats.effect.std.Env
import cats.kernel.Monoid
import cats.syntax.traverse.*
import com.comcast.ip4s.{Host, SocketAddress}
import com.github.markusbernhardt.proxy.ProxySearch
import de.bitmarck.bms.secon.fs2.*
import fs2.io.file.{Files, Path}
import org.http4s.HttpApp
import org.http4s.ember.server.EmberServerBuilder
import org.http4s.implicits.*
import org.http4s.jdkhttpclient.JdkHttpClient
import org.http4s.server.Server
import org.http4s.server.middleware.{ErrorAction, ErrorHandling}
import org.typelevel.log4cats.*
import org.typelevel.log4cats.slf4j.*

import java.net.{ProxySelector, URI}
import scala.concurrent.duration.*
import scala.util.chaining.*

object SeconGateway extends IOApp {
  private implicit val logger: SelfAwareStructuredLogger[IO] = LoggerFactory[IO].getLogger

  override def run(args: List[String]): IO[ExitCode] = {
    ProxySelector.setDefault(
      Option(new ProxySearch().tap { s =>
        s.addStrategy(ProxySearch.Strategy.JAVA)
        s.addStrategy(ProxySearch.Strategy.ENV_VAR)
      }.getProxySelector)
        .getOrElse(ProxySelector.getDefault)
    )

    applicationResource[IO].use(_ => IO.never)
  }

  def applicationResource[F[_] : Async : Logger]: Resource[F, Unit] =
    for {
      config <- Resource.eval(SeconGatewayConfig.fromEnv(Env.make[F]))
      client <- Resource.eval(JdkHttpClient.simple[F])
      keyStore <- Files[F].readAll(Path.fromNioPath(config.keyStorePath))
        .through(loadKeyStore(config.password.toCharArray))
        .compile
        .resource
        .lastOrError
      identityLookup = IdentityLookup.fromKeyStore[F](keyStore, config.password.toCharArray)
      certLookup <- config.ldapUri.map(uri =>
        CertLookup.fromLdapUri[F](URI.create(uri.renderString))
      ).sequence.map(certLookup =>
        Monoid[CertLookup[F]].combineAll(certLookup.toSeq :+ CertLookup.fromIdentityLookup(identityLookup))
      )
      given DecryptVerify[F] = DecryptVerify.make[F]()
      given SignEncrypt[F] = SignEncrypt.make[F]()
      proxyRoutes = new SeconGatewayRoutes[F](
        identityLookup = identityLookup,
        certLookup = certLookup,
        server = config.serverOrDefault,
        multipart = config.multipartOrDefault,
        uri = config.uri,
        httpApp = client.toHttpApp
      )
      _ <- serverResource(
        config.serverAddressOrDefault,
        proxyRoutes.toRoutes.orNotFound
      )
    } yield ()

  def serverResource[F[_] : Async : Logger](
                                             socketAddress: SocketAddress[Host],
                                             http: HttpApp[F]
                                           ): Resource[F, Server] =
    EmberServerBuilder.default[F]
      .withHost(socketAddress.host)
      .withPort(socketAddress.port)
      .withHttpApp(ErrorHandling.Recover.total(ErrorAction.log(
        http = http,
        messageFailureLogAction = (t, msg) => Logger[F].debug(t)(msg),
        serviceErrorLogAction = (t, msg) => Logger[F].error(t)(msg)
      )))
      .withShutdownTimeout(1.second)
      .build
}
