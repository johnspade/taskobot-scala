package ru.johnspade.taskobot

import zio.*
import zio.interop.catz.*

import doobie.*
import doobie.implicits.*

import ru.johnspade.taskobot.DbTransactor.DbTransactor

trait CleanupRepository:
  def truncateTables(): Task[Unit]

object CleanupRepository:
  def truncateTables(): ZIO[CleanupRepository, Throwable, Unit] =
    ZIO.serviceWithZIO(_.truncateTables())

final class CleanupRepositoryLive(xa: DbTransactor) extends CleanupRepository:
  override def truncateTables(): Task[Unit] =
    sql"truncate table tasks, users, reminders restart identity cascade".update.run.transact(xa).unit

object CleanupRepositoryLive:
  val layer: ZLayer[DbTransactor, Nothing, CleanupRepository] = ZLayer.fromFunction(new CleanupRepositoryLive(_))
