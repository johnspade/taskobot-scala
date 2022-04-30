package ru.johnspade.taskobot.task

import cats.implicits.*
import doobie.*
import doobie.implicits.*
import doobie.postgres.implicits.*
import doobie.util.transactor.Transactor
import ru.johnspade.taskobot.DbTransactor.DbTransactor
import ru.johnspade.taskobot.messages.Language
import ru.johnspade.taskobot.user.User
import zio.*
import zio.interop.catz.*
import ru.johnspade.taskobot.task.TaskRepositoryLive.TaskQueries.*

trait TaskRepository:
  def findById(id: Long): Task[Option[BotTask]]

  def findByIdWithCollaborator(id: Long, `for`: Long): Task[Option[TaskWithCollaborator]]

  def create(task: NewTask): Task[BotTask]

  def setReceiver(id: Long, senderId: Option[Long], receiverId: Long): Task[Unit]

  def findShared(id1: Long, id2: Long)(offset: Long, limit: Int): Task[List[BotTask]]

  def check(id: Long, doneAt: Long, userId: Long): Task[Unit]

  def clear(): Task[Unit]

object TaskRepository:
  def findById(id: Long): ZIO[TaskRepository, Throwable, Option[BotTask]] =
    ZIO.serviceWithZIO(_.findById(id))

  def findByIdWithCollaborator(id: Long, `for`: Long): ZIO[TaskRepository, Throwable, Option[TaskWithCollaborator]] =
    ZIO.serviceWithZIO(_.findByIdWithCollaborator(id, `for`))

  def create(task: NewTask): ZIO[TaskRepository, Throwable, BotTask] =
    ZIO.serviceWithZIO(_.create(task))

  def setReceiver(id: Long, senderId: Option[Long], receiverId: Long): ZIO[TaskRepository, Throwable, Unit] =
    ZIO.serviceWithZIO(_.setReceiver(id, senderId, receiverId))

  def findShared(id1: Long, id2: Long)(offset: Long, limit: Int): ZIO[TaskRepository, Throwable, List[BotTask]] =
    ZIO.serviceWithZIO(_.findShared(id1, id2)(offset, limit))

  def check(id: Long, doneAt: Long, userId: Long): ZIO[TaskRepository, Throwable, Unit] =
    ZIO.serviceWithZIO(_.check(id, doneAt, userId))

  def clear(): ZIO[TaskRepository, Throwable, Unit] =
    ZIO.serviceWithZIO(_.clear())

class TaskRepositoryLive(xa: DbTransactor) extends TaskRepository:
  override def findById(id: Long): Task[Option[BotTask]] =
    selectById(id).option
      .transact(xa)

  override def findByIdWithCollaborator(id: Long, `for`: Long): Task[Option[TaskWithCollaborator]] =
    selectByIdJoinCollaborator(id, `for`).option
      .transact(xa)

  override def create(task: NewTask): Task[BotTask] =
    insert(task).unique
      .transact(xa)

  override def setReceiver(id: Long, senderId: Option[Long], receiverId: Long): Task[Unit] =
    senderId
      .map(updateReceiverId(id, _, receiverId))
      .getOrElse(updateReceiverIdWithoutSenderCheck(id, receiverId))
      .run
      .void
      .transact(xa)

  override def findShared(id1: Long, id2: Long)(offset: Long, limit: Int): Task[List[BotTask]] =
    selectByUserId(id1, id2, offset, limit)
      .to[List]
      .transact(xa)

  override def check(id: Long, doneAt: Long, userId: Long): Task[Unit] =
    setDone(id, doneAt, userId).run.void
      .transact(xa)

  override def clear(): Task[Unit] =
    deleteAll.run.void
      .transact(xa)

object TaskRepositoryLive:
  val layer: URLayer[DbTransactor, TaskRepository] =
    ZLayer(ZIO.service[DbTransactor].map(new TaskRepositoryLive(_)))

  object TaskQueries:
    def selectById(id: Long): Query0[BotTask] =
      sql"""
        select id, sender_id, text, receiver_id, created_at, done_at, done, forward_from_id, forward_sender_name
        from tasks
        where id = $id
      """
        .query[BotTask]

    def selectByIdJoinCollaborator(id: Long, `for`: Long): Query0[TaskWithCollaborator] =
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

    def updateReceiverId(id: Long, senderId: Long, receiverId: Long): Update0 =
      sql"""
        update tasks
        set receiver_id = $receiverId
        where id = $id and sender_id = $senderId and receiver_id is null
      """.update

    def updateReceiverIdWithoutSenderCheck(id: Long, receiverId: Long): Update0 =
      sql"""
        update tasks
        set receiver_id = $receiverId
        where id = $id and receiver_id is null
      """.update

    def selectByUserId(id1: Long, id2: Long, offset: Long, limit: Int): Query0[BotTask] =
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

    def setDone(id: Long, doneAt: Long, userId: Long): Update0 =
      sql"""
        update tasks
        set done = true, done_at = $doneAt
        where id = $id and done = false and
        (sender_id = $userId or receiver_id = $userId)
    """.update

    val deleteAll: Update0 = sql"delete from tasks where true".update
