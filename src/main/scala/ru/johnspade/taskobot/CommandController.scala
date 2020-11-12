package ru.johnspade.taskobot

import cats.syntax.option._
import ru.johnspade.taskobot.BotService.BotService
import ru.johnspade.taskobot.TelegramBotApi.TelegramBotApi
import ru.johnspade.taskobot.core.ChangeLanguage
import ru.johnspade.taskobot.core.TelegramOps.inlineKeyboardButton
import ru.johnspade.taskobot.i18n.messages
import ru.johnspade.taskobot.task.TaskRepository.TaskRepository
import ru.johnspade.taskobot.task.tags.{CreatedAt, TaskText}
import ru.johnspade.taskobot.task.{NewTask, TaskRepository, TaskType}
import ru.johnspade.taskobot.user.User
import ru.johnspade.taskobot.user.tags.ChatId
import ru.makkarpov.scalingua.I18n._
import ru.makkarpov.scalingua.LanguageId
import telegramium.bots.client.Method
import telegramium.bots.high.Methods.sendMessage
import telegramium.bots.high.implicits._
import telegramium.bots.high.{Api, InlineKeyboardButton, InlineKeyboardMarkup}
import telegramium.bots.{ChatIntId, ForceReply, Html, Message}
import zio._
import zio.clock.Clock
import zio.macros.accessible

@accessible
object CommandController {
  type CommandController = Has[Service]

  trait Service {
    def onStartCommand(message: Message): UIO[Option[Method[Message]]]

    def onHelpCommand(message: Message): UIO[Option[Method[Message]]]

    def onSettingsCommand(message: Message): UIO[Option[Method[Message]]]

    def onCreateCommand(message: Message): UIO[Option[Method[Message]]]
  }

  val live: URLayer[TelegramBotApi with BotService with TaskRepository with Clock, CommandController] =
    ZLayer.fromServices[Api[Task], BotService.Service, TaskRepository.Service, Clock.Service, Service] {
      (api, botService, taskRepo, clock) => new LiveService(botService, taskRepo, clock)(api)
    }

  final class LiveService(
    botService: BotService.Service,
    taskRepository: TaskRepository.Service,
    clock: Clock.Service
  )(implicit bot: Api[Task]) extends Service {
    override def onStartCommand(message: Message): UIO[Option[Method[Message]]] =
      withSender(message) { user =>
        implicit val languageId: LanguageId = LanguageId(user.language.languageTag)
        createHelpMessage(message).exec.orDie.as(createSettingsMessage(message, user).some)
      }

    override def onHelpCommand(message: Message): UIO[Option[Method[Message]]] =
      withSender(message) { user =>
        implicit val languageId: LanguageId = LanguageId(user.language.languageTag)
        ZIO.succeed(createHelpMessage(message).some)
      }

    override def onSettingsCommand(message: Message): UIO[Option[Method[Message]]] =
      withSender(message) { user =>
        implicit val languageId: LanguageId = LanguageId(user.language.languageTag)
        ZIO.succeed(createSettingsMessage(message, user).some)
      }

    override def onCreateCommand(message: Message): UIO[Option[Method[Message]]] =
      withSender(message) { user =>
        implicit val languageId: LanguageId = LanguageId(user.language.languageTag)
        ZIO.foreach(message.text) { text =>
          val task = text.drop("/create".length).trim()
          if (task.isEmpty)
            ZIO.succeed {
              sendMessage(
                ChatIntId(message.chat.id),
                t"/create: New personal task",
                replyMarkup = ForceReply(forceReply = true).some
              )
            }
          else
            for {
              now <- clock.instant
              _ <- taskRepository.create(NewTask(TaskType.Personal, user.id, TaskText(task), CreatedAt(now.toEpochMilli)))
              method = sendMessage(ChatIntId(message.chat.id), Messages.taskCreated(task))
            } yield method
        }
      }

    private def withSender(message: Message)(handle: User => UIO[Option[Method[Message]]]): UIO[Option[Method[Message]]] =
      ZIO.foreach(message.from)(botService.updateUser(_, ChatId(message.chat.id).some).flatMap(handle(_))).map(_.flatten)

    private def createHelpMessage(message: Message)(implicit languageId: LanguageId) =
      sendMessage(
        ChatIntId(message.chat.id),
        Messages.help(),
        Html.some,
        disableWebPagePreview = true.some,
        replyMarkup = InlineKeyboardMarkup.singleButton(InlineKeyboardButton.switchInlineQuery(Messages.tasksStart(), "")).some
      )

    private def createSettingsMessage(message: Message, user: User)(implicit languageId: LanguageId) = {
      val languageName = user.language.languageName
      sendMessage(
        ChatIntId(message.chat.id),
        t"Current language: $languageName",
        replyMarkup = InlineKeyboardMarkup.singleButton(inlineKeyboardButton(t"Switch language", ChangeLanguage)).some
      )
    }
  }
}
