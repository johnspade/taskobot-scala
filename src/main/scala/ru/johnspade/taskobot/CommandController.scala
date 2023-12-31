package ru.johnspade.taskobot

import zio.*
import zio.interop.catz.*

import cats.syntax.option.*
import telegramium.bots.ChatIntId
import telegramium.bots.ForceReply
import telegramium.bots.Html
import telegramium.bots.LinkPreviewOptions
import telegramium.bots.Message
import telegramium.bots.client.Method
import telegramium.bots.high.Api
import telegramium.bots.high.Methods.sendMessage
import telegramium.bots.high.implicits.*
import telegramium.bots.high.keyboards.InlineKeyboardButtons
import telegramium.bots.high.keyboards.InlineKeyboardMarkups

import ru.johnspade.taskobot.TelegramBotApi.TelegramBotApi
import ru.johnspade.taskobot.core.ChangeLanguage
import ru.johnspade.taskobot.core.Page
import ru.johnspade.taskobot.core.TelegramOps.inlineKeyboardButton
import ru.johnspade.taskobot.messages.MessageService
import ru.johnspade.taskobot.messages.MsgId
import ru.johnspade.taskobot.task.NewTask
import ru.johnspade.taskobot.task.TaskRepository
import ru.johnspade.taskobot.user.User
import ru.johnspade.taskobot.user.UserRepository

trait CommandController:
  def onStartCommand(message: Message): Task[Option[Method[Message]]]

  def onHelpCommand(message: Message): Task[Option[Method[Message]]]

  def onSettingsCommand(message: Message): Task[Option[Method[Message]]]

  def onPersonalTaskCommand(message: Message): Task[Option[Method[Message]]]

  def onCollaborativeTaskCommand(message: Message): Task[Option[Method[Message]]]

  def onListCommand(message: Message): Task[Option[Method[Message]]]

final class CommandControllerLive(
    botService: BotService,
    taskRepo: TaskRepository,
    userRepo: UserRepository,
    msgService: MessageService,
    kbService: KeyboardService
)(implicit bot: Api[Task])
    extends CommandController:
  override def onStartCommand(message: Message): Task[Option[Method[Message]]] =
    createHelpMessage(message)

  override def onHelpCommand(message: Message): Task[Option[Method[Message]]] =
    createHelpMessage(message)

  override def onSettingsCommand(message: Message): Task[Option[Method[Message]]] =
    withSender(message) { user =>
      ZIO.succeed(createSettingsMessage(message, user).some)
    }

  override def onPersonalTaskCommand(message: Message): Task[Option[Method[Message]]] =
    withSender(message) { user =>
      val listPersonalTasks =
        botService
          .getTasks(user, user, 0)
          .map { case (page, messageEntities) =>
            sendMessage(
              ChatIntId(message.chat.id),
              messageEntities.toPlainText(),
              replyMarkup = kbService.tasks(page, user, user.language).some,
              entities = messageEntities.toTelegramEntities()
            )
          }

      ZIO.foreach(message.text) { text =>
        Option
          .when(text.contains("/create ")) {
            text.drop("/create".length).trim()
          }
          .map { task =>
            for
              now <- Clock.instant
              _   <- taskRepo.create(NewTask(user.id, task, now, timezone = user.timezoneOrDefault, user.id.some))
              _ <- sendMessage(
                ChatIntId(message.chat.id),
                msgService.taskCreated(task, user.language),
                replyMarkup = kbService.menu(user.language).some
              ).exec
              method <- listPersonalTasks
            yield method
          }
          .getOrElse(
            ZIO.succeed {
              sendMessage(
                ChatIntId(message.chat.id),
                "/create: " + msgService.getMessage(MsgId.`tasks-personal-new`, user.language),
                replyMarkup = ForceReply(forceReply = true).some
              )
            }
          )
      }
    }

  override def onCollaborativeTaskCommand(message: Message): Task[Option[Method[Message]]] =
    withSender(message) { user =>
      ZIO.succeed {
        sendMessage(
          ChatIntId(message.chat.id),
          msgService.getMessage(MsgId.`help-task-new`, user.language),
          parseMode = Html.some,
          replyMarkup =
            InlineKeyboardMarkups.singleButton(InlineKeyboardButtons.switchInlineQuery("\uD83D\uDE80", "")).some
        ).some
      }
    }

  override def onListCommand(message: Message): Task[Option[Method[Message]]] =
    withSender(message) { user =>
      Page.request[User, Task](0, DefaultPageSize, userRepo.findUsersWithSharedTasks(user.id)).map { page =>
        sendMessage(
          ChatIntId(message.chat.id),
          msgService.chatsWithTasks(user.language),
          replyMarkup = kbService.chats(page, user).some
        ).some
      }
    }

  private def withSender(
      message: Message
  )(handle: User => Task[Option[Method[Message]]]): Task[Option[Method[Message]]] =
    ZIO
      .foreach(message.from)(botService.updateUser(_, message.chat.id.some).flatMap(handle(_)))
      .map(_.flatten)

  private def createHelpMessage(message: Message) =
    withSender(message) { user =>
      ZIO.succeed {
        sendMessage(
          ChatIntId(message.chat.id),
          msgService.help(user.language),
          parseMode = Html.some,
          linkPreviewOptions = LinkPreviewOptions(isDisabled = true.some).some,
          replyMarkup = kbService.menu(user.language).some
        ).some
      }
    }

  private def createSettingsMessage(message: Message, user: User) =
    val currentLanguage = msgService.currentLanguage(user.language)
    val currentTimezone = s"${msgService.getMessage(MsgId.`timezone`, user.language)}: ${user.timezoneOrDefault.getId}"
    sendMessage(
      ChatIntId(message.chat.id),
      s"""
        |$currentLanguage
        |$currentTimezone
        |""".stripMargin,
      replyMarkup = InlineKeyboardMarkups
        .singleButton(inlineKeyboardButton(msgService.switchLanguage(user.language), ChangeLanguage))
        .some
    )

object CommandControllerLive:
  val layer: URLayer[
    TelegramBotApi with BotService with TaskRepository with UserRepository with MessageService with KeyboardService,
    CommandController
  ] =
    ZLayer {
      for
        api        <- ZIO.service[TelegramBotApi]
        botService <- ZIO.service[BotService]
        taskRepo   <- ZIO.service[TaskRepository]
        userRepo   <- ZIO.service[UserRepository]
        msgService <- ZIO.service[MessageService]
        kbService  <- ZIO.service[KeyboardService]
      yield new CommandControllerLive(botService, taskRepo, userRepo, msgService, kbService)(api)
    }
