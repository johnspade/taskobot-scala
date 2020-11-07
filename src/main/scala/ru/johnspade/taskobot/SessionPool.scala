package ru.johnspade.taskobot

import cats.effect.Resource
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
      Task.runtime.toManaged_.flatMap { implicit r: Runtime[Any] =>
        Session.pooled(cfg.host, cfg.port, cfg.user, cfg.database, cfg.password.some, 10).toManaged
      }
        .orDie
    }
}
