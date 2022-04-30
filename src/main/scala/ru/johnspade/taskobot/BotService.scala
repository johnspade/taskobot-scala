package ru.johnspade.taskobot

import ru.johnspade.taskobot.core.Page
import ru.johnspade.taskobot.core.TelegramOps.toUser
import ru.johnspade.taskobot.messages.{Language, MessageService}
import ru.johnspade.taskobot.task.{BotTask, TaskRepository}
import ru.johnspade.taskobot.user.{User, UserRepository}
import ru.johnspade.tgbot.messageentities.TypedMessageEntity
import ru.johnspade.tgbot.messageentities.TypedMessageEntity.Plain.lineBreak
import ru.johnspade.tgbot.messageentities.TypedMessageEntity.*
import ru.johnspade.taskobot.messages.MsgId
import telegramium.bots
import zio.*
import zio.interop.catz.*

trait BotService:
  def updateUser(tgUser: telegramium.bots.User, chatId: Option[Long] = None): Task[User]

  def getTasks(
      `for`: User,
      collaborator: User,
      pageNumber: Int
  ): Task[(Page[BotTask], List[TypedMessageEntity])]

object BotService:
  def updateUser(tgUser: telegramium.bots.User, chatId: Option[Long] = None): RIO[BotService, User] =
    ZIO.serviceWithZIO(_.updateUser(tgUser, chatId))

  def getTasks(
      `for`: User,
      collaborator: User,
      pageNumber: Int
  ): RIO[BotService, (Page[BotTask], List[TypedMessageEntity])] =
    ZIO.serviceWithZIO(_.getTasks(`for`, collaborator, pageNumber))

class BotServiceLive(userRepo: UserRepository, taskRepo: TaskRepository, msgService: MessageService) extends BotService:
  override def updateUser(tgUser: bots.User, chatId: Option[Long] = None): Task[User] =
    userRepo.createOrUpdate(toUser(tgUser, chatId))

  override def getTasks(
      `for`: User,
      collaborator: User,
      pageNumber: Int
  ): Task[(Page[BotTask], List[TypedMessageEntity])] = {
    Page
      .request[BotTask, Task](pageNumber, DefaultPageSize, taskRepo.findShared(`for`.id, collaborator.id))
      .map { page =>
        val chatName =
          if (collaborator.id == `for`.id) msgService.personalTasks(`for`.language) else collaborator.fullName
        val header = List(Plain(msgService.getMessage(MsgId.`chat`, `for`.language) + ": "), Bold(chatName), lineBreak)
        val footer = List(lineBreak, Italic(msgService.getMessage(MsgId.`tasks-complete`, `for`.language)))

        val taskLines = page.items.zipWithIndex
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
      val lineLimit     = taskListLimit / DefaultPageSize
      lines.map { line =>
        val ellipsis = if ((line.text + line.senderName).length > lineLimit) "..." else ""
        line.copy(text = line.text.take(lineLimit - line.senderName.length - ellipsis.length) + ellipsis)
      }
    } else lines
  }

object BotServiceLive:
  val layer: URLayer[UserRepository with TaskRepository with MessageService, BotService] =
    ZLayer {
      for
        userRepo   <- ZIO.service[UserRepository]
        taskRepo   <- ZIO.service[TaskRepository]
        msgService <- ZIO.service[MessageService]
      yield new BotServiceLive(userRepo, taskRepo, msgService)
    }
