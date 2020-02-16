package modules

import com.google.inject.AbstractModule
import net.codingwell.scalaguice.ScalaModule
import services.{RandomStringGenerator, UUIDGenerator, Clock}
import java.util.UUID

class Module extends AbstractModule with ScalaModule {

  /**
    * Simple configurations.
    */
  override def configure() = {
    bind[RandomStringGenerator].toInstance(new RandomStringGenerator)
    bind[UUIDGenerator].toInstance(new UUIDGenerator)
    bind[Clock].toInstance(new Clock)
  }

}
