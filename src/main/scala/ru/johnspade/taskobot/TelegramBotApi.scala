package ru.johnspade.taskobot

import cats.effect.ConcurrentEffect
import org.http4s.client.blaze.BlazeClientBuilder
import ru.johnspade.taskobot.Configuration.BotConfig
import telegramium.bots.high.{Api, BotApi}
import zio._
import zio.interop.catz._

import scala.concurrent.ExecutionContext

object TelegramBotApi {
  type TelegramBotApi = Has[Api[Task]]

  val live: URLayer[Has[BotConfig], TelegramBotApi] =
    ZLayer.fromServiceManaged[BotConfig, Any, Nothing, Api[Task]] { cfg =>
      Task.concurrentEffect.toManaged_.flatMap { implicit CE: ConcurrentEffect[Task] =>
        BlazeClientBuilder[Task](ExecutionContext.global).resource.toManaged.map { httpClient => // todo ec
          BotApi[Task](httpClient, s"https://api.telegram.org/bot${cfg.token}")
        }
      }
        .orDie
    }
}
