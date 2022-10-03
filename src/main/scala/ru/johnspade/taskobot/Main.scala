package ru.johnspade.taskobot

import ru.johnspade.taskobot.DbConfig
import ru.johnspade.taskobot.Taskobot
import ru.johnspade.taskobot.messages.{MessageServiceLive, MsgConfig}
import ru.johnspade.taskobot.settings.SettingsControllerLive
import ru.johnspade.taskobot.task.{TaskControllerLive, TaskRepositoryLive}
import ru.johnspade.taskobot.user.UserRepositoryLive
import zio.*
import zio.interop.catz.*

object Main extends ZIOAppDefault:
  private val program =
    for
      _ <- FlywayMigration.migrate
      botConfig <- ZIO.service[BotConfig]
      _ <- ZIO.serviceWithZIO[Taskobot](_.start(botConfig.port, "0.0.0.0").useForever)
    yield ()

  def run: ZIO[Environment with ZIOAppArgs with Scope, Any, Any] =
    program
      .provide(
        DbConfig.live,
        BotConfig.live,
        MsgConfig.live,
        DbTransactor.live,
        MessageServiceLive.layer,
        KeyboardServiceLive.layer,
        UserRepositoryLive.layer,
        TaskRepositoryLive.layer,
        TelegramBotApi.live,
        BotServiceLive.layer,
        CommandControllerLive.layer,
        TaskControllerLive.layer,
        SettingsControllerLive.layer,
        IgnoreControllerLive.layer,
        UserMiddleware.live,
        Taskobot.live
      )
