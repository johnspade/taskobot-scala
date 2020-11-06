package ru.johnspade.taskobot

import skunk.Session
import zio.{Has, Task}

object DbSession {
  type DbSession = Has[Session[Task]]
}
