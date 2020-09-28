package services

import org.joda.time.DateTime
import com.mohiva.play.silhouette.api.util.{Clock => SilhouetteClock}

/**
  * A clock that provides datetime.
  */
class Clock extends SilhouetteClock {

  def now: DateTime = DateTime.now

}
