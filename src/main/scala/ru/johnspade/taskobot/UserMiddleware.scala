package ru.johnspade.taskobot

import cats.data.{Kleisli, OptionT}
import ru.johnspade.taskobot.BotService.BotService
import ru.johnspade.taskobot.core.CbData
import ru.johnspade.taskobot.user.User
import ru.johnspade.tgbot.callbackqueries.{CallbackQueryData, ContextCallbackQuery}
import zio._
import zio.interop.catz._

object UserMiddleware {
  type UserMiddleware = Has[CallbackQueryUserMiddleware]

  val live: URLayer[BotService, UserMiddleware] =
    ZLayer.fromService[BotService.Service, CallbackQueryUserMiddleware] { botService =>
      _.compose(
        Kleisli { (cb: CallbackQueryData[CbData]) =>
          val retrieveUser = botService.updateUser(cb.cb.from)
          OptionT.liftF[Task, ContextCallbackQuery[CbData, User]](retrieveUser.map(ContextCallbackQuery(_, cb)))
        }
      )
    }
}
