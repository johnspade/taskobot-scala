package ru.johnspade.taskobot

import org.http4s.blaze.client.BlazeClientBuilder
import telegramium.bots.high.{Api, BotApi}
import zio.*
import zio.interop.catz.*

object TelegramBotApi {
  type TelegramBotApi = Api[Task]

  val live =
    ZLayer.scoped {
      (for
        cfg        <- ZIO.service[BotConfig]
        httpClient <- BlazeClientBuilder[Task].resource.toScopedZIO
        botApi = BotApi[Task](httpClient, s"https://api.telegram.org/bot${cfg.token}")
      yield botApi).orDie
    }
}
