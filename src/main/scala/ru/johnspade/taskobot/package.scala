package ru.johnspade

import ru.johnspade.taskobot.core.{CbData, Tagged}
import ru.johnspade.taskobot.tags.PageSize
import ru.johnspade.taskobot.user.User
import ru.johnspade.tgbot.callbackqueries.{CallbackQueryContextMiddleware, CallbackQueryContextRoutes, CallbackQueryRoutes}
import supertagged.TaggedOps
import telegramium.bots.client.Method
import zio.Task

package object taskobot {
  object tags {
    object PageNumber extends Tagged[Int] {
      def apply(value: Int): Type =
        if (value >= 0) TaggedOps(this)(value)
        else throw new IllegalArgumentException("Can't be less than zero")
    }
    type PageNumber = PageNumber.Type

    object PageSize extends Tagged[Int] {
      def apply(value: Int): Type =
        if (value >= 0) TaggedOps(this)(value)
        else throw new IllegalArgumentException("Can't be less than zero")
    }
    type PageSize = PageSize.Type

    object Offset extends Tagged[Long] {
      def apply(value: Long): Type =
        if (value >= 0) TaggedOps(this)(value)
        else throw new IllegalArgumentException("Can't be less than zero")
    }
    type Offset = Offset.Type
  }

  val DefaultPageSize: PageSize = PageSize(5)

  type CbDataRoutes[F[_]] = CallbackQueryRoutes[CbData, Option[Method[_]], F]

  type CbDataUserRoutes[F[_]] = CallbackQueryContextRoutes[CbData, User, Option[Method[_]], F]

  type CallbackQueryUserMiddleware = CallbackQueryContextMiddleware[CbData, User, Option[Method[_]], Task]
}
