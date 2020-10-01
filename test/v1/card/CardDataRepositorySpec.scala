package v1.card

import scala.language.reflectiveCalls

import org.scalatest._
import org.scalatestplus.play._
import org.scalatestplus.mockito.MockitoSugar

import org.mockito.Mockito._

import v1.card.tagsrepository._

import scala.concurrent.ExecutionContext
import org.scalatest.concurrent.ScalaFutures
import v1.auth.User
import scala.util.Failure
import play.api.db.Database
import test.utils.TestUtils
import org.joda.time.DateTime
import scala.concurrent.Await
import scala.concurrent.duration._
import scala.language.postfixOps
import scala.concurrent.Future
import scala.util.Success

import v1.card.testUtils._
import v1.card.CardRefGenerator.CardRefGenerator
import v1.card.cardrepositorycomponents.CardRepositoryComponentsLike
import v1.card.cardrepositorycomponents.CardRepositoryComponents

class CardDataRepositorySpec extends PlaySpec {
  
}
