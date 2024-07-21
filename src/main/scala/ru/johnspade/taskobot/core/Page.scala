package ru.johnspade.taskobot.core

import cats.Functor
import cats.syntax.functor.*

final case class Page[T](
    items: List[T],
    number: Int,
    hasPrevious: Boolean,
    hasNext: Boolean
)

object Page {
  def request[T, F[_]: Functor](number: Int, size: Int, f: (Long, Int) => F[List[T]]): F[Page[T]] = {
    require(number >= 0 && size >= 0)

    val offset = (size * number).toLong
    val limit  = size + 1

    f(offset, limit).map { items =>
      Page(
        items = items.take(size),
        number = number,
        hasPrevious = number > 0,
        hasNext = items.size > size
      )
    }
  }
}
