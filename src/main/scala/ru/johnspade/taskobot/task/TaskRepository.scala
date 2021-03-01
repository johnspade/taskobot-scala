package ru.johnspade.taskobot.task

import cats.implicits._
import doobie._
import doobie.implicits._
import doobie.postgres.implicits._
import doobie.util.transactor.Transactor
import ru.johnspade.taskobot.DbTransactor.DbTransactor
import ru.johnspade.taskobot.i18n.Language
import ru.johnspade.taskobot.tags.{Offset, PageSize}
import ru.johnspade.taskobot.task.tags._
import ru.johnspade.taskobot.user.User
import ru.johnspade.taskobot.user.tags.{ChatId, FirstName, LastName, UserId}
import zio._
import zio.interop.catz._
import zio.macros.accessible

@accessible
object TaskRepository {
  type TaskRepository = Has[Service]

  trait Service {
    def findById(id: TaskId): Task[Option[BotTask]]

    def findByIdWithCollaborator(id: TaskId, `for`: UserId): Task[Option[TaskWithCollaborator]]

    def create(task: NewTask): Task[BotTask]

    def setReceiver(id: TaskId, senderId: Option[UserId], receiverId: UserId): Task[Unit]

    def findShared(id1: UserId, id2: UserId)(offset: Offset, limit: PageSize): Task[List[BotTask]]

    def check(id: TaskId, doneAt: DoneAt, userId: UserId): Task[Unit]

    def clear(): Task[Unit]
  }

  val live: URLayer[DbTransactor, TaskRepository] = ZLayer.fromService[Transactor[Task], Service] {
    xa =>
      import TaskQueries._

      new Service {
        override def findById(id: TaskId): Task[Option[BotTask]] =
          selectById(id)
            .option
            .transact(xa)

        override def findByIdWithCollaborator(id: TaskId, `for`: UserId): Task[Option[TaskWithCollaborator]] =
          selectByIdJoinCollaborator(id, `for`)
            .option
            .transact(xa)

        override def create(task: NewTask): Task[BotTask] =
          insert(task)
            .unique
            .transact(xa)

        override def setReceiver(id: TaskId, senderId: Option[UserId], receiverId: UserId): Task[Unit] =
          senderId.map(updateReceiverId(id, _, receiverId))
            .getOrElse(updateReceiverIdWithoutSenderCheck(id, receiverId))
            .run
            .void
            .transact(xa)

        override def findShared(id1: UserId, id2: UserId)(offset: Offset, limit: PageSize): Task[List[BotTask]] =
          selectByUserId(id1, id2, offset, limit)
            .to[List]
            .transact(xa)

        override def check(id: TaskId, doneAt: DoneAt, userId: UserId): Task[Unit] =
          setDone(id, doneAt, userId)
            .run
            .void
            .transact(xa)

        override def clear(): Task[Unit] =
          deleteAll
            .run
            .void
            .transact(xa)
      }
  }
}

private object TaskQueries {
  private implicit val taskWithCollaboratorRead: Read[TaskWithCollaborator] =
    Read[(Long, String, Option[Int], Option[String], Option[String], Option[Long], Option[String])].map {
      case (taskId, text, userIdOpt, firstNameOpt, languageOpt, chatIdOpt, lastNameOpt) =>
        val collaborator = for {
          userId <- userIdOpt
          firstName <- firstNameOpt
          language <- languageOpt
        } yield User(UserId(userId), FirstName(firstName), Language.withValue(language), chatIdOpt.map(ChatId(_)), lastNameOpt.map(LastName(_)))
        TaskWithCollaborator(TaskId(taskId), TaskText(text), collaborator)
    }

  def selectById(id: TaskId): Query0[BotTask] =
    sql"""
      select id, sender_id, text, receiver_id, created_at, done_at, done, forward_from_id, forward_sender_name
      from tasks
      where id = $id
    """
      .query[BotTask]

  def selectByIdJoinCollaborator(id: TaskId, `for`: UserId): Query0[TaskWithCollaborator] =
    sql"""
      select t.id, t.text, u.id, u.first_name, u.language, u.chat_id, u.last_name
      from tasks t
      left join users u on
        (u.id = t.sender_id and u.id <> ${`for`}) or
        (u.id = t.receiver_id and u.id <> ${`for`}) or
        (t.sender_id = t.receiver_id and u.id = t.sender_id)
      where t.id = $id and (t.sender_id = ${`for`} or t.receiver_id = ${`for`})
    """
      .query[TaskWithCollaborator]

  def insert(task: NewTask): Query0[BotTask] = {
    import task._

    sql"""
      insert into tasks (sender_id, text, created_at, receiver_id, done, forward_from_id, forward_sender_name)
      values ($sender, $text, $createdAt, $receiver, $done, $forwardFromId, $forwardFromSenderName)
      returning id, sender_id, text, receiver_id, created_at, done_at, done, forward_from_id, forward_sender_name
    """
      .query[BotTask]
  }

  def updateReceiverId(id: TaskId, senderId: UserId, receiverId: UserId): Update0 =
    sql"""
      update tasks
      set receiver_id = $receiverId
      where id = $id and sender_id = $senderId and receiver_id is null
    """
      .update

  def updateReceiverIdWithoutSenderCheck(id: TaskId, receiverId: UserId): Update0 =
    sql"""
      update tasks
      set receiver_id = $receiverId
      where id = $id and receiver_id is null
    """
      .update

  def selectByUserId(id1: UserId, id2: UserId, offset: Offset, limit: PageSize): Query0[BotTask] =
    sql"""
      select id, sender_id, text, receiver_id, created_at, done_at, done, forward_from_id, forward_sender_name
      from tasks
      where receiver_id is not null and done <> true and
      ((sender_id = $id1 and receiver_id = $id2) or
       (sender_id = $id2 and receiver_id = $id1))
      order by created_at desc
      offset $offset limit $limit
    """
      .query[BotTask]

  def setDone(id: TaskId, doneAt: DoneAt, userId: UserId): Update0 =
    sql"""
      update tasks
      set done = true, done_at = $doneAt
      where id = $id and done = false and
      (sender_id = $userId or receiver_id = $userId)
   """
      .update

  val deleteAll: Update0 = sql"delete from tasks where true".update
}
