package ru.johnspade.taskobot

import ru.johnspade.taskobot.core.Page
import ru.johnspade.taskobot.core.TelegramOps.toUser
import ru.johnspade.taskobot.i18n.messages
import ru.johnspade.taskobot.tags.PageNumber
import ru.johnspade.taskobot.task.TaskRepository.TaskRepository
import ru.johnspade.taskobot.task.{BotTask, TaskRepository}
import ru.johnspade.taskobot.user.UserRepository.UserRepository
import ru.johnspade.taskobot.user.tags._
import ru.johnspade.taskobot.user.{User, UserRepository}
import ru.johnspade.tgbot.messageentities.TypedMessageEntity
import ru.johnspade.tgbot.messageentities.TypedMessageEntity.Plain.lineBreak
import ru.johnspade.tgbot.messageentities.TypedMessageEntity._
import ru.makkarpov.scalingua.I18n._
import ru.makkarpov.scalingua.LanguageId
import telegramium.bots
import zio._
import zio.interop.catz._
import zio.macros.accessible

@accessible
object BotService {
  type BotService = Has[Service]

  trait Service {
    def updateUser(tgUser: telegramium.bots.User, chatId: Option[ChatId] = None): Task[User]

    def getTasks(`for`: User, collaborator: User, pageNumber: PageNumber)(
      implicit languageId: LanguageId
    ): Task[(Page[BotTask], List[TypedMessageEntity])]
  }

  val live: URLayer[UserRepository with TaskRepository, BotService] =
    ZLayer.fromServices[UserRepository.Service, TaskRepository.Service, Service](new LiveService(_, _))

  final class LiveService(
    userRepo: UserRepository.Service,
    taskRepo: TaskRepository.Service
  ) extends Service {
    override def updateUser(tgUser: bots.User, chatId: Option[ChatId] = None): Task[User] =
      userRepo.createOrUpdate(toUser(tgUser, chatId))

    override def getTasks(`for`: User, collaborator: User, pageNumber: PageNumber)(
      implicit languageId: LanguageId
    ): Task[(Page[BotTask], List[TypedMessageEntity])] = {
      Page.request[BotTask, Task](pageNumber, DefaultPageSize, taskRepo.findShared(`for`.id, collaborator.id))
        .map { page =>
          val chatName = if (collaborator.id == `for`.id) Messages.personalTasks() else collaborator.fullName
          val header = List(Plain(t"Chat" + ": "), Bold(chatName), lineBreak)
          val footer = List(lineBreak, Italic(t"Select the task number to mark it as completed."))

          val taskLines = page
            .items
            .zipWithIndex
            .map { case (task, i) =>
              val taskText = s"${i + 1}. ${task.text}"
              val senderName = if (collaborator.id == `for`.id) {
                task.forwardFromSenderName
                  .map(n => s" – $n")
                  .getOrElse("")
              } else {
                val sender = if (task.sender == `for`.id) `for`.firstName else collaborator.firstName
                s" – $sender"
              }
              TaskLine(taskText, senderName)
            }

          val taskList = limitTaskLines(taskLines, headerFooterLength = (header ++ footer).map(_.text).mkString.length)
            .flatMap { line =>
              List(Plain(line.text), Italic(line.senderName), lineBreak)
            }

          val messageEntities = header ++ taskList ++ footer

          (page, messageEntities)
        }
    }

    private case class TaskLine(text: String, senderName: String)

    private val LineBreakLength = "\n".length
    private val LineBreaksCount = DefaultPageSize

    private def limitTaskLines(lines: List[TaskLine], headerFooterLength: Int): List[TaskLine] = {
      val messageLength = lines.map(l => (l.text + l.senderName).length + LineBreakLength).sum + headerFooterLength
      if (messageLength > MessageLimit) {
        val taskListLimit = MessageLimit - headerFooterLength - LineBreaksCount
        val lineLimit = taskListLimit / DefaultPageSize
        lines.map { line =>
          val ellipsis = if ((line.text + line.senderName).length > lineLimit) "..." else ""
          line.copy(text = line.text.take(lineLimit - line.senderName.length - ellipsis.length) + ellipsis)
        }
      }
      else
        lines
    }
  }
}
