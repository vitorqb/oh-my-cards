package utils.anorm

import anorm.Column
import anorm.MetaDataItem
import anorm.TypeDoesNotMatch

final case class RelatedObjectDoesNotExist(
    private val message: String = "",
    private val cause: Throwable = None.orNull
) extends Exception(message, cause)

trait AnormUtils {
  // Custom conversion from JDBC column to Boolean
  implicit def columnToBoolean: Column[Boolean] =
    Column.nonNull1 { (value, meta) =>
      val MetaDataItem(qualified, nullable, clazz) = meta
      value match {
        case bool: Boolean => Right(bool) // Provided-default case
        case bit: Int      => Right(bit == 1) // Custom conversion
        case _ =>
          Left(
            TypeDoesNotMatch(
              s"Cannot convert $value: ${value.asInstanceOf[AnyRef].getClass} to Boolean for column $qualified"
            )
          )
      }
    }
}
