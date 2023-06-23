package ru.johnspade.taskobot.task

import cats.data.NonEmptyList
import cats.syntax.all.*
import doobie.*
import doobie.implicits.*
import ru.johnspade.taskobot.DbTransactor.DbTransactor
import ru.johnspade.taskobot.task.ReminderRepositoryLive.ReminderQueries.*
import zio.*
import zio.interop.catz.*
import ru.johnspade.taskobot.Errors.MaxRemindersExceeded
import doobie.free.connection

trait ReminderRepository:
  def create(taskId: Long, userId: Long, offsetMinutes: Int): ZIO[Any, MaxRemindersExceeded | Throwable, Reminder]
  def getByTaskIdAndUserId(taskId: Long, userId: Long): Task[List[Reminder]]
  def delete(id: Long, userId: Long): Task[Unit]
  def getEnqueued(): Task[List[Reminder]]
  def fetchAndMarkAsProcessing(ids: NonEmptyList[Long]): Task[List[Reminder]]
  def deleteByIds(ids: NonEmptyList[Long]): Task[Unit]

object ReminderRepository:
  def create(
      taskId: Long,
      userId: Long,
      offsetMinutes: Int
  ): ZIO[ReminderRepository, MaxRemindersExceeded | Throwable, Reminder] =
    ZIO.serviceWithZIO(_.create(taskId, userId, offsetMinutes))

  def getByTaskIdAndUserId(taskId: Long, userId: Long): ZIO[ReminderRepository, Throwable, List[Reminder]] =
    ZIO.serviceWithZIO(_.getByTaskIdAndUserId(taskId, userId))

  def delete(id: Long, userId: Long): ZIO[ReminderRepository, Throwable, Unit] =
    ZIO.serviceWithZIO(_.delete(id, userId))

  def getEnqueued(): ZIO[ReminderRepository, Throwable, List[Reminder]] =
    ZIO.serviceWithZIO(_.getEnqueued())

  def fetchAndMarkAsProcessing(ids: NonEmptyList[Long]): ZIO[ReminderRepository, Throwable, List[Reminder]] =
    ZIO.serviceWithZIO(_.fetchAndMarkAsProcessing(ids))

  def deleteByIds(ids: NonEmptyList[Long]): ZIO[ReminderRepository, Throwable, Unit] =
    ZIO.serviceWithZIO(_.deleteByIds(ids))

class ReminderRepositoryLive(xa: DbTransactor) extends ReminderRepository:
  override def create(
      taskId: Long,
      userId: Long,
      offsetMinutes: Int
  ): ZIO[Any, MaxRemindersExceeded | Throwable, Reminder] =
    (for
      reminderIds <- countByTaskId(taskId, userId).to[List]
      reminderCount = reminderIds.length
      reminder <-
        if (reminderCount < 3) insert(taskId, userId, offsetMinutes).unique
        else connection.raiseError(MaxRemindersExceeded(taskId))
    yield reminder)
      .transact(xa)

  override def getByTaskIdAndUserId(taskId: Long, userId: Long): Task[List[Reminder]] =
    selectByTaskIdAndUserId(taskId, userId)
      .to[List]
      .transact(xa)

  override def delete(id: Long, userId: Long): Task[Unit] =
    deleteById(id, userId).run.void
      .transact(xa)

  override def getEnqueued(): Task[List[Reminder]] =
    selectEnqueued()
      .to[List]
      .transact(xa)

  override def fetchAndMarkAsProcessing(ids: NonEmptyList[Long]): Task[List[Reminder]] =
    selectAndSetStatusProcessing(ids)
      .to[List]
      .transact(xa)

  override def deleteByIds(ids: NonEmptyList[Long]): Task[Unit] =
    deleteMultiple(ids).run.transact(xa).unit
end ReminderRepositoryLive

object ReminderRepositoryLive:
  val layer: URLayer[DbTransactor, ReminderRepository] =
    ZLayer(ZIO.service[DbTransactor].map(new ReminderRepositoryLive(_)))

  object ReminderQueries:
    def insert(taskId: Long, userId: Long, offsetMinutes: Int): Query0[Reminder] =
      sql"""
          insert into reminders (task_id, user_id, offset_minutes, status)
          values ($taskId, $userId, $offsetMinutes, 'ENQUEUED')
          returning id, task_id, user_id, offset_minutes, status
        """.query[Reminder]

    def selectByTaskIdAndUserId(taskId: Long, userId: Long): Query0[Reminder] =
      sql"""
          select id, task_id, user_id, offset_minutes, status
          from reminders
          where task_id = $taskId and user_id = $userId
          order by offset_minutes asc
        """
        .query[Reminder]

    def deleteById(id: Long, userId: Long): Update0 =
      sql"""
          delete from reminders
          where id = $id and user_id = $userId
        """.update

    def selectEnqueued(): Query0[Reminder] =
      sql"""
          select id, task_id, user_id, offset_minutes, status
          from reminders
          where status = 'ENQUEUED'
        """
        .query[Reminder]

    def selectAndSetStatusProcessing(ids: NonEmptyList[Long]) =
      (fr"""
          update reminders
          set status = 'PROCESSING'
          where id in(
            select id from reminders r
            where status = 'ENQUEUED' and 
        """ ++ Fragments.in(fr"id", ids) ++
        fr"""
            for update skip locked)
            returning id, task_id, user_id, offset_minutes, status
          """)
        .query[Reminder]

    def countByTaskId(taskId: Long, userId: Long): Query0[Long] =
      sql"""
           select id from reminders where task_id = $taskId and user_id = $userId for update
         """.query[Long]

    def deleteMultiple(ids: NonEmptyList[Long]): Update0 =
      (fr"""
          delete from reminders
          where 
        """ ++ Fragments.in(fr"id", ids)).update
  end ReminderQueries
end ReminderRepositoryLive
