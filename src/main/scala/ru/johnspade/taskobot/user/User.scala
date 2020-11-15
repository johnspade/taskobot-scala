package ru.johnspade.taskobot.user

import ru.johnspade.taskobot.i18n.Language
import ru.johnspade.taskobot.user.tags._

final case class User(
  id: UserId,
  firstName: FirstName,
  lastName: Option[LastName],
  chatId: Option[ChatId],
  language: Language
) {
  def fullName: String = firstName + lastName.fold("")(n => s" $n")
}
