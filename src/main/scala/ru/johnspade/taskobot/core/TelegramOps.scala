package ru.johnspade.taskobot.core

import ru.johnspade.taskobot.i18n.Language
import ru.johnspade.taskobot.user.User
import ru.johnspade.taskobot.user.tags.{ChatId, FirstName, LastName, UserId}
import telegramium.bots.InlineKeyboardButton
import telegramium.bots.high.keyboards.InlineKeyboardButtons

object TelegramOps {
  def inlineKeyboardButton(text: String, cbData: CbData): InlineKeyboardButton =
    InlineKeyboardButtons.callbackData(text, callbackData = cbData.toCsv)

  def toUser(tgUser: telegramium.bots.User, chatId: Option[ChatId] = None): User = {
    val language = tgUser.languageCode.filter(_.startsWith("ru")).fold[Language](Language.English)(_ => Language.Russian)
    User(
      id = UserId(tgUser.id),
      firstName = FirstName(tgUser.firstName),
      lastName = tgUser.lastName.map(LastName(_)),
      chatId = chatId,
      language = language
    )
  }
}
