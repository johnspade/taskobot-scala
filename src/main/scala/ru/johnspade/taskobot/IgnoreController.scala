package ru.johnspade.taskobot

import cats.syntax.option._
import ru.johnspade.taskobot.core.Ignore
import ru.johnspade.tgbot.callbackqueries.CallbackQueryDsl._
import ru.johnspade.tgbot.callbackqueries.CallbackQueryRoutes
import telegramium.bots.high.Methods.answerCallbackQuery
import zio.interop.catz._
import zio.{Has, Task, ULayer, ZLayer}

object IgnoreController {
  type IgnoreController = Has[Service]

  trait Service {
    def routes: CbDataRoutes[Task]
  }

  val live: ULayer[IgnoreController] = ZLayer.succeed {
    new Service {
      override def routes: CbDataRoutes[Task] = CallbackQueryRoutes.of {

        case Ignore in cb =>
          Task.succeed(answerCallbackQuery(cb.id).some)

      }
    }
  }
}
