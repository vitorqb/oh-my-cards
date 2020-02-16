package services

import java.{util => ju}

class UUIDGenerator {

  def generate(): String = ju.UUID.randomUUID.toString

}
