package ru.johnspade.taskobot.user

import ru.johnspade.taskobot.i18n.Language
import ru.johnspade.taskobot.user.tags._

final case class User(
  id: UserId,
  firstName: FirstName,
  language: Language,
  chatId: Option[ChatId] = None,
  lastName: Option[LastName] = None,
) {
  def fullName: String = firstName + lastName.fold("")(n => s" $n")
}
