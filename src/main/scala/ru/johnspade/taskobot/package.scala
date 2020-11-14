package ru.johnspade

import ru.johnspade.taskobot.core.CbData
import ru.johnspade.taskobot.core.callbackqueries.CallbackQueryContextRoutes
import ru.johnspade.taskobot.tags.PageSize
import ru.johnspade.taskobot.user.User
import supertagged.TaggedType

package object taskobot {
  object tags {
    object PageNumber extends TaggedType[Int]
    type PageNumber = PageNumber.Type

    object PageSize extends TaggedType[Int]
    type PageSize = PageSize.Type

    object Offset extends TaggedType[Long]
    type Offset = Offset.Type
  }

  val DefaultPageSize: PageSize = PageSize(5)

  type CbDataUserRoutes[F[_]] = CallbackQueryContextRoutes[CbData, User, F]
}
