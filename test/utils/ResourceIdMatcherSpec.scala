package utils.resourceidmatcher

import org.scalatestplus.play.PlaySpec

class ResourceIdMatcherSpec extends PlaySpec {

  "ResourceIdMatcherLike.run" should {

    "match uuid" in {
      val uuid = "5c86cfd8-f147-4f25-ab55-5bfd472c7365"
      val result = new ResourceIdMatcher().run(uuid)
      result mustEqual UUID(uuid)
    }

    "match ref" in {
      val ref = 12
      val result = new ResourceIdMatcher().run(ref.toString())
      result mustEqual IntRef(ref)
    }

    "not match wrong entry" in {
      val unknown = "12asb"
      val result = new ResourceIdMatcher().run(unknown)
      result mustEqual UnknownRef()
    }

  }

}
