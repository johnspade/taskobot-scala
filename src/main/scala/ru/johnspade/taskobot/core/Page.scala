package ru.johnspade.taskobot.core

import cats.Functor
import cats.syntax.functor._
import ru.johnspade.taskobot.tags.{Offset, PageNumber, PageSize}

// todo test
sealed abstract class Page[T] extends Product with Serializable {
  def items: List[T]

  def number: PageNumber

  def hasPrevious: Boolean

  def hasNext: Boolean
}

object Page {
  // todo guards
  def request[T, F[_]: Functor](number: PageNumber, size: PageSize, f: (Offset, PageSize) => F[List[T]]): F[Page[T]] = {
    val offset = (size * number).toLong
    val adjustedOffset = Offset(if (number == 0) offset else offset - 1)
    val limit = PageSize(if (number == 0) size + 1 else size + 2)

    f(adjustedOffset, limit).map { items =>
      val pageContent = items.slice((offset - adjustedOffset).toInt, size)
      items.size match {
        case s if s <= size && number == 0 => SinglePage(pageContent)
        case s if s > size && number == 0 => FirstPage(pageContent)
        case s if s <= size && number > 0 => LastPage(pageContent, number)
        case s if s > size && number > 0 => PageN(pageContent, number)
      }
    }
  }

  final case class FirstPage[T](content: List[T]) extends Page[T] {
    val items: List[T] = content
    val number: PageNumber = PageNumber(0)
    val hasPrevious: Boolean = false
    val hasNext: Boolean = true
  }

  final case class LastPage[T](content: List[T], pageNumber: PageNumber) extends Page[T] {
    val items: List[T] = content
    val number: PageNumber = pageNumber
    val hasPrevious: Boolean = true
    val hasNext = false
  }

  final case class SinglePage[T](content: List[T]) extends Page[T] {
    val items: List[T] = content
    val number: PageNumber = PageNumber(0)
    val hasPrevious: Boolean = false
    val hasNext: Boolean = false
  }

  final case class PageN[T](content: List[T], pageNumber: PageNumber) extends Page[T] {
    val items: List[T] = content
    val number: PageNumber = pageNumber
    val hasPrevious: Boolean = true
    val hasNext: Boolean = true
  }
}
