package v1.staticassets

import utils.TempFileWritter

import org.scalatestplus.play._
import play.api.test.Helpers._
import play.api.test.Helpers
import play.api.test.FakeRequest
import org.scalatest.concurrent.ScalaFutures
import testutils.silhouettetestutils.SilhouetteTestUtils
import scala.concurrent.ExecutionContext
import org.mockito.MockitoSugar
import services.filerepository.FileRepositoryLike
import play.api.mvc.MultipartFormData
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchersSugar
import scala.concurrent.Future
import scala.io.Source
import testutils.silhouettetestutils.SilhouetteInjectorContext
import services.UUIDGenerator
import services.UUIDGeneratorLike
import play.api.mvc.AnyContent
import services.resourcepermissionregistry.ResourcePermissionRegistryLike
import akka.actor.ActorSystem
import akka.stream.Materializer
import java.io.File
import play.api.libs.json.Json
import v1.auth.CookieUserIdentifier
import v1.auth.CookieUserIdentifierLike
import v1.auth.User


class StaticAssetsControllerSpec
    extends PlaySpec
    with ScalaFutures
    with MockitoSugar
    with ArgumentMatchersSugar {

  implicit val ec: ExecutionContext = ExecutionContext.global

  implicit val actorSystem = ActorSystem("test")
  implicit lazy val mat = Materializer(actorSystem)

  case class ControllerBuilder(
    c: SilhouetteInjectorContext,
    f: Option[FileRepositoryLike] = None,
    u: Option[UUIDGeneratorLike] = None,
    r: Option[ResourcePermissionRegistryLike] =  None,
    i: Option[CookieUserIdentifierLike] = None,
    s: Option[User] = None
  ) {
    def withFileRepository(f2: FileRepositoryLike) = copy(f=Some(f2))
    def withUUIDGenerator(u2: UUIDGeneratorLike) = copy(u=Some(u2))
    def withPermissionRegistry(r2: ResourcePermissionRegistryLike) = copy(r=Some(r2))
    def withCookieUserIdentifier(i2: CookieUserIdentifierLike) = copy(i=Some(i2))
    def withUser(s2: User) = copy(s=Some(s2))
    def build(): StaticAssetsController = {
      val f2 = f.getOrElse({
        val x = mock[FileRepositoryLike]
        when(x.store(any, any)).thenReturn(Future.successful(()))
        x
      })
      val u2 = u.getOrElse(mock[UUIDGeneratorLike])
      val r2 = r.getOrElse({
        val x = mock[ResourcePermissionRegistryLike]
        when(x.grantAccess(any, any)).thenReturn(Future.successful(()))
        x
      })
      val s2 = s.getOrElse(User("foo", "bar@baz"))
      val i2 = i.getOrElse({
        val i = mock[CookieUserIdentifier]
        when(i.identifyUser(any)).thenReturn(Future.successful(Some(s2)))
        i
      })
      val components = Helpers.stubControllerComponents()
      new StaticAssetsController(components, c.silhouette, f2, u2, r2, i2)
    }
  }

  def mkFakeRequest(fileContent: Option[String]): FakeRequest[AnyContent] = {
    val result = FakeRequest()
    fileContent match {
      case None => result
      case Some(x) => {
        val file = TempFileWritter.write(x)
        val filePart = MultipartFormData.FilePart("store", "foo.txt", None, file)
        val formData = new MultipartFormData(Map(), Seq(filePart), Seq())
        result.withMultipartFormDataBody(formData)
      }
    }
  }
  def mkFakeRequest(): FakeRequest[AnyContent] = mkFakeRequest(None)
  def mkFakeRequest(x: String): FakeRequest[AnyContent] = mkFakeRequest(Some(x))

  ".store()" should {

    "store a file" in {
      SilhouetteTestUtils.running() { c =>
        Helpers.running(c.app) {
          val uuidGenerator = mock[UUIDGenerator]
          when(uuidGenerator.generate()).thenReturn("MyKey")
          val fileRepository = mock[FileRepositoryLike]
          when(fileRepository.store(any, any)).thenReturn(Future.successful(()))
          val controller = ControllerBuilder(c)
            .withFileRepository(fileRepository)
            .withUUIDGenerator(uuidGenerator)
            .withUser(c.user)
            .build()
          val fakeRequest = mkFakeRequest("foo")

          val result = controller.store()(fakeRequest)

          val fileArgCapture = ArgumentCaptor.forClass(classOf[File])
          status(result) mustEqual 200
          verify(fileRepository).store(eqTo("MyKey"), fileArgCapture.capture())
          Source.fromFile(fileArgCapture.getValue()).mkString mustEqual "foo"
        }
      }
    }

    "returns key" in {
      SilhouetteTestUtils.running() { c =>
        Helpers.running(c.app) {
          val uuidGenerator = mock[UUIDGenerator]
          when(uuidGenerator.generate()).thenReturn("MyKey")
          val controller = ControllerBuilder(c)
            .withUUIDGenerator(uuidGenerator)
            .withUser(c.user)
            .build()
          val result = controller.store()(mkFakeRequest("A"))
          contentAsJson(result) mustEqual Json.obj("key" -> "MyKey")
        }
      }
    }

    "gives user permission to the storeed file" in {
      SilhouetteTestUtils.running() { c =>
        Helpers.running(c.app) {
          val uuidGenerator = mock[UUIDGenerator]
          when(uuidGenerator.generate()).thenReturn("theKey")
          val permissionRegistry = mock[ResourcePermissionRegistryLike]
          when(permissionRegistry.grantAccess(c.user, "theKey")).thenReturn(Future.successful(()))
          val controller = ControllerBuilder(c)
            .withUUIDGenerator(uuidGenerator)
            .withPermissionRegistry(permissionRegistry)
            .withUser(c.user)
            .build()
          val request = mkFakeRequest("content")

          val result = controller.store()(request)

          status(result) mustEqual 200
          verify(permissionRegistry).grantAccess(c.user, "theKey")
        }
      }
    }

    "return error if missing file" in {
      SilhouetteTestUtils.running() { c =>
        Helpers.running(c.app) {
          val controller = ControllerBuilder(c).build()
          val fakeRequest = mkFakeRequest()

          val result = controller.store()(fakeRequest)

          status(result) mustEqual 400
        }
      }
    }
  }

  ".retrieve" should {

    "retrieve the file for the user from the repository" in {
      SilhouetteTestUtils.running() { c =>
        Helpers.running(c.app) {
          val file = TempFileWritter.write("content")
          val permissionRegistry = mock[ResourcePermissionRegistryLike]
          when(permissionRegistry.hasAccess(c.user, "theKey")).thenReturn(Future.successful(true))
          val fileRepository = mock[FileRepositoryLike]
          when(fileRepository.read("theKey")).thenReturn(Future.successful(file))
          val controller = ControllerBuilder(c)
            .withPermissionRegistry(permissionRegistry)
            .withFileRepository(fileRepository)
            .withUser(c.user)
            .build()
          val request = mkFakeRequest()

          val result = controller.retrieve("theKey")(request)

          status(result) mustEqual 200
          contentAsString(result) mustEqual "content"
        }
      }
    }

    "returns 404 if user does not has access" in {
      SilhouetteTestUtils.running() { c =>
        Helpers.running(c.app) {
          val file = TempFileWritter.write("content")
          val permissionRegistry = mock[ResourcePermissionRegistryLike]
          when(permissionRegistry.hasAccess(c.user, "theKey")).thenReturn(Future.successful(false))
          val fileRepository = mock[FileRepositoryLike]
          when(fileRepository.read("theKey")).thenReturn(Future.successful(file))
          val controller = ControllerBuilder(c)
            .withPermissionRegistry(permissionRegistry)
            .withFileRepository(fileRepository)
            .withUser(c.user)
            .build()
          val request = mkFakeRequest()

          val result = controller.retrieve("theKey")(request)

          status(result) mustEqual 404
        }
      }
    }

  }

}
