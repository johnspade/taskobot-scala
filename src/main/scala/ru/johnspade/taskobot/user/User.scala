package ru.johnspade.taskobot.user

import ru.johnspade.taskobot.messages.Language

final case class User(
    id: Long,
    firstName: String,
    language: Language,
    chatId: Option[Long] = None,
    lastName: Option[String] = None
) {
  def fullName: String = firstName + lastName.fold("")(n => s" $n")
}
