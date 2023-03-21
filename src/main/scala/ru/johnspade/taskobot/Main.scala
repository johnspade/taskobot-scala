package ru.johnspade.taskobot

import ru.johnspade.taskobot.datetime.DateTimeControllerLive
import ru.johnspade.taskobot.messages.MessageServiceLive
import ru.johnspade.taskobot.messages.MsgConfig
import ru.johnspade.taskobot.settings.SettingsControllerLive
import ru.johnspade.taskobot.task.TaskControllerLive
import ru.johnspade.taskobot.task.TaskRepositoryLive
import ru.johnspade.taskobot.user.UserRepositoryLive
import zio.*
import zio.interop.catz.*
import ru.johnspade.taskobot.datetime.DatePickerServiceLive
import ru.johnspade.taskobot.datetime.TimePickerServiceLive

object Main extends ZIOAppDefault:
  private val program =
    for
      _         <- FlywayMigration.migrate
      botConfig <- ZIO.service[BotConfig]
      _         <- ZIO.serviceWithZIO[Taskobot](_.start(botConfig.port, "0.0.0.0").useForever)
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
        DatePickerServiceLive.layer,
        TimePickerServiceLive.layer,
        DateTimeControllerLive.layer,
        IgnoreControllerLive.layer,
        UserMiddleware.live,
        Taskobot.live
      )
