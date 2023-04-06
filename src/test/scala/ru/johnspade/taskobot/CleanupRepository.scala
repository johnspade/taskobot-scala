package ru.johnspade.taskobot

import doobie.*
import doobie.implicits.*
import ru.johnspade.taskobot.DbTransactor.DbTransactor
import zio.*
import zio.interop.catz.*

trait CleanupRepository:
  def clearTasks(): Task[Unit]
  def clearUsers(): Task[Unit]
  def clearReminders(): Task[Unit]

object CleanupRepository:
  def clearTasks(): ZIO[CleanupRepository, Throwable, Unit] =
    ZIO.serviceWithZIO(_.clearTasks())
  def clearUsers(): ZIO[CleanupRepository, Throwable, Unit] =
    ZIO.serviceWithZIO(_.clearUsers())
  def clearReminders(): ZIO[CleanupRepository, Throwable, Unit] =
    ZIO.serviceWithZIO(_.clearReminders())

final class CleanupRepositoryLive(xa: DbTransactor) extends CleanupRepository:
  override def clearTasks(): Task[Unit]     = sql"delete from tasks where true".update.run.transact(xa).unit
  override def clearUsers(): Task[Unit]     = sql"delete from users where true".update.run.transact(xa).unit
  override def clearReminders(): Task[Unit] = sql"delete from reminders where true".update.run.transact(xa).unit

object CleanupRepositoryLive:
  val layer: ZLayer[DbTransactor, Nothing, CleanupRepository] = ZLayer.fromFunction(new CleanupRepositoryLive(_))
