package de.bitmarck.bms.secon.gateway

import cats.data.OptionT
import cats.effect.Sync
import cats.effect.std.Env
import com.comcast.ip4s.*
import io.circe.generic.semiauto.*
import io.circe.{Codec, Decoder, Encoder}
import org.http4s.Uri

import java.nio.file.{Path, Paths}

case class SeconGatewayConfig(
                               serverAddress: Option[SocketAddress[Host]],
                               server: Option[Boolean],
                               multipart: Option[Boolean],
                               keyStorePath: Path,
                               password: String,
                               ldapUri: Option[Uri],
                               uri: Uri
                             ) {
  val serverAddressOrDefault: SocketAddress[Host] =
    serverAddress.getOrElse(SocketAddress(host"0.0.0.0", port"8080"))

  val serverOrDefault: Boolean =
    server.getOrElse(false)

  val multipartOrDefault: Boolean =
    multipart.getOrElse(true)
}

object SeconGatewayConfig {
  private implicit val socketAddressCodec: Codec[SocketAddress[Host]] = Codec.from(
    Decoder.decodeString.map(SocketAddress.fromString(_).get),
    Encoder.encodeString.contramap(_.toString())
  )

  private implicit val pathCodec: Codec[Path] = Codec.from(
    Decoder.decodeString.map(Paths.get(_)),
    Encoder.encodeString.contramap(_.toString)
  )

  private implicit val uriCodec: Codec[Uri] = Codec.from(
    Decoder.decodeString.map(Uri.unsafeFromString),
    Encoder.encodeString.contramap(_.renderString)
  )

  implicit val codec: Codec[SeconGatewayConfig] = deriveCodec

  def fromEnv[F[_] : Sync](env: Env[F]): F[SeconGatewayConfig] =
    OptionT(env.get("CONFIG"))
      .toRight(new IllegalArgumentException("Missing environment variable: CONFIG"))
      .subflatMap(io.circe.config.parser.decode[SeconGatewayConfig](_))
      .rethrowT
}