package ru.johnspade.taskobot

import cats.implicits._
import ru.johnspade.taskobot.BotService.BotService
import ru.johnspade.taskobot.CommandController.CommandController
import ru.johnspade.taskobot.Configuration.BotConfig
import ru.johnspade.taskobot.IgnoreController.IgnoreController
import ru.johnspade.taskobot.TelegramBotApi.TelegramBotApi
import ru.johnspade.taskobot.UserMiddleware.UserMiddleware
import ru.johnspade.taskobot.core.TelegramOps.inlineKeyboardButton
import ru.johnspade.taskobot.core.{CbData, ConfirmTask}
import ru.johnspade.taskobot.i18n.messages
import ru.johnspade.taskobot.settings.SettingsController
import ru.johnspade.taskobot.settings.SettingsController.SettingsController
import ru.johnspade.taskobot.tags.PageNumber
import ru.johnspade.taskobot.task.TaskController.TaskController
import ru.johnspade.taskobot.task.TaskRepository.TaskRepository
import ru.johnspade.taskobot.task.tags.{CreatedAt, SenderName, TaskText}
import ru.johnspade.taskobot.task.{NewTask, TaskController, TaskRepository}
import ru.johnspade.taskobot.user.User
import ru.johnspade.taskobot.user.tags.{ChatId, UserId}
import ru.johnspade.tgbot.callbackqueries.{CallbackDataDecoder, CallbackQueryHandler, DecodeError, ParseError}
import ru.johnspade.tgbot.messageentities.TypedMessageEntity
import ru.makkarpov.scalingua.I18n._
import ru.makkarpov.scalingua.LanguageId
import telegramium.bots.client.Method
import telegramium.bots.high.implicits._
import telegramium.bots.high.keyboards.InlineKeyboardMarkups
import telegramium.bots.high.{Api, WebhookBot}
import telegramium.bots.{CallbackQuery, ChatIntId, ChosenInlineResult, InlineQuery, InlineQueryResultArticle, InputTextMessageContent, Markdown2, Message}
import zio._
import zio.clock.Clock
import zio.interop.catz._
import zio.interop.catz.implicits._

object Taskobot {
  type Taskobot = Has[LiveTaskobot]

  val live: URLayer[
    Clock
      with TelegramBotApi
      with Has[BotConfig]
      with BotService
      with TaskRepository
      with CommandController
      with TaskController
      with SettingsController
      with IgnoreController
      with UserMiddleware,
    Taskobot
  ] =
    ZLayer.fromServices[
      Clock.Service,
      Api[Task],
      BotConfig,
      BotService.Service,
      TaskRepository.Service,
      CommandController.Service,
      TaskController.Service,
      SettingsController.Service,
      IgnoreController.Service,
      CallbackQueryUserMiddleware,
      LiveTaskobot
    ] { (clock, api, botConfig, botService, taskRepo, commandController, taskController, settingsController, ignoreController, userMiddleware) =>
      new LiveTaskobot(clock, botConfig, botService, taskRepo, commandController, taskController, settingsController, ignoreController, userMiddleware)(
        api
      )
    }

  final class LiveTaskobot(
    clock: Clock.Service,
    botConfig: BotConfig,
    botService: BotService.Service,
    taskRepo: TaskRepository.Service,
    commandController: CommandController.Service,
    taskController: TaskController.Service,
    settingsController: SettingsController.Service,
    ignoreController: IgnoreController.Service,
    userMiddleware: CallbackQueryUserMiddleware
  )(
    implicit api: Api[Task]
  ) extends WebhookBot[Task](api, url = s"${botConfig.url}/${botConfig.token}", path = botConfig.token) {

    private val botId = botConfig.token.split(":").head.toInt

    override def onInlineQueryReply(query: InlineQuery): Task[Option[Method[_]]] = {
      implicit val language: LanguageId = query.from.languageCode.flatMap(LanguageId.get).getOrElse(LanguageId("en-US"))
      val text = query.query
      val article = InlineQueryResultArticle(
        id = "1",
        title = t"Create task",
        inputMessageContent = InputTextMessageContent(s"*$text*", Markdown2.some),
        replyMarkup = InlineKeyboardMarkups.singleButton(
          inlineKeyboardButton("Confirm task", ConfirmTask(id = None, senderId = UserId(query.from.id).some))
        )
          .some,
        description = text.some
      )
      Task.succeed {
        answerInlineQuery(query.id, List(article), cacheTime = 0.some).some
      }
    }

    override def onChosenInlineResultReply(inlineResult: ChosenInlineResult): Task[Option[Method[_]]] =
      for {
        user <- botService.updateUser(inlineResult.from)
        now <- clock.instant
        task <- taskRepo.create(NewTask(user.id, TaskText(inlineResult.query), CreatedAt(now.toEpochMilli), None))
        method = editMessageReplyMarkup(
          inlineMessageId = inlineResult.inlineMessageId,
          replyMarkup = InlineKeyboardMarkups.singleButton(
            inlineKeyboardButton("Confirm task", ConfirmTask(task.id.some, user.id.some))
          )
            .some
        )
      } yield method.some

    override def onMessageReply(msg: Message): Task[Option[Method[_]]] = {
      def listPersonalTasks(user: User)(implicit language: LanguageId) =
        botService.getTasks(user, user, PageNumber(0))
          .map { case (page, messageEntities) =>
            sendMessage(
              ChatIntId(msg.chat.id),
              messageEntities.map(_.text).mkString,
              replyMarkup = Keyboards.tasks(page, user).some,
              entities = TypedMessageEntity.toMessageEntities(messageEntities),
            )
          }

      def handleReply() =
        for {
          replyTo <- msg.replyToMessage
          replyToText <- replyTo.text if replyTo.from.map(_.id).contains(botId)
          text <- msg.text if replyToText.startsWith("/create:")
          from <- msg.from
        } yield {
          for {
            user <- botService.updateUser(from, ChatId(msg.chat.id).some)
            implicit0(languageId: LanguageId) = LanguageId(user.language.value)
            now <- clock.instant
            _ <- taskRepo.create(NewTask(user.id, TaskText(text), CreatedAt(now.toEpochMilli), user.id.some))
            _ <- sendMessage(ChatIntId(msg.chat.id), Messages.taskCreated(text), replyMarkup = Keyboards.menu().some).exec
            method <- listPersonalTasks(user)
          } yield method.some
        }

      def handleForward() =
        for {
          _ <- msg.forwardDate
          senderName = msg.forwardSenderName
            .orElse {
              msg.forwardFrom
                .map(u => u.firstName + u.lastName.map(" " + _).getOrElse(""))
            }
          text <- msg.text
          from <- msg.from
        } yield {
          for {
            user <- botService.updateUser(from, ChatId(msg.chat.id).some)
            implicit0(languageId: LanguageId) = LanguageId(user.language.value)
            now <- clock.instant
            newTask = NewTask(
              user.id,
              TaskText(text),
              CreatedAt(now.toEpochMilli),
              user.id.some,
              forwardFromId = msg.forwardFrom.map(user => UserId(user.id)),
              forwardFromSenderName = senderName.map(SenderName(_))
            )
            _ <- taskRepo.create(newTask)
            _ <- sendMessage(ChatIntId(msg.chat.id), Messages.taskCreated(text), replyMarkup = Keyboards.menu().some).exec
            method <- listPersonalTasks(user)
          } yield method.some
        }

      def handleText() =
        ZIO.foreach(msg.text) {
          case t if t.startsWith("/start") => commandController.onStartCommand(msg)
          case t if t.startsWith("/create") || t.startsWith("➕") => commandController.onPersonalTaskCommand(msg)
          case t if t.startsWith("\uD83D\uDE80") => commandController.onCollaborativeTaskCommand(msg)
          case t if t.startsWith("/list") || t.startsWith("\uD83D\uDCCB") => commandController.onListCommand(msg)
          case t if t.startsWith("/settings") || t.startsWith("⚙") => commandController.onSettingsCommand(msg)
          case t if t.startsWith("/menu") => commandController.onHelpCommand(msg)
          case t if t.startsWith("/help") || t.startsWith("❓") => commandController.onHelpCommand(msg)
          case _ => ZIO.none
        }
          .map(_.flatten)

      handleReply()
        .orElse(handleForward())
        .getOrElse(handleText())
    }

    private val cbRoutes = taskController.routes <+> settingsController.routes <+> ignoreController.routes <+>
      userMiddleware(taskController.userRoutes <+> settingsController.userRoutes)
    private val cbDataDecoder: CallbackDataDecoder[Task, CbData] =
      CbData.decode(_).left.map {
        case error: kantan.csv.ParseError => ParseError(error.getMessage)
        case error: kantan.csv.DecodeError => DecodeError(error.getMessage)
      }
        .toEitherT[Task]

    override def onCallbackQueryReply(query: CallbackQuery): Task[Option[Method[_]]] = {
      CallbackQueryHandler.handle(
        query,
        cbRoutes,
        cbDataDecoder,
        _ => ZIO.succeed(Option.empty[Method[_]])
      )
    }
  }
}
