package services.TagsFilterMiniLang.Helpers

/** Param name generator for unique param names in queries.
  *
  * This helper object generates "unique" strings that can be used by the TagsFilterMiniLang
  * to use as names for the sql parameters it generates in it's Result.
  */
class SqlParamNameGenerator() {

  var count: Int = 0
  var generated: List[String] = List()

  /**
    * Generates a unique name for a parameter.
    */
  def gen(): String = {
    val count_ = count
    count += 1
    val result = s"__TAGSMINILANG_PARAM_${count_}__"
    generated = generated ::: List(result)
    result
  }

}
