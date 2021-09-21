package ru.johnspade.taskobot

import org.http4s.blaze.client.BlazeClientBuilder
import ru.johnspade.taskobot.Configuration.BotConfig
import telegramium.bots.high.{Api, BotApi}
import zio._
import zio.blocking.Blocking
import zio.clock.Clock
import zio.interop.catz._

import scala.concurrent.ExecutionContext

object TelegramBotApi {
  type TelegramBotApi = Has[Api[Task]]

  val live: URLayer[Has[BotConfig] with Blocking with Clock, TelegramBotApi] =
    ZLayer.fromServiceManaged[BotConfig, Blocking with Clock, Nothing, Api[Task]] { cfg =>
      (for {
        implicit0(rts: Runtime[Clock with Blocking]) <- ZIO.runtime[Clock with Blocking].toManaged_
        httpClient <- BlazeClientBuilder[Task](ExecutionContext.global).resource.toManagedZIO
        botApi = BotApi[Task](httpClient, s"https://api.telegram.org/bot${cfg.token}")
      } yield botApi)
        .orDie
    }
}
