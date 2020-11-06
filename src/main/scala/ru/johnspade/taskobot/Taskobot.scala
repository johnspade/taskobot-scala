package ru.johnspade.taskobot

import cats.syntax.option._
import ru.johnspade.taskobot.Configuration.BotConfig
import ru.johnspade.taskobot.core.ConfirmTask
import ru.johnspade.taskobot.core.TelegramOps.inlineKeyboardButton
import telegramium.bots.client.Method
import telegramium.bots.high.{Api, WebhookBot, _}
import telegramium.bots.{InlineQuery, InlineQueryResultArticle, InputTextMessageContent, Markdown2}
import zio.Task
import zio.interop.catz._
import zio.interop.catz.implicits._

class Taskobot(
  botConfig: BotConfig
)(
  implicit api: Api[Task],
  runtime: zio.Runtime[Any]
) extends WebhookBot[Task](api, botConfig.port, url = s"${botConfig.url}/${botConfig.token}", path = botConfig.token) {
  override def onInlineQueryReply(query: InlineQuery): Task[Option[Method[_]]] = {
    val text = query.query
    val article = InlineQueryResultArticle(
      id = "1",
      title = "Create task",
      inputMessageContent = InputTextMessageContent(s"*$text*", Markdown2.some),
      replyMarkup = InlineKeyboardMarkup.singleButton(inlineKeyboardButton("Confirm task", ConfirmTask(taskId = None))).some,
      description = text.some
    )
    Task.succeed {
      answerInlineQuery(
        query.id,
        List(article),
        cacheTime = 0.some
      ).some
    }
  }
}
