package ru.johnspade.taskobot

import cats.effect.ConcurrentEffect
import cats.syntax.option._
import org.http4s.server.Server
import ru.johnspade.taskobot.BotService.BotService
import ru.johnspade.taskobot.CommandController.CommandController
import ru.johnspade.taskobot.Configuration.BotConfig
import ru.johnspade.taskobot.TelegramBotApi.TelegramBotApi
import ru.johnspade.taskobot.core.ConfirmTask
import ru.johnspade.taskobot.core.TelegramOps.inlineKeyboardButton
import ru.johnspade.taskobot.i18n.messages
import ru.johnspade.taskobot.task.{NewTask, TaskRepository}
import ru.johnspade.taskobot.task.TaskRepository.TaskRepository
import ru.johnspade.taskobot.task.tags.{CreatedAt, TaskText}
import ru.johnspade.taskobot.user.tags.ChatId
import ru.makkarpov.scalingua.I18n._
import ru.makkarpov.scalingua.LanguageId
import telegramium.bots.client.Method
import telegramium.bots.high.{Api, WebhookBot, _}
import telegramium.bots.{ChatIntId, ChosenInlineResult, InlineQuery, InlineQueryResultArticle, InputTextMessageContent, Markdown2, Message}
import zio._
import zio.clock.Clock
import zio.interop.catz._
import zio.interop.catz.implicits._

object Taskobot {
  type Taskobot = Has[TaskManaged[Server[Task]]]

  val live: URLayer[Clock with TelegramBotApi with Has[BotConfig] with BotService with TaskRepository with CommandController, Taskobot] =
    ZLayer.fromServices[
      Clock.Service,
      Api[Task],
      BotConfig,
      BotService.Service,
      TaskRepository.Service,
      CommandController.Service,
      TaskManaged[Server[Task]]
    ] { (clock, api, botConfig, botService, taskRepo, commandController) =>
      Task.concurrentEffect.toManaged_.flatMap { implicit CE: ConcurrentEffect[Task] =>
        new LiveTaskobot(clock, botConfig, botService, taskRepo, commandController)(api, CE).start().toManaged
      }
    }

  final class LiveTaskobot(
    clock: Clock.Service,
    botConfig: BotConfig,
    botService: BotService.Service,
    taskRepo: TaskRepository.Service,
    commandController: CommandController.Service
  )(
    implicit api: Api[Task],
    CE: ConcurrentEffect[Task]
  ) extends WebhookBot[Task](api, botConfig.port, url = s"${botConfig.url}/${botConfig.token}", path = botConfig.token) {

    private val botId = botConfig.token.split(":").head.toInt

    override def onInlineQueryReply(query: InlineQuery): Task[Option[Method[_]]] = {
      implicit val language: LanguageId = query.from.languageCode.flatMap(LanguageId.get).getOrElse(LanguageId("en-US"))
      val text = query.query
      val article = InlineQueryResultArticle(
        id = "1",
        title = t"Create task",
        inputMessageContent = InputTextMessageContent(s"*$text*", Markdown2.some),
        replyMarkup = InlineKeyboardMarkup.singleButton(inlineKeyboardButton("Confirm task", ConfirmTask(taskId = None))).some,
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
          replyMarkup = InlineKeyboardMarkup.singleButton(inlineKeyboardButton("Confirm task", ConfirmTask(task.id.some))).some
        )
      } yield method.some

    override def onMessageReply(msg: Message): Task[Option[Method[_]]] = {
      def handleReply() =
        for {
          replyTo <- msg.replyToMessage
          replyToText <- replyTo.text if replyTo.from.map(_.id).contains(botId)
          text <- msg.text if replyToText.startsWith("/create:")
          from <- msg.from
        } yield {
          for {
            user <- botService.updateUser(from, ChatId(msg.chat.id).some)
            implicit0(languageId: LanguageId) = LanguageId(user.language.languageTag)
            now <- clock.instant
            _ <- taskRepo.create(NewTask(user.id, TaskText(text), CreatedAt(now.toEpochMilli), user.id.some))
            method = sendMessage(ChatIntId(msg.chat.id), Messages.taskCreated(text))
          } yield method.some
        }

      def handleCommand() =
        ZIO.foreach(
          msg.entities
            .find(entity => entity.`type` == "bot_command" && entity.offset == 0)
            .flatMap(_ => msg.text)
        ) {
          case t if t.startsWith("/start") => commandController.onStartCommand(msg)
          case t if t.startsWith("/help") => commandController.onHelpCommand(msg)
          case t if t.startsWith("/settings") => commandController.onSettingsCommand(msg)
          case t if t.startsWith("/create") => commandController.onCreateCommand(msg)
          case t if t.startsWith("/list") => commandController.onListCommand(msg)
          case _ => commandController.onHelpCommand(msg)
        }
          .map(_.flatten)

      handleReply().getOrElse(handleCommand())
    }
  }
}
