package ru.johnspade.taskobot.user

import java.time.ZoneId

import ru.johnspade.taskobot.UTC
import ru.johnspade.taskobot.messages.Language

final case class User(
    id: Long,
    firstName: String,
    language: Language,
    chatId: Option[Long] = None,
    lastName: Option[String] = None,
    timezone: Option[ZoneId] = None,
    blockedBot: Option[Boolean] = None
) {
  def fullName: String = firstName + lastName.fold("")(n => s" $n")

  val timezoneOrDefault: ZoneId = timezone.getOrElse(UTC)

  val sendNotifications = !blockedBot.getOrElse(false)
}
