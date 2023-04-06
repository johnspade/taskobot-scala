package ru.johnspade.taskobot.messages

import zio.{URLayer, ZIO, ZLayer}
import MsgId.*

import java.text.MessageFormat

trait MessageService:
  def getMessage(id: MsgId, language: Language, args: String*): String

  def help(language: Language): String

  def taskCreated(task: String, language: Language): String

  def chatsWithTasks(language: Language): String

  def personalTasks(language: Language): String

  def previousPage(language: Language): String

  def nextPage(language: Language): String

  def currentLanguage(language: Language): String

  def switchLanguage(language: Language): String

  def remindersMinutesBefore(n: Int, language: Language): String

  def remindersHoursBefore(n: Int, language: Language): String

  def remindersDaysBefore(n: Int, language: Language): String

final class MessageServiceLive(msgConfig: MsgConfig) extends MessageService:
  def getMessage(id: MsgId, language: Language, args: String*): String =
    val message = msgConfig.messages(language)(id)
    if args.isEmpty then message
    else MessageFormat.format(message, args: _*)

  def help(language: Language): String =
    getMessage(`help-description`, language) + "\n\n" +
      switchLanguage(language) + ": /settings" + "\n" +
      getMessage(`support-creator`, language) + ": https://buymeacoff.ee/johnspade ☕" + "\n\n" +
      getMessage(`help-forward`, language)

  def taskCreated(task: String, language: Language): String =
    getMessage(`tasks-personal-created`, language, task)

  def chatsWithTasks(language: Language): String =
    getMessage(`chats-tasks`, language)

  def personalTasks(language: Language): String =
    getMessage(`tasks-personal`, language)

  def previousPage(language: Language): String =
    getMessage(`pages-previous`, language)

  def nextPage(language: Language): String =
    getMessage(`pages-next`, language)

  def currentLanguage(language: Language): String =
    getMessage(`languages-current`, language, language.languageName)

  def switchLanguage(language: Language): String =
    getMessage(`languages-switch`, language)

  def remindersMinutesBefore(n: Int, language: Language): String =
    getMessage(`reminders-minutes-before`, language, n.toString())

  def remindersHoursBefore(n: Int, language: Language): String =
    getMessage(`reminders-hours-before`, language, n.toString())

  def remindersDaysBefore(n: Int, language: Language): String =
    getMessage(`reminders-days-before`, language, n.toString())

object MessageServiceLive:
  val layer: URLayer[MsgConfig, MessageServiceLive] =
    ZLayer(ZIO.service[MsgConfig].map(new MessageServiceLive(_)))
