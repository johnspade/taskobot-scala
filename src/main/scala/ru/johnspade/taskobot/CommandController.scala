package ru.johnspade.taskobot

import cats.syntax.option._
import ru.johnspade.taskobot.BotService.BotService
import ru.johnspade.taskobot.TelegramBotApi.TelegramBotApi
import ru.johnspade.taskobot.core.TelegramOps.inlineKeyboardButton
import ru.johnspade.taskobot.core.{ChangeLanguage, Page}
import ru.johnspade.taskobot.i18n.messages
import ru.johnspade.taskobot.tags.PageNumber
import ru.johnspade.taskobot.task.TaskRepository.TaskRepository
import ru.johnspade.taskobot.task.tags.{CreatedAt, TaskText}
import ru.johnspade.taskobot.task.{NewTask, TaskRepository}
import ru.johnspade.taskobot.user.UserRepository.UserRepository
import ru.johnspade.taskobot.user.tags.ChatId
import ru.johnspade.taskobot.user.{User, UserRepository}
import ru.makkarpov.scalingua.I18n._
import ru.makkarpov.scalingua.LanguageId
import telegramium.bots.client.Method
import telegramium.bots.high.Api
import telegramium.bots.high.Methods.sendMessage
import telegramium.bots.high.implicits._
import telegramium.bots.high.keyboards.{InlineKeyboardButtons, InlineKeyboardMarkups}
import telegramium.bots.{ChatIntId, ForceReply, Html, Message}
import zio._
import zio.clock.Clock
import zio.interop.catz._
import zio.macros.accessible

@accessible
object CommandController {
  type CommandController = Has[Service]

  trait Service {
    def onStartCommand(message: Message): UIO[Option[Method[Message]]]

    def onHelpCommand(message: Message): UIO[Option[Method[Message]]]

    def onSettingsCommand(message: Message): UIO[Option[Method[Message]]]

    def onCreateCommand(message: Message): UIO[Option[Method[Message]]]

    def onListCommand(message: Message): UIO[Option[Method[Message]]]
  }

  val live: URLayer[TelegramBotApi with BotService with TaskRepository with UserRepository with Clock, CommandController] =
    ZLayer.fromServices[Api[Task], BotService.Service, TaskRepository.Service, UserRepository.Service, Clock.Service, Service] {
      (api, botService, taskRepo, userRepo, clock) => new LiveService(botService, taskRepo, userRepo, clock)(api)
    }

  final class LiveService(
    botService: BotService.Service,
    taskRepo: TaskRepository.Service,
    userRepo: UserRepository.Service,
    clock: Clock.Service
  )(implicit bot: Api[Task]) extends Service {
    override def onStartCommand(message: Message): UIO[Option[Method[Message]]] =
      withSender(message) { user =>
        implicit val languageId: LanguageId = LanguageId(user.language.languageTag)
        createHelpMessage(message).exec.orDie *>
          createSwitchMessage(message).exec.orDie
            .as(createSettingsMessage(message, user).some)
      }

    override def onHelpCommand(message: Message): UIO[Option[Method[Message]]] =
      withSender(message) { user =>
        implicit val languageId: LanguageId = LanguageId(user.language.languageTag)
        createHelpMessage(message)
          .exec
          .orDie
          .as(createSwitchMessage(message).some)
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
          Option.when(text.contains("/create ")) {
            text.drop("/create".length).trim()
          }
            .map { task =>
              for {
                now <- clock.instant
                _ <- taskRepo.create(NewTask(user.id, TaskText(task), CreatedAt(now.toEpochMilli), user.id.some))
                method = sendMessage(ChatIntId(message.chat.id), Messages.taskCreated(task), replyMarkup = Keyboards.menu().some)
              } yield method
            }
            .getOrElse(
              ZIO.succeed {
                sendMessage(
                  ChatIntId(message.chat.id),
                  t"/create: New personal task",
                  replyMarkup = ForceReply(forceReply = true).some
                )
              }
            )
        }
      }

    override def onListCommand(message: Message): UIO[Option[Method[Message]]] =
      withSender(message) { user =>
        implicit val languageId: LanguageId = LanguageId(user.language.languageTag)
        Page.request[User, UIO](PageNumber(0), DefaultPageSize, userRepo.findUsersWithSharedTasks(user.id)).map { page =>
          sendMessage(
            ChatIntId(message.chat.id),
            Messages.chatsWithTasks(),
            replyMarkup = Keyboards.chats(page, user).some
          )
            .some
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
        replyMarkup = Keyboards.menu().some
      )

    private def createSwitchMessage(message: Message)(implicit languageId: LanguageId) =
      sendMessage(
        ChatIntId(message.chat.id),
        t"Start creating tasks" + ":",
        replyMarkup = InlineKeyboardMarkups.singleButton(InlineKeyboardButtons.switchInlineQuery("\uD83D\uDE80", "")).some
      )

    private def createSettingsMessage(message: Message, user: User)(implicit languageId: LanguageId) =
      sendMessage(
        ChatIntId(message.chat.id),
        Messages.currentLanguage(user.language),
        replyMarkup = InlineKeyboardMarkups.singleButton(inlineKeyboardButton(t"Switch language", ChangeLanguage)).some
      )
  }
}
