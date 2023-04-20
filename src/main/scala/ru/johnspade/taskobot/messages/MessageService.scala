package ru.johnspade.taskobot.messages

import zio.{URLayer, ZIO, ZLayer}
import MsgId.*

import java.text.MessageFormat

trait MessageService:
  def getMessage(id: MsgId, lang: Language, args: String*): String

  def help(lang: Language): String

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
  def getMessage(id: MsgId, lang: Language, args: String*): String =
    val message = msgConfig.messages(lang)(id)
    if args.isEmpty then message
    else MessageFormat.format(message, args: _*)

  def help(lang: Language): String =
    getMessage(`help-description`, lang) + " " +
      getMessage(`help-forward`, lang) + "\n\n" +
      getMessage(`help-due-date`, lang) + "\n\n" +
      getMessage(`help-task-complete`, lang) + "\n\n" +
      switchLanguage(lang) + ": /settings" + "\n" +
      getMessage(`support-creator`, lang) + ": https://buymeacoff.ee/johnspade ☕"

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
