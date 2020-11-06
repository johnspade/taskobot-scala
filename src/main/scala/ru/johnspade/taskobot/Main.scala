package ru.johnspade.taskobot

import org.http4s.client.blaze.BlazeClientBuilder
import ru.johnspade.taskobot.Configuration.BotConfig
import ru.johnspade.taskobot.Environments.{AppEnvironment, appEnvironment}
import telegramium.bots.high.{Api, BotApi}
import zio.interop.catz._
import zio.{Task, _}

import scala.concurrent.ExecutionContext

object Main extends zio.App {
  val program: ZIO[AppEnvironment, Throwable, Unit] =
    for {
      _ <- FlywayMigration.migrate
      _ <- startBot()
    } yield ()

  private def startBot(): URIO[Has[BotConfig], Unit] =
    (for {
      implicit0(rts: Runtime[Has[BotConfig]]) <- ZIO.runtime[Has[BotConfig]]
      botConfig = rts.environment.get[BotConfig]
      _ <- BlazeClientBuilder[Task](ExecutionContext.global).resource.toManaged.use { httpClient =>
        implicit val api: Api[Task] = BotApi(httpClient, s"https://api.telegram.org/bot${botConfig.token}")
        val bot = new Taskobot(botConfig)
        bot.start().toManaged.useForever.unit
      }
    } yield ())
      .orDie

  def run(args: List[String]): URIO[ZEnv, ExitCode] =
    program
      .provideSomeLayer(appEnvironment)
      .exitCode
}
