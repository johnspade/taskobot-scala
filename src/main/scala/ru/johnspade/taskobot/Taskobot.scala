package ru.johnspade.taskobot

import java.time.ZoneId

import zio.Task
import zio.*
import zio.interop.catz.*
import zio.json.*

import cats.implicits.*
import iozhik.OpenEnum
import ru.johnspade.tgbot.callbackqueries.*
import telegramium.bots.*
import telegramium.bots.client.Method
import telegramium.bots.high.*
import telegramium.bots.high.implicits.*
import telegramium.bots.high.keyboards.InlineKeyboardMarkups
import telegramium.bots.high.messageentities.MessageEntities
import telegramium.bots.high.messageentities.MessageEntityFormat.*

import ru.johnspade.taskobot.TelegramBotApi.TelegramBotApi
import ru.johnspade.taskobot.core.CbData
import ru.johnspade.taskobot.core.ConfirmTask
import ru.johnspade.taskobot.core.TelegramOps.inlineKeyboardButton
import ru.johnspade.taskobot.datetime.DateTimeController
import ru.johnspade.taskobot.messages.Language
import ru.johnspade.taskobot.messages.MessageService
import ru.johnspade.taskobot.messages.MsgId
import ru.johnspade.taskobot.settings.SettingsController
import ru.johnspade.taskobot.task.NewTask
import ru.johnspade.taskobot.task.TaskController
import ru.johnspade.taskobot.task.TaskRepository
import ru.johnspade.taskobot.user.User

val DefaultPageSize: Int = 5
val MessageLimit         = 4096

val UTC = ZoneId.of("UTC")

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
    dateTimeController: DateTimeController,
    ignoreController: IgnoreController,
    userMiddleware: CallbackQueryUserMiddleware,
    msgService: MessageService,
    kbService: KeyboardService
)(using api: Api[Task])
    extends WebhookBot[Task](api, url = s"${botConfig.url}/${botConfig.token}", path = botConfig.token) {

  private val botId = botConfig.token.split(":").head.toInt

  override def onInlineQueryReply(query: InlineQuery): Task[Option[Method[_]]] = {
    val language: Language =
      query.from.languageCode.flatMap(Language.withValue).getOrElse(Language.English)
    val text            = query.query
    val taskTextEntity  = Bold(text)
    val messageEntities = MessageEntities(taskTextEntity)
    val article = InlineQueryResultArticle(
      id = "1",
      title = msgService.getMessage(MsgId.`tasks-create`, language),
      inputMessageContent = InputTextMessageContent(
        messageEntities.toPlainText(),
        entities = messageEntities.toTelegramEntities().map(OpenEnum(_))
      ),
      replyMarkup = InlineKeyboardMarkups
        .singleButton(
          inlineKeyboardButton("Confirm task", ConfirmTask(id = None, senderId = query.from.id.some))
        )
        .some,
      description = text.some
    )
    ZIO.succeed {
      answerInlineQuery(query.id, List(article), cacheTime = 0.some).some
    }
  }

  override def onChosenInlineResultReply(inlineResult: ChosenInlineResult): Task[Option[Method[_]]] =
    for
      user <- botService.updateUser(inlineResult.from)
      now  <- Clock.instant
      task <- taskRepo.create(NewTask(user.id, inlineResult.query, now, user.timezoneOrDefault))
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
            messageEntities.toPlainText(),
            replyMarkup = kbService.tasks(page, user, user.language).some,
            entities = messageEntities.toTelegramEntities()
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
          _    <- taskRepo.create(NewTask(user.id, text, now, user.timezoneOrDefault, user.id.some))
          _ <- sendMessage(
            ChatIntId(msg.chat.id),
            msgService.taskCreated(text, user.language),
            replyMarkup = kbService.menu(user.language).some
          ).exec
          method <- listPersonalTasks(user)
        yield method.some
      }

    def handleForward() =
      msg.forwardOrigin.fold(None): originOpen =>
        originOpen match
          case OpenEnum.Unknown(_) => Some(ZIO.some(sendMessage(ChatIntId(msg.chat.id), Errors.NotSupported)))
          case OpenEnum.Known(origin) =>
            val (senderName, forwardFromId) = origin match
              case MessageOriginUser(_, senderUser) =>
                Some(senderUser.firstName + senderUser.lastName.map(" " + _).getOrElse("")) -> Some(senderUser.id)
              case MessageOriginChannel(_, chat, _, _)        => chat.title.orElse(chat.username) -> Some(chat.id)
              case MessageOriginHiddenUser(_, senderUserName) => Some(senderUserName)             -> None
              case MessageOriginChat(_, senderChat, _) =>
                senderChat.title.orElse(senderChat.username) -> Some(senderChat.id)
            for
              text <- msg.text
              from <- msg.from
            yield for
              user <- botService.updateUser(from, msg.chat.id.some)
              now  <- Clock.instant
              newTask = NewTask(
                user.id,
                text,
                now,
                user.timezoneOrDefault,
                user.id.some,
                forwardFromId = forwardFromId,
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

    def handleText() =
      msg.text
        .map {
          case t if t.startsWith("/start")                       => commandController.onStartCommand(msg)
          case t if t.startsWith("/create") || t.startsWith("➕") => commandController.onPersonalTaskCommand(msg)
          case t if t.startsWith("\uD83D\uDE80")                 => commandController.onCollaborativeTaskCommand(msg)
          case t if t.startsWith("/list") || t.startsWith("\uD83D\uDCCB") => commandController.onListCommand(msg)
          case t if t.startsWith("/settings") || t.startsWith("⚙")        => commandController.onSettingsCommand(msg)
          case t if t.startsWith("/menu")                                 => commandController.onHelpCommand(msg)
          case t if t.startsWith("/help") || t.startsWith("❓")            => commandController.onHelpCommand(msg)
          case _                                                          => ZIO.none
        }

    def handleWebAppData() = {
      for
        from <- msg.from
        json <- msg.webAppData.map(_.data)
      yield {
        (for
          data     <- ZIO.fromEither(json.fromJson[TimezonesWebAppData]).mapError(e => new RuntimeException(e))
          timezone <- ZIO.attempt(ZoneId.of(data.timezone))
          user     <- botService.updateUser(from, msg.chat.id.some, Some(timezone))
          response <- commandController.onSettingsCommand(msg)
        yield response)
          .catchAll { error =>
            ZIO.logErrorCause("Error while saving user's time zone", Cause.fail(error)) *> ZIO.none
          }
      }
    }

    handleReply()
      .orElse(handleForward())
      .orElse(handleText())
      .orElse(handleWebAppData())
      .getOrElse(ZIO.none)
      .tapDefect(d => ZIO.logError(d.prettyPrint))
  }

  private val cbRoutes =
    taskController.routes <+> settingsController.routes <+> ignoreController.routes <+>
      userMiddleware(taskController.userRoutes <+> settingsController.userRoutes <+> dateTimeController.routes)
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
      with DateTimeController
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
        dateTimeController <- ZIO.service[DateTimeController]
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
        dateTimeController,
        ignoreController,
        userMiddleware,
        msgService,
        keyboardService
      )(using api)
    )
