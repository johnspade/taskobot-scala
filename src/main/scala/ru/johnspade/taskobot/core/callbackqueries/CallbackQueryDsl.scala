package ru.johnspade.taskobot.core.callbackqueries

import telegramium.bots.CallbackQuery

trait CallbackQueryDsl {
  object as {
    def unapply[I, A](cb: ContextCallbackQuery[I, A]): Option[(CallbackQueryData[I], A)] =
      Some(cb.query -> cb.context)
  }

  object in {
    def unapply[I](cb: CallbackQueryData[I]): Option[(I, CallbackQuery)] =
      Some(cb.data -> cb.cb)
  }
}

object CallbackQueryDsl extends CallbackQueryDsl
