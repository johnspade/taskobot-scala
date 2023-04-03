package ru.johnspade.taskobot.task

import cats.data.NonEmptyList
import cats.syntax.all.*
import doobie.*
import doobie.implicits.*
import ru.johnspade.taskobot.DbTransactor.DbTransactor
import ru.johnspade.taskobot.task.ReminderRepositoryLive.ReminderQueries.*
import zio.*
import zio.interop.catz.*
import zio.interop.catz.implicits.*

trait ReminderRepository:
  def create(taskId: Long, userId: Long, offsetMinutes: Int): Task[Unit]
  def getByTaskIdAndUserId(taskId: Long, userId: Long): Task[List[Reminder]]
  def delete(id: Long): Task[Unit]
  def getEnqueued(): Task[List[Reminder]]
  def fetchAndMarkAsProcessing(ids: NonEmptyList[Long]): Task[List[Reminder]]
  def deleteByIds(ids: NonEmptyList[Long]): Task[Unit]

object ReminderRepository:
  def create(taskId: Long, userId: Long, offsetMinutes: Int): ZIO[ReminderRepository, Throwable, Unit] =
    ZIO.serviceWithZIO(_.create(taskId, userId, offsetMinutes))

  def getByTaskIdAndUserId(taskId: Long, userId: Long): ZIO[ReminderRepository, Throwable, List[Reminder]] =
    ZIO.serviceWithZIO(_.getByTaskIdAndUserId(taskId, userId))

  def delete(id: Long): ZIO[ReminderRepository, Throwable, Unit] =
    ZIO.serviceWithZIO(_.delete(id))

  def getEnqueued(): ZIO[ReminderRepository, Throwable, List[Reminder]] =
    ZIO.serviceWithZIO(_.getEnqueued())

  def fetchAndMarkAsProcessing(ids: NonEmptyList[Long]): ZIO[ReminderRepository, Throwable, List[Reminder]] =
    ZIO.serviceWithZIO(_.fetchAndMarkAsProcessing(ids))

  def deleteByIds(ids: NonEmptyList[Long]): ZIO[ReminderRepository, Throwable, Unit] =
    ZIO.serviceWithZIO(_.deleteByIds(ids))

class ReminderRepositoryLive(xa: DbTransactor) extends ReminderRepository:
  override def create(taskId: Long, userId: Long, offsetMinutes: Int): Task[Unit] =
    (for
      reminderIds <- countByTaskId(taskId).to[List]
      reminderCount = reminderIds.length
      _ <- insert(taskId, userId, offsetMinutes).run.void.whenA(reminderCount < 3)
    yield reminderCount)
      .transact(xa)
      .flatMap { count =>
        ZIO.logWarning(s"Max reminders for taskId $taskId exceeded").when(count >= 3).unit
      }

  override def getByTaskIdAndUserId(taskId: Long, userId: Long): Task[List[Reminder]] =
    selectByTaskIdAndUserId(taskId, userId)
      .to[List]
      .transact(xa)

  override def delete(id: Long): Task[Unit] =
    deleteById(id).run.void
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
    def insert(taskId: Long, userId: Long, offsetMinutes: Int): Update0 =
      sql"""
          insert into reminders (task_id, user_id, offset_minutes, status)
          values ($taskId, $userId, $offsetMinutes, 'ENQUEUED')
        """.update

    def selectByTaskIdAndUserId(taskId: Long, userId: Long): Query0[Reminder] =
      sql"""
          select id, task_id, user_id, offset_minutes, status
          from reminders
          where task_id = $taskId and user_id = $userId
          order by offset_minutes asc
        """
        .query[Reminder]

    def deleteById(id: Long): Update0 =
      sql"""
          delete from reminders
          where id = $id
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

    def countByTaskId(taskId: Long): Query0[Long] =
      sql"""
           select id from reminders where task_id = $taskId for update
         """.query[Long]

    def deleteMultiple(ids: NonEmptyList[Long]): Update0 =
      (fr"""
          delete from reminders
          where 
        """ ++ Fragments.in(fr"id", ids)).update
  end ReminderQueries
end ReminderRepositoryLive
