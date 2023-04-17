package de.bitmarck.bms.secon.http4s

import org.http4s.Media

trait EntityMediaEncoder[F[_], A] {
  def toMedia(a: A): F[Media[F]]
}

object EntityMediaEncoder {
  def apply[F[_], A](implicit ev: EntityMediaEncoder[F, A]): EntityMediaEncoder[F, A] = ev
}
