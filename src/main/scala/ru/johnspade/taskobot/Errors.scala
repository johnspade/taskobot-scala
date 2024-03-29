package ru.johnspade.taskobot

object Errors:
  final val Default: String  = "Something went wrong..."
  final val NotFound: String = "Not found."

  final case class MaxRemindersExceeded(taskId: Long) extends Throwable
