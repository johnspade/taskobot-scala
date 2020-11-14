package ru.johnspade.taskobot

import cats.effect.{ConcurrentEffect, Resource}
import cats.syntax.option._
import natchez.Trace.Implicits.noop
import ru.johnspade.taskobot.Configuration.DbConfig
import skunk.Session
import zio._
import zio.interop.catz._

object SessionPool {
  type SessionPool = Has[Resource[Task, Session[Task]]]

  val live: URLayer[Has[DbConfig], SessionPool] =
    ZLayer.fromServiceManaged[DbConfig, Any, Nothing, Resource[Task, Session[Task]]] { cfg =>
      Task.concurrentEffect.toManaged_.flatMap { implicit CE: ConcurrentEffect[Task] =>
        Session.pooled(cfg.host, cfg.port, cfg.user, cfg.database, cfg.password.some, 10).toManaged
      }
        .orDie
    }
}
