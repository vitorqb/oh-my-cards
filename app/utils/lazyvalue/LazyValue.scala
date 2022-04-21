package utils.lazyvalue

case class LazyValue[T](factory: () => T) {
  var currentValue: Option[T] = None
  def get(): T = currentValue.getOrElse(factory())
  def set(x: T): Unit = {
    currentValue = Some(x)
  }
}

object LazyValue {
  implicit def toValue[T](x: LazyValue[T]) = x.get()
}
