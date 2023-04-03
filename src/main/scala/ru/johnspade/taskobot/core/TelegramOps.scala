package ru.johnspade.taskobot.core

import ru.johnspade.taskobot.messages.Language
import ru.johnspade.taskobot.user.User
import telegramium.bots.CallbackQuery
import telegramium.bots.InlineKeyboardButton
import telegramium.bots.Message
import telegramium.bots.client.Method
import telegramium.bots.high.Methods.answerCallbackQuery
import telegramium.bots.high.keyboards.InlineKeyboardButtons
import zio.*

import java.time.ZoneId

object TelegramOps {
  def inlineKeyboardButton(text: String, cbData: CbData): InlineKeyboardButton =
    InlineKeyboardButtons.callbackData(text, callbackData = cbData.toCsv)

  def toUser(tgUser: telegramium.bots.User, chatId: Option[Long] = None, timezone: Option[ZoneId] = None): User = {
    val language =
      tgUser.languageCode.filter(_.startsWith("ru")).fold[Language](Language.English)(_ => Language.Russian)
    User(
      id = tgUser.id,
      firstName = tgUser.firstName,
      lastName = tgUser.lastName,
      chatId = chatId,
      language = language,
      timezone = timezone,
      // If we got chatId, the bot is not blocked
      blockedBot = chatId.map(_ => false)
    )
  }

  def ackCb(cb: CallbackQuery)(f: Message => Task[Unit]): ZIO[Any, Throwable, Option[Method[Boolean]]] =
    ZIO.foreachDiscard(cb.message)(f).as(Some(answerCallbackQuery(cb.id)))
}
