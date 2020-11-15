package ru.johnspade.taskobot.core

import telegramium.bots.{MessageEntity, User}

sealed abstract class StringMessageEntity extends Product with Serializable {
  def `type`: String
  def value: String
  def url: Option[String] = None
  def user: Option[User] = None
  def language: Option[String] = None
}

object StringMessageEntity {
  // todo test
  def toMessageEntities(entities: List[StringMessageEntity]): List[MessageEntity] =
    entities.foldLeft((List.empty[MessageEntity], 0)) { case ((acc, offset), entity) =>
      entity match {
        case Plain(text) => (acc, offset + text.length)
        case e: StringMessageEntity =>
          val messageEntity = MessageEntity(e.`type`, offset, e.value.length, e.url, e.user, e.language)
          (messageEntity :: acc, offset + e.value.length)
      }
    }
      ._1
      .reverse

  final case class Plain(text: String) extends StringMessageEntity {
    def `type`: String = "plain"
    def value: String = text
  }

  final case class Bold(text: String) extends StringMessageEntity {
    def `type`: String = "bold"
    def value: String = text
  }

  final case class Italic(text: String) extends StringMessageEntity {
    def `type`: String = "italic"
    def value: String = text
  }

  final case class Url(text: String, targetUrl: String) extends StringMessageEntity {
    def `type`: String = "url"
    def value: String = text
    override def url: Option[String] = Some(targetUrl)
  }

  final case class TextMention(text: String, mentionedUser: User) extends StringMessageEntity {
    def `type`: String = "text_mention"
    def value: String = text
    override def user: Option[User] = Some(mentionedUser)
  }

  final case class Pre(text: String, programmingLanguage: String) extends StringMessageEntity {
    def `type`: String = "pre"
    def value: String = text
    override def language: Option[String] = Some(programmingLanguage)
  }
}
