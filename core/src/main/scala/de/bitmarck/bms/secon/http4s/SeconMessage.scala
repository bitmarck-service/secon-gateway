package de.bitmarck.bms.secon.http4s

import cats.data.{NonEmptyList, OptionT}
import cats.effect.Async
import cats.syntax.flatMap._
import cats.syntax.functor._
import cats.syntax.semigroupk._
import cats.syntax.traverse._
import de.bitmarck.bms.secon.fs2.{CertLookup, DecryptVerify, IdentityLookup, SignEncrypt}
import fs2.Stream
import org.http4s.headers.`Content-Type`
import org.http4s.multipart.{Multipart, Multiparts, Part}
import org.http4s.{DecodeFailure, EntityDecoder, EntityEncoder, InvalidMessageBodyFailure, Media, MediaType}

trait SeconMessage[F[_]] {
  def metadata: SeconMetadata

  def fileName: Option[String]

  def media: Media[F]

  def encoder: EntityMediaEncoder[F, SeconMessage[F]]

  final def withMedia(media: Media[F]): SeconMessage[F] = {
    val _media = media
    new SeconMessage[F] {
      override def metadata: SeconMetadata = SeconMessage.this.metadata

      override def fileName: Option[String] = SeconMessage.this.fileName

      override def media: Media[F] = _media

      override def encoder: EntityMediaEncoder[F, SeconMessage[F]] =
        SeconMessage.this.encoder
    }
  }

  final def signAndEncrypt(
                            identityLookup: IdentityLookup[F],
                            certLookup: CertLookup[F],
                            request: Boolean
                          )(implicit signEncrypt: SignEncrypt[F]): SeconMessage[F] = withMedia(Media(
    Stream.eval(identityLookup.identityByAliasUnsafe(
      if (request) metadata.sender
      else metadata.empfaenger
    )).flatMap(identity =>
      media.body.through(signEncrypt.signAndEncrypt(
        identity = identity,
        certLookup = certLookup,
        recipientAliases =
          if (request) NonEmptyList.one(metadata.empfaenger)
          else NonEmptyList.one(metadata.sender)
      ))
    ),
    media.headers
      .removePayloadHeaders
      .put(
        media.headers.get[`Content-Type`].map(SeconHeaders.SeconContentType(_)),
        `Content-Type`(MediaType.application.`octet-stream`)
      )
  ))

  final def decryptAndVerify(
                              identityLookup: IdentityLookup[F],
                              certLookup: CertLookup[F]
                            )(implicit decryptVerify: DecryptVerify[F]): SeconMessage[F] = withMedia(Media(
    media.body.through(DecryptVerify[F].decryptAndVerify(
      identityLookup = identityLookup,
      certLookup = certLookup
    )),
    media.headers
      .removePayloadHeaders
      .put(media.headers.get[SeconHeaders.SeconContentType].map(_.contentType))
      .transform(_.filterNot(_.name == SeconHeaders.SeconContentType.headerInstance.name))
  ))
}

object SeconMessage {
  private val partNameIkSender = "iksender"
  private val partNameIkEmpfaenger = "ikempfaenger"
  private val partNameVerfahren = "verfahren"
  private val partNameNutzdaten = "nutzdaten"

  def seconMultipartEncoder[F[_] : Async]: EntityMediaEncoder[F, SeconMessage[F]] = {
    def toMultipart(seconMessage: SeconMessage[F]): F[Multipart[F]] =
      for {
        multiparts <- Multiparts.forSync[F]
        newMultipart <- multiparts.multipart(Vector[Option[Part[F]]](
          Some(Part.formData(partNameIkSender, seconMessage.metadata.sender)),
          Some(Part.formData(partNameIkEmpfaenger, seconMessage.metadata.empfaenger)),
          seconMessage.metadata.verfahren.map(Part.formData(partNameVerfahren, _)),
          Some(Part.fileData(
            name = partNameNutzdaten,
            filename = seconMessage.fileName.getOrElse(partNameNutzdaten),
            entityBody = seconMessage.media.body,
            headers = seconMessage.media.headers
          ))
        ).flatten)
      } yield newMultipart

    new EntityMediaEncoder[F, SeconMessage[F]] {
      override def toMedia(seconMessage: SeconMessage[F]): F[Media[F]] =
        for {
          multipart <- toMultipart(seconMessage)
          entity = EntityEncoder[F, Multipart[F]].toEntity(multipart)
        } yield Media(
          entity.body,
          multipart.headers
        )
    }
  }

  def seconOctetStreamEncoder[F[_] : Async]: EntityMediaEncoder[F, SeconMessage[F]] = new EntityMediaEncoder[F, SeconMessage[F]] {
    override def toMedia(seconMessage: SeconMessage[F]): F[Media[F]] =
      Async[F].pure(Media(
        seconMessage.media.body,
        seconMessage.media.headers ++ seconMessage.metadata.toHeaders
      ))
  }

  def fromOctetStreamRequest[F[_] : Async](msg: Media[F]): OptionT[F, SeconMessage[F]] =
    OptionT.fromOption[F](SeconMetadata.fromHeaders(msg.headers))
      .map(seconMetadata => new SeconMessage[F] {
        override def metadata: SeconMetadata = seconMetadata

        override val fileName: Option[String] = None

        override def media: Media[F] = msg

        override def encoder: EntityMediaEncoder[F, SeconMessage[F]] =
          seconOctetStreamEncoder
      })

  def seconMultipartDecoder[F[_] : Async]: EntityDecoder[F, SeconMessage[F]] = {
    def fromMultipart(multipart: Multipart[F]): OptionT[F, SeconMessage[F]] =
      for {
        sender <- OptionT(multipart.parts.find(_.name.contains(partNameIkSender)).map(_.as[String]).sequence)
        empfaenger <- OptionT(multipart.parts.find(_.name.contains(partNameIkEmpfaenger)).map(_.as[String]).sequence)
        verfahren <- OptionT.liftF(multipart.parts.find(_.name.contains(partNameVerfahren)).map(_.as[String]).sequence)
        nutzdaten <- OptionT.fromOption[F](multipart.parts.find(_.name.contains(partNameNutzdaten)))
      } yield new SeconMessage[F] {
        override val metadata: SeconMetadata = SeconMetadata(
          sender = sender,
          empfaenger = empfaenger,
          verfahren = verfahren
        )

        override def fileName: Option[String] = nutzdaten.filename

        override def media: Media[F] = Media(nutzdaten.body, nutzdaten.headers)

        override def encoder: EntityMediaEncoder[F, SeconMessage[F]] =
          seconMultipartEncoder
      }

    EntityDecoder.multipart[F].flatMapR { multipart =>
      fromMultipart(multipart)
        .toRight[DecodeFailure](InvalidMessageBodyFailure("failed to decode multipart secon message"))
    }
  }

  def seconOctetStreamDecoder[F[_] : Async]: EntityDecoder[F, SeconMessage[F]] = EntityDecoder.decodeBy(MediaType.application.`octet-stream`) { msg =>
    fromOctetStreamRequest(msg)
      .toRight[DecodeFailure](InvalidMessageBodyFailure("failed to decode secon message"))
  }

  implicit def entityDecoder[F[_] : Async]: EntityDecoder[F, SeconMessage[F]] =
    seconMultipartDecoder <+> seconOctetStreamDecoder
}
