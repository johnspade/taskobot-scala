package ru.johnspade.taskobot.core.callbackqueries

import telegramium.bots.CallbackQuery

final case class CallbackQueryData[I](
  data: I,
  cb: CallbackQuery
)
