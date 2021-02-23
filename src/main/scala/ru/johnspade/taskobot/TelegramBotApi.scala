package ru.johnspade.taskobot

import cats.effect.ConcurrentEffect
import org.http4s.client.blaze.BlazeClientBuilder
import ru.johnspade.taskobot.Configuration.BotConfig
import telegramium.bots.high.{Api, BotApi}
import zio._
import zio.blocking.Blocking
import zio.interop.catz._

object TelegramBotApi {
  type TelegramBotApi = Has[Api[Task]]

  val live: URLayer[Has[BotConfig] with Blocking, TelegramBotApi] =
    ZLayer.fromServicesManaged[BotConfig, Blocking.Service, Any, Nothing, Api[Task]] { case (cfg, b) =>
      Task.concurrentEffect.toManaged_.flatMap { implicit CE: ConcurrentEffect[Task] =>
        BlazeClientBuilder[Task](b.blockingExecutor.asEC).resource.toManaged.map { httpClient =>
          BotApi[Task](httpClient, s"https://api.telegram.org/bot${cfg.token}")
        }
      }
        .orDie
    }
}
