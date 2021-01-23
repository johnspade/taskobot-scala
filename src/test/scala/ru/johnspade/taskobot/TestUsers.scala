package ru.johnspade.taskobot

import cats.syntax.option._
import ru.johnspade.taskobot.core.TelegramOps.toUser
import ru.johnspade.taskobot.user.User
import ru.johnspade.taskobot.user.tags.ChatId
import telegramium.bots.{User => TgUser}

object TestUsers {
  val johnTg: TgUser = TgUser(1337, isBot = false, "John")
  val kaitrinTg: TgUser = TgUser(911, isBot = false, "Kaitrin")

  val johnChatId: Int = 0
  val john: User = toUser(johnTg, ChatId(johnChatId.toLong).some)
  val kaitrinChatId: Int = 17
  val kaitrin: User = toUser(kaitrinTg, ChatId(kaitrinChatId.toLong).some)

  val taskobot: TgUser = TgUser(id = 123, isBot = true, "Taskobot")
}
