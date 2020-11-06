package ru.johnspade.taskobot.csv

import kantan.csv.{DecodeError, RowDecoder}
import magnolia._

import scala.language.experimental.macros

object MagnoliaRowDecoder {
  type Typeclass[T] = RowDecoder[T]

  def combine[T](ctx: CaseClass[Typeclass, T]): Typeclass[T] =
    (e: Seq[String]) =>
      ctx.constructEither { p =>
        p.typeclass.decode(Seq(e(p.index)))
      }
        .left
        .map(_.head)

  def dispatch[T](ctx: SealedTrait[Typeclass, T]): Typeclass[T] =
    (e: Seq[String]) =>
      if (e.isEmpty)
        Left(DecodeError.OutOfBounds(0))
      else {
        (for {
          typeId <- e.head.toIntOption
          subtype <- ctx.subtypes.find(_.annotations.contains(TypeId(typeId)))
        } yield subtype.typeclass.decode(e.tail))
          .toRight(DecodeError.TypeError(s"Invalid type tag: ${e.head}"))
          .flatten
      }

  implicit def deriveRowDecoder[T]: Typeclass[T] = macro Magnolia.gen[T]
}
