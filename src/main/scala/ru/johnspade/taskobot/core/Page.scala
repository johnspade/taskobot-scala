package ru.johnspade.taskobot.core

import cats.Functor
import cats.syntax.functor._
import ru.johnspade.taskobot.tags.{Offset, PageNumber, PageSize}

// todo test
sealed abstract class Page[T] extends Product with Serializable {
  def items: Seq[T]

  def number: PageNumber

  def hasPrevious: Boolean

  def hasNext: Boolean
}

object Page {
  // todo guards
  def paginate[T, F[_]: Functor](number: PageNumber, size: PageSize, f: (Offset, PageSize) => F[Seq[T]]): F[Page[T]] = {
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

  final case class FirstPage[T](content: Seq[T]) extends Page[T] {
    val items: Seq[T] = content
    val number: PageNumber = PageNumber(1)
    val hasPrevious: Boolean = false
    val hasNext: Boolean = true
  }

  final case class LastPage[T](content: Seq[T], pageNumber: PageNumber) extends Page[T] {
    val items: Seq[T] = content
    val number: PageNumber = pageNumber
    val hasPrevious: Boolean = true
    val hasNext = false
  }

  final case class SinglePage[T](content: Seq[T]) extends Page[T] {
    val items: Seq[T] = content
    val number: PageNumber = PageNumber(1)
    val hasPrevious: Boolean = false
    val hasNext: Boolean = false
  }

  final case class PageN[T](content: Seq[T], pageNumber: PageNumber) extends Page[T] {
    val items: Seq[T] = content
    val number: PageNumber = pageNumber
    val hasPrevious: Boolean = true
    val hasNext: Boolean = true
  }
}
