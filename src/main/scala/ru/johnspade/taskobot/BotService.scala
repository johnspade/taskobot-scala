package ru.johnspade.taskobot

import ru.johnspade.taskobot.core.Page
import ru.johnspade.taskobot.core.TelegramOps.toUser
import ru.johnspade.taskobot.messages.Language
import ru.johnspade.taskobot.messages.MessageService
import ru.johnspade.taskobot.messages.MsgId
import ru.johnspade.taskobot.task.BotTask
import ru.johnspade.taskobot.task.TaskRepository
import ru.johnspade.taskobot.user.User
import ru.johnspade.taskobot.user.UserRepository
import telegramium.bots
import telegramium.bots.high.messageentities.MessageEntities
import telegramium.bots.high.messageentities.MessageEntityFormat
import telegramium.bots.high.messageentities.MessageEntityFormat.Plain.lineBreak
import telegramium.bots.high.messageentities.MessageEntityFormat.*
import zio.*
import zio.interop.catz.*

import java.time.ZoneId
import java.time.format.DateTimeFormatter

trait BotService:
  def updateUser(
      tgUser: telegramium.bots.User,
      chatId: Option[Long] = None,
      timezone: Option[ZoneId] = None
  ): Task[User]

  def getTasks(
      `for`: User,
      collaborator: User,
      pageNumber: Int
  ): Task[(Page[BotTask], MessageEntities)]

  def createTaskDetails(task: BotTask, language: Language): MessageEntities

  def createTaskDetailsReminder(task: BotTask, language: Language): MessageEntities

object BotService:
  def updateUser(
      tgUser: telegramium.bots.User,
      chatId: Option[Long] = None,
      timezone: Option[ZoneId] = None
  ): RIO[BotService, User] =
    ZIO.serviceWithZIO(_.updateUser(tgUser, chatId, timezone))

  def getTasks(
      `for`: User,
      collaborator: User,
      pageNumber: Int
  ): RIO[BotService, (Page[BotTask], MessageEntities)] =
    ZIO.serviceWithZIO(_.getTasks(`for`, collaborator, pageNumber))

  def createTaskDetails(task: BotTask, language: Language): ZIO[BotService, Nothing, MessageEntities] =
    ZIO.serviceWith(_.createTaskDetails(task, language))

  def createTaskDetailsReminder(task: BotTask, language: Language): ZIO[BotService, Nothing, MessageEntities] =
    ZIO.serviceWith(_.createTaskDetailsReminder(task, language))

class BotServiceLive(userRepo: UserRepository, taskRepo: TaskRepository, msgService: MessageService) extends BotService:
  private val dateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

  override def updateUser(tgUser: bots.User, chatId: Option[Long] = None, timezone: Option[ZoneId] = None): Task[User] =
    userRepo.createOrUpdate(toUser(tgUser, chatId, timezone))

  override def getTasks(
      `for`: User,
      collaborator: User,
      pageNumber: Int
  ): Task[(Page[BotTask], MessageEntities)] =
    Page
      .request[BotTask, Task](pageNumber, DefaultPageSize, taskRepo.findShared(`for`.id, collaborator.id))
      .map { page =>
        val chatName =
          if (collaborator.id == `for`.id) msgService.personalTasks(`for`.language) else collaborator.fullName
        val header =
          MessageEntities(Plain(msgService.getMessage(MsgId.`chat`, `for`.language) + ": "), Bold(chatName), lineBreak)

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
            val deadline = task.deadline.fold("")(deadline => s" 🕒 ${deadline.format(dateTimeFormatter)}")
            TaskLine(taskText, senderName, deadline)
          }

        val taskList = limitTaskLines(taskLines, headerFooterLength = header.toPlainText().length)
          .flatMap { line =>
            Vector(Plain(line.text), Italic(line.deadline + line.senderName), lineBreak)
          }

        val messageEntities = MessageEntities(header.underlying ++ taskList)

        (page, messageEntities)
      }
  end getTasks

  override def createTaskDetails(task: BotTask, language: Language): MessageEntities =
    taskDetails(task, language)

  override def createTaskDetailsReminder(task: BotTask, language: Language): MessageEntities =
    taskDetails(task, language, reminderHeader = "🔔 ")

  private def taskDetails(task: BotTask, language: Language, reminderHeader: String = "") =
    val header = Plain(reminderHeader)
    val deadline = MessageEntities(
      Bold(
        s"🕒 ${msgService.getMessage(MsgId.`tasks-due-date`, language)}: " +
          task.deadline
            .map(_.format(dateTimeFormatter))
            .getOrElse("-")
      ),
      lineBreak,
      lineBreak
    )
    val created = MessageEntities(
      Italic(
        s"${msgService.getMessage(MsgId.`tasks-created-at`, language)}: " +
          task.createdAt
            .atZone(task.timezoneOrDefault)
            .format(dateTimeFormatter)
      )
    )
    val breaks             = MessageEntities(lineBreak, lineBreak)
    val footer             = breaks ++ deadline ++ created
    val headerFooterLength = (header +: footer).toPlainText().length
    val text               = MessageEntities(Plain(limitTaskText(task.text, headerFooterLength)))
    header +: (text ++ footer)
  end taskDetails

  private case class TaskLine(text: String, senderName: String, deadline: String)

  private val LineBreakLength = "\n".length
  private val LineBreaksCount = DefaultPageSize

  private def limitTaskLines(lines: List[TaskLine], headerFooterLength: Int): List[TaskLine] =
    def truncatedText(line: TaskLine, lineLimit: Int): String =
      val ellipsis = if ((line.text + line.senderName + line.deadline).length > lineLimit) "..." else ""
      line.text.take(lineLimit - line.senderName.length - line.deadline.length - ellipsis.length) + ellipsis

    val messageLength =
      lines.map(l => (l.text + l.senderName + l.deadline).length + LineBreakLength).sum + headerFooterLength

    if (messageLength > MessageLimit) {
      val taskListLimit = MessageLimit - headerFooterLength - LineBreaksCount
      val lineLimit     = taskListLimit / DefaultPageSize
      lines.map(line => line.copy(text = truncatedText(line, lineLimit)))
    } else lines

private def limitTaskText(text: String, headerFooterLength: Int) =
  val ellipsis = if text.length + headerFooterLength > MessageLimit then "..." else ""
  text.take(MessageLimit - headerFooterLength - ellipsis.length) + ellipsis

object BotServiceLive:
  val layer: URLayer[UserRepository with TaskRepository with MessageService, BotService] =
    ZLayer {
      for
        userRepo   <- ZIO.service[UserRepository]
        taskRepo   <- ZIO.service[TaskRepository]
        msgService <- ZIO.service[MessageService]
      yield new BotServiceLive(userRepo, taskRepo, msgService)
    }
