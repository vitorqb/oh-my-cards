package services.TagsFilterMiniLang.Helpers

import org.scalatestplus.play.PlaySpec

class SqlParamNameGeneratorSpec extends PlaySpec {

  "gen" should {
    "generate two names" in {
      val generator = new SqlParamNameGenerator()
      generator.gen() mustEqual "__TAGSMINILANG_PARAM_0__"
      generator.gen() mustEqual "__TAGSMINILANG_PARAM_1__"
      generator.count mustEqual 2
      generator.generated mustEqual List(
        "__TAGSMINILANG_PARAM_0__",
        "__TAGSMINILANG_PARAM_1__"
      )
    }
  }

}
