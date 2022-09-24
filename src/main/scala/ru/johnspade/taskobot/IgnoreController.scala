package ru.johnspade.taskobot

import cats.syntax.option._
import ru.johnspade.taskobot.core.Ignore
import ru.johnspade.tgbot.callbackqueries.CallbackQueryDsl._
import ru.johnspade.tgbot.callbackqueries.CallbackQueryRoutes
import telegramium.bots.high.Methods.answerCallbackQuery
import zio.interop.catz._
import zio._

trait IgnoreController:
  def routes: CbDataRoutes[Task]

final class IgnoreControllerLive extends IgnoreController:
  override def routes: CbDataRoutes[Task] = CallbackQueryRoutes.of { case Ignore in cb =>
    ZIO.succeed(answerCallbackQuery(cb.id).some)
  }

object IgnoreControllerLive:
  val layer: ULayer[IgnoreController] = ZLayer.succeed(new IgnoreControllerLive)
