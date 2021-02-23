package ru.johnspade.taskobot

import org.http4s.client.blaze.BlazeClientBuilder
import ru.johnspade.taskobot.Configuration.BotConfig
import telegramium.bots.high.{Api, BotApi}
import zio._
import zio.interop.catz._

object TelegramBotApi {
  type TelegramBotApi = Has[Api[Task]]

  val live: URLayer[Has[BotConfig], TelegramBotApi] =
    ZLayer.fromServiceManaged[BotConfig, Any, Nothing, Api[Task]] { cfg =>
      ZIO.runtime[Any].toManaged_.flatMap { implicit rts =>
        BlazeClientBuilder[Task](rts.platform.executor.asEC).resource.toManaged.map { httpClient =>
          BotApi[Task](httpClient, s"https://api.telegram.org/bot${cfg.token}")
        }
      }
        .orDie
    }
}
