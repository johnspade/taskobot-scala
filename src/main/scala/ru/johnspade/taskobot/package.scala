package ru.johnspade

import ru.johnspade.taskobot.core.CbData
import ru.johnspade.taskobot.tags.PageSize
import ru.johnspade.taskobot.user.User
import ru.johnspade.tgbot.callbackqueries.{CallbackQueryContextRoutes, CallbackQueryRoutes}
import supertagged.{TaggedOps, TaggedType}

package object taskobot {
  object tags {
    object PageNumber extends TaggedType[Int] {
      def apply(value: Int): Type =
        if (value >= 0) TaggedOps(this)(value)
        else throw new IllegalArgumentException("Can't be less than zero")
    }
    type PageNumber = PageNumber.Type

    object PageSize extends TaggedType[Int] {
      def apply(value: Int): Type =
        if (value >= 0) TaggedOps(this)(value)
        else throw new IllegalArgumentException("Can't be less than zero")
    }
    type PageSize = PageSize.Type

    object Offset extends TaggedType[Long] {
      def apply(value: Long): Type =
        if (value >= 0) TaggedOps(this)(value)
        else throw new IllegalArgumentException("Can't be less than zero")
    }
    type Offset = Offset.Type
  }

  val DefaultPageSize: PageSize = PageSize(5)

  type CbDataRoutes[F[_]] = CallbackQueryRoutes[CbData, F]

  type CbDataUserRoutes[F[_]] = CallbackQueryContextRoutes[CbData, User, F]
}
