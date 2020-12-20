package ru.johnspade.taskobot.core

import cats.Functor
import cats.syntax.functor._
import ru.johnspade.taskobot.tags.{Offset, PageNumber, PageSize}

final case class Page[T](
  items: List[T],
  number: PageNumber,
  hasPrevious: Boolean,
  hasNext: Boolean
)

object Page {
  def request[T, F[_]: Functor](number: PageNumber, size: PageSize, f: (Offset, PageSize) => F[List[T]]): F[Page[T]] = {
    require(number >= 0 && size >= 0)

    val offset = Offset((size * number).toLong)
    val limit = PageSize(size + 1)

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
