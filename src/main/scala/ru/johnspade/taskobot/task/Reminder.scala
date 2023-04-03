package ru.johnspade.taskobot.task

final case class Reminder(id: Long, taskId: Long, userId: Long, offsetMinutes: Int, status: String = "ENQUEUED")
