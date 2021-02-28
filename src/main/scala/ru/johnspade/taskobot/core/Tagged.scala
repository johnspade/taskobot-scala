package ru.johnspade.taskobot.core

import supertagged.TaggedType

trait Tagged[T]
  extends TaggedType[T]
    with TaggedMeta[T]
