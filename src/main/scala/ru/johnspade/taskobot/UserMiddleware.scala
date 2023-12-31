package ru.johnspade.taskobot

import zio._
import zio.interop.catz._

import cats.data.Kleisli
import cats.data.OptionT
import ru.johnspade.tgbot.callbackqueries.CallbackQueryData
import ru.johnspade.tgbot.callbackqueries.ContextCallbackQuery

import ru.johnspade.taskobot.core.CbData
import ru.johnspade.taskobot.user.User

object UserMiddleware {
  val live: URLayer[BotService, CallbackQueryUserMiddleware] =
    ZLayer(
      ZIO.service[BotService].map { botService =>
        _.compose(
          Kleisli { (cb: CallbackQueryData[CbData]) =>
            val retrieveUser = botService.updateUser(cb.cb.from)
            OptionT.liftF[Task, ContextCallbackQuery[CbData, User]](retrieveUser.map(ContextCallbackQuery(_, cb)))
          }
        )
      }
    )
}
