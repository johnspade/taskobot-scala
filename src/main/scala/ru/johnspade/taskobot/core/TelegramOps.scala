package ru.johnspade.taskobot.core

import telegramium.bots.high.InlineKeyboardButton

object TelegramOps {
  def inlineKeyboardButton(text: String, cbData: CbData): InlineKeyboardButton =
    InlineKeyboardButton.callbackData(text, callbackData = cbData.toCsv)
}
