package utils.lazyvalue

import org.scalatestplus.play.PlaySpec

class LazyValueSpec extends PlaySpec {

  "LazyValue" should {

    "use default factory if no value passed" in {
      val myValue = LazyValue(() => 1)
      myValue.get() mustEqual 1
    }

    "use value is passed" in {
      val myValue = LazyValue(() => 1)
      myValue.set(2)
      myValue.get() mustEqual 2
    }

  }

}
