package services

import java.{util => ju}

trait UUIDGeneratorLike {

  def generate(): String

}

/**
  * Random UUID Generator.
  */
class UUIDGenerator extends UUIDGeneratorLike {

  def generate(): String = ju.UUID.randomUUID.toString

}

/**
  * Fake UUID generator used mainly for tests.
  */
class CounterUUIDGenerator extends UUIDGeneratorLike {

  var counter = 0

  def generate(): String = {
    counter += 1
    s"$counter"
  }

}
