package services

import java.{util => ju}

trait UUIDGeneratorLike {

  def generate(): String

}

class UUIDGenerator extends UUIDGeneratorLike {

  def generate(): String = ju.UUID.randomUUID.toString

}
