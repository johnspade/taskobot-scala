package ru.johnspade.taskobot.task

import ru.johnspade.taskobot.user.User

import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import ru.johnspade.taskobot.UTC

case class BotTask(
    id: Long,
    sender: Long,
    text: String,
    receiver: Option[Long],
    createdAt: Instant,
    doneAt: Option[Instant] = None,
    done: Boolean = false,
    forwardFromId: Option[Long] = None,
    forwardFromSenderName: Option[String] = None,
    deadline: Option[LocalDateTime] = None,
    timezone: Option[ZoneId] = None
) {
  val timezoneOrDefault: ZoneId = timezone.getOrElse(UTC)

  def getCollaborator(userId: Long): Long = receiver.filterNot(_ == userId).getOrElse(sender)
}

case class TaskWithCollaborator(
    id: Long,
    text: String,
    collaborator: Option[User]
)
