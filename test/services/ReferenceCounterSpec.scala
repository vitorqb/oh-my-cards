package services.referencecounter

import org.scalatestplus.play.PlaySpec
import test.utils.TestUtils

class ReferenceCounterSpec extends PlaySpec {

  "nextRef" should {
    "generate an increasing counter starting in 1002" in {
      TestUtils.testDB { db =>
        val counter = new ReferenceCounter(db)
        counter.nextRef() mustEqual 1001
        counter.nextRef() mustEqual 1002
        counter.nextRef() mustEqual 1003
      }
    }
  }

}
