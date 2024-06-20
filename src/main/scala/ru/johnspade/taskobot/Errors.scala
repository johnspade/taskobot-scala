package ru.johnspade.taskobot

object Errors:
  final val Default: String      = "Something went wrong..."
  final val NotFound: String     = "Not found."
  final val NotSupported: String = "This message is not supported."

  final case class MaxRemindersExceeded(taskId: Long) extends Throwable
