package ru.johnspade.taskobot

import cats.effect.ConcurrentEffect
import cats.syntax.option._
import org.http4s.client.blaze.BlazeClientBuilder
import org.http4s.server.Server
import ru.johnspade.taskobot.BotService.BotService
import ru.johnspade.taskobot.CommandController.CommandController
import ru.johnspade.taskobot.Configuration.BotConfig
import ru.johnspade.taskobot.core.ConfirmTask
import ru.johnspade.taskobot.core.TelegramOps.inlineKeyboardButton
import ru.johnspade.taskobot.i18n.messages
import ru.johnspade.taskobot.task.TaskRepository.TaskRepository
import ru.johnspade.taskobot.task.tags.TaskText
import ru.johnspade.taskobot.task.{NewTask, TaskRepository}
import ru.makkarpov.scalingua.I18n._
import ru.makkarpov.scalingua.LanguageId
import telegramium.bots.client.Method
import telegramium.bots.high.{Api, WebhookBot, _}
import telegramium.bots.{ChosenInlineResult, InlineQuery, InlineQueryResultArticle, InputTextMessageContent, Markdown2, Message}
import zio._
import zio.interop.catz._
import zio.interop.catz.implicits._

import scala.concurrent.ExecutionContext

object Taskobot {
  type Taskobot = Has[TaskManaged[Server[Task]]]

  val live: URLayer[Has[BotConfig] with BotService with TaskRepository with CommandController, Taskobot] =
    ZLayer.fromServices[
      BotConfig,
      BotService.Service,
      TaskRepository.Service,
      CommandController.Service,
      TaskManaged[Server[Task]]
    ] { (botConfig, botService, taskRepo, commandController) =>
      Task.concurrentEffect.toManaged_.flatMap { implicit CE: ConcurrentEffect[Task] =>
        BlazeClientBuilder[Task](ExecutionContext.global).resource.toManaged.flatMap { httpClient =>
          val api = BotApi[Task](httpClient, s"https://api.telegram.org/bot${botConfig.token}")
          new LiveTaskobot(botConfig, botService, taskRepo, commandController)(api, CE).start().toManaged
        }
      }
    }

  final class LiveTaskobot(
    botConfig: BotConfig,
    botService: BotService.Service,
    taskRepo: TaskRepository.Service,
    commandController: CommandController.Service
  )(
    implicit api: Api[Task],
    CE: ConcurrentEffect[Task]
  ) extends WebhookBot[Task](api, botConfig.port, url = s"${botConfig.url}/${botConfig.token}", path = botConfig.token) {

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
        implicit0(languageId: LanguageId) = LanguageId(user.language.languageTag)
        task <- taskRepo.create(NewTask(user.id, TaskText(inlineResult.query)))
        method = editMessageReplyMarkup(
          inlineMessageId = inlineResult.inlineMessageId,
          replyMarkup = InlineKeyboardMarkup.singleButton(inlineKeyboardButton("Confirm task", ConfirmTask(task.id.some))).some
        )
      } yield method.some

    override def onMessageReply(msg: Message): Task[Option[Method[_]]] =
      ZIO.foreach(
        msg.entities
          .find(entity => entity.`type` == "bot_command" && entity.offset == 0)
          .flatMap(_ => msg.text)
      ) {
        case t if t.startsWith("/help") => commandController.onHelpCommand(msg)
        case _ => commandController.onHelpCommand(msg)
      }
        .map(_.flatten)
  }
}
