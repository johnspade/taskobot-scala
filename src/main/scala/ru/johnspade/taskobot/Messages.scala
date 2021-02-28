package ru.johnspade.taskobot

import ru.makkarpov.scalingua.LanguageId
import ru.johnspade.taskobot.i18n.{Language, messages}
import ru.makkarpov.scalingua.I18n._

object Messages {
  def help()(implicit languageId: LanguageId): String =
    t"Taskobot is a task collaboration bot. You can type <code>@tasko_bot task</code> in private chat and select <b>Create task</b>. After receiver's confirmation collaborative task will be created. Type /list in the bot chat to see your tasks." +
      "\n\n" +
      switchLanguage() + ": /settings" +
      "\n" +
      t"Support a creator" + ": https://buymeacoff.ee/johnspade ☕" +
      "\n\n" +
      t"Forward messages here to create personal tasks."

  def taskCreated(task: String)(implicit languageId: LanguageId): String = t"""Personal task "$task" has been created."""

  def chatsWithTasks()(implicit languageId: LanguageId): String = t"Chats with tasks"

  def personalTasks()(implicit languageId: LanguageId): String = t"Personal tasks"

  def previousPage()(implicit languageId: LanguageId): String = t"Previous page"

  def nextPage()(implicit languageId: LanguageId): String = t"Next page"

  def currentLanguage(language: Language)(implicit languageId: LanguageId): String = {
    val languageName = language.languageName
    t"Current language: $languageName"
  }

  def switchLanguage()(implicit languageId: LanguageId): String = t"Switch language"
}
