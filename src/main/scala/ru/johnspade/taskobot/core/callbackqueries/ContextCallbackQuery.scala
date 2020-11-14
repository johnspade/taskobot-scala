package ru.johnspade.taskobot.core.callbackqueries

final case class ContextCallbackQuery[I, A](context: A, query: CallbackQueryData[I])
