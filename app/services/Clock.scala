package services

import org.joda.time.DateTime


/**
  * A clock that provides datetime.
  */
class Clock extends com.mohiva.play.silhouette.api.util.Clock {

  def now(): DateTime = DateTime.now

}
