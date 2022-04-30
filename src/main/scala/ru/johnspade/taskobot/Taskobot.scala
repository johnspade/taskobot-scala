package ru.johnspade.taskobot

import cats.implicits.*
import ru.johnspade.taskobot.BotService
import ru.johnspade.taskobot.BotConfig
import ru.johnspade.taskobot.IgnoreController
import ru.johnspade.taskobot.TelegramBotApi.TelegramBotApi
import ru.johnspade.taskobot.UserMiddleware
import ru.johnspade.taskobot.core.TelegramOps.inlineKeyboardButton
import ru.johnspade.taskobot.core.{CbData, ConfirmTask}
import ru.johnspade.taskobot.settings.SettingsController
import ru.johnspade.taskobot.task.TaskController
import ru.johnspade.taskobot.task.{NewTask, TaskController, TaskRepository}
import ru.johnspade.taskobot.user.User
import ru.johnspade.tgbot.callbackqueries.{CallbackDataDecoder, CallbackQueryHandler, DecodeError, ParseError}
import ru.johnspade.tgbot.messageentities.TypedMessageEntity
import ru.johnspade.tgbot.messageentities.TypedMessageEntity.*
import ru.johnspade.taskobot.messages.MsgId
import telegramium.bots.client.Method
import telegramium.bots.high.implicits.*
import telegramium.bots.high.keyboards.InlineKeyboardMarkups
import telegramium.bots.high.{Api, WebhookBot}
import telegramium.bots.{CallbackQuery, ChatIntId, ChosenInlineResult, InlineQuery, InlineQueryResultArticle, InputTextMessageContent, Message}
import zio.*
import zio.interop.catz.*
import zio.interop.catz.implicits.*
import ru.johnspade.taskobot.core.CbData
import ru.johnspade.taskobot.messages.{Language, MessageService}
import ru.johnspade.taskobot.user.User
import ru.johnspade.tgbot.callbackqueries.{CallbackQueryContextMiddleware, CallbackQueryContextRoutes, CallbackQueryRoutes}
import telegramium.bots.client.Method
import zio.Task

val DefaultPageSize: Int = 5
val MessageLimit         = 4096

type CbDataRoutes[F[_]] = CallbackQueryRoutes[CbData, Option[Method[_]], F]

type CbDataUserRoutes[F[_]] = CallbackQueryContextRoutes[CbData, User, Option[Method[_]], F]

type CallbackQueryUserMiddleware = CallbackQueryContextMiddleware[CbData, User, Option[Method[_]], Task]

final class Taskobot(
    botConfig: BotConfig,
    botService: BotService,
    taskRepo: TaskRepository,
    commandController: CommandController,
    taskController: TaskController,
    settingsController: SettingsController,
    ignoreController: IgnoreController,
    userMiddleware: CallbackQueryUserMiddleware,
    msgService: MessageService,
    kbService: KeyboardService
)(implicit
    api: Api[Task]
) extends WebhookBot[Task](api, url = s"${botConfig.url}/${botConfig.token}", path = botConfig.token) {

  private val botId = botConfig.token.split(":").head.toInt

  override def onInlineQueryReply(query: InlineQuery): Task[Option[Method[_]]] = {
    val language: Language =
      query.from.languageCode.flatMap(Language.withValue).getOrElse(Language.English)
    val text           = query.query
    val taskTextEntity = Bold(text)
    val article = InlineQueryResultArticle(
      id = "1",
      title = msgService.getMessage(MsgId.`tasks-create`, language),
      inputMessageContent = InputTextMessageContent(
        taskTextEntity.text,
        entities = TypedMessageEntity.toMessageEntities(List(taskTextEntity))
      ),
      replyMarkup = InlineKeyboardMarkups
        .singleButton(
          inlineKeyboardButton("Confirm task", ConfirmTask(id = None, senderId = query.from.id.some))
        )
        .some,
      description = text.some
    )
    Task.succeed {
      answerInlineQuery(query.id, List(article), cacheTime = 0.some).some
    }
  }

  override def onChosenInlineResultReply(inlineResult: ChosenInlineResult): Task[Option[Method[_]]] =
    for
      user <- botService.updateUser(inlineResult.from)
      now  <- Clock.instant
      task <- taskRepo.create(NewTask(user.id, inlineResult.query, now.toEpochMilli, None))
      method = editMessageReplyMarkup(
        inlineMessageId = inlineResult.inlineMessageId,
        replyMarkup = InlineKeyboardMarkups
          .singleButton(
            inlineKeyboardButton("Confirm task", ConfirmTask(task.id.some, user.id.some))
          )
          .some
      )
    yield method.some

  override def onMessageReply(msg: Message): Task[Option[Method[_]]] = {
    def listPersonalTasks(user: User) =
      botService
        .getTasks(user, user, 0)
        .map { case (page, messageEntities) =>
          sendMessage(
            ChatIntId(msg.chat.id),
            messageEntities.map(_.text).mkString,
            replyMarkup = kbService.tasks(page, user, user.language).some,
            entities = TypedMessageEntity.toMessageEntities(messageEntities)
          )
        }

    def handleReply() =
      for
        replyTo     <- msg.replyToMessage
        replyToText <- replyTo.text if replyTo.from.map(_.id).contains(botId)
        text        <- msg.text if replyToText.startsWith("/create:")
        from        <- msg.from
      yield {
        for
          user <- botService.updateUser(from, msg.chat.id.some)
          now  <- Clock.instant
          _    <- taskRepo.create(NewTask(user.id, text, now.toEpochMilli, user.id.some))
          _ <- sendMessage(
            ChatIntId(msg.chat.id),
            msgService.taskCreated(text, user.language),
            replyMarkup = kbService.menu(user.language).some
          ).exec
          method <- listPersonalTasks(user)
        yield method.some
      }

    def handleForward() =
      for
        _ <- msg.forwardDate
        senderName = msg.forwardSenderName
          .orElse {
            msg.forwardFrom
              .map(u => u.firstName + u.lastName.map(" " + _).getOrElse(""))
          }
        text <- msg.text
        from <- msg.from
      yield {
        for
          user <- botService.updateUser(from, msg.chat.id.some)
          now  <- Clock.instant
          newTask = NewTask(
            user.id,
            text,
            now.toEpochMilli,
            user.id.some,
            forwardFromId = msg.forwardFrom.map(user => user.id),
            forwardFromSenderName = senderName
          )
          _ <- taskRepo.create(newTask)
          _ <- sendMessage(
            ChatIntId(msg.chat.id),
            msgService.taskCreated(text, user.language),
            replyMarkup = kbService.menu(user.language).some
          ).exec
          method <- listPersonalTasks(user)
        yield method.some
      }

    def handleText() =
      ZIO
        .foreach(msg.text) {
          case t if t.startsWith("/start")                       => commandController.onStartCommand(msg)
          case t if t.startsWith("/create") || t.startsWith("➕") => commandController.onPersonalTaskCommand(msg)
          case t if t.startsWith("\uD83D\uDE80")                 => commandController.onCollaborativeTaskCommand(msg)
          case t if t.startsWith("/list") || t.startsWith("\uD83D\uDCCB") => commandController.onListCommand(msg)
          case t if t.startsWith("/settings") || t.startsWith("⚙")        => commandController.onSettingsCommand(msg)
          case t if t.startsWith("/menu")                                 => commandController.onHelpCommand(msg)
          case t if t.startsWith("/help") || t.startsWith("❓")            => commandController.onHelpCommand(msg)
          case _                                                          => ZIO.none
        }
        .map(_.flatten)

    handleReply()
      .orElse(handleForward())
      .getOrElse(handleText())
  }

  private val cbRoutes = taskController.routes <+> settingsController.routes <+> ignoreController.routes <+>
    userMiddleware(taskController.userRoutes <+> settingsController.userRoutes)
  private val cbDataDecoder: CallbackDataDecoder[Task, CbData] =
    CbData
      .decode(_)
      .toEitherT[Task]

  override def onCallbackQueryReply(query: CallbackQuery): Task[Option[Method[_]]] =
    CallbackQueryHandler.handle(
      query,
      cbRoutes,
      cbDataDecoder,
      _ => ZIO.succeed(Option.empty[Method[_]])
    )
}

object Taskobot:
  val live: URLayer[
    TelegramBotApi
      with BotConfig
      with BotService
      with TaskRepository
      with CommandController
      with TaskController
      with SettingsController
      with IgnoreController
      with CallbackQueryUserMiddleware
      with MessageService
      with KeyboardService,
    Taskobot
  ] =
    ZLayer(
      for
        api                <- ZIO.service[TelegramBotApi]
        botConfig          <- ZIO.service[BotConfig]
        botService         <- ZIO.service[BotService]
        taskRepo           <- ZIO.service[TaskRepository]
        commandController  <- ZIO.service[CommandController]
        taskController     <- ZIO.service[TaskController]
        settingsController <- ZIO.service[SettingsController]
        ignoreController   <- ZIO.service[IgnoreController]
        userMiddleware     <- ZIO.service[CallbackQueryUserMiddleware]
        msgService         <- ZIO.service[MessageService]
        keyboardService    <- ZIO.service[KeyboardService]
      yield new Taskobot(
        botConfig,
        botService,
        taskRepo,
        commandController,
        taskController,
        settingsController,
        ignoreController,
        userMiddleware,
        msgService,
        keyboardService
      )(api)
    )
