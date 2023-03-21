package ru.johnspade.taskobot

import ru.johnspade.taskobot.core.Ignore
import ru.johnspade.tgbot.callbackqueries.CallbackQueryDsl.*
import ru.johnspade.tgbot.callbackqueries.CallbackQueryRoutes
import telegramium.bots.high.Methods.answerCallbackQuery
import zio.*
import zio.interop.catz.*

trait IgnoreController:
  def routes: CbDataRoutes[Task]

final class IgnoreControllerLive extends IgnoreController:
  override def routes: CbDataRoutes[Task] = CallbackQueryRoutes.of { case Ignore in cb =>
    ZIO.some(answerCallbackQuery(cb.id))
  }

object IgnoreControllerLive:
  val layer: ULayer[IgnoreController] = ZLayer.succeed(new IgnoreControllerLive)
