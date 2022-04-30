package ru.johnspade.taskobot.core

import ru.johnspade.taskobot.messages.Language
import ru.johnspade.taskobot.user.User
import telegramium.bots.InlineKeyboardButton
import telegramium.bots.high.keyboards.InlineKeyboardButtons

object TelegramOps {
  def inlineKeyboardButton(text: String, cbData: CbData): InlineKeyboardButton =
    InlineKeyboardButtons.callbackData(text, callbackData = cbData.toCsv)

  def toUser(tgUser: telegramium.bots.User, chatId: Option[Long] = None): User = {
    val language =
      tgUser.languageCode.filter(_.startsWith("ru")).fold[Language](Language.English)(_ => Language.Russian)
    User(
      id = tgUser.id,
      firstName = tgUser.firstName,
      lastName = tgUser.lastName,
      chatId = chatId,
      language = language
    )
  }
}
