package v1.staticassets

import utils.TempFileWritter

import org.scalatestplus.play._
import play.api.test.Helpers._
import play.api.test.Helpers
import play.api.test.FakeRequest
import org.scalatest.concurrent.ScalaFutures
import scala.concurrent.ExecutionContext
import org.mockito.MockitoSugar
import services.filerepository.FileRepositoryLike
import play.api.mvc.MultipartFormData
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchersSugar
import scala.concurrent.Future
import scala.io.Source
import testutils.silhouettetestutils.SilhouetteInjectorContext
import services.UUIDGeneratorLike
import play.api.mvc.AnyContent
import services.resourcepermissionregistry.ResourcePermissionRegistryLike
import akka.actor.ActorSystem
import akka.stream.Materializer
import java.io.File
import v1.auth.CookieUserIdentifierLike
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.Json


class StaticAssetsControllerSpec
    extends PlaySpec
    with ScalaFutures
    with MockitoSugar
    with ArgumentMatchersSugar {

  implicit val ec: ExecutionContext = ExecutionContext.global

  implicit val actorSystem = ActorSystem("test")
  implicit lazy val mat = Materializer(actorSystem)

  ".store()" should {

    "store a file" in new Context() { c =>
      when(c.permissionRegistry.grantAccess(any, any)).thenReturn(Future.successful(()))
      when(c.uuidGenerator.generate()).thenReturn("MyKey")
      when(c.fileRepository.store(any, any)).thenReturn(Future.successful(()))
      val fakeRequest = RequestMaker().gen("foo")

      val result = c.controller.store()(fakeRequest)

      val fileArgCapture = ArgumentCaptor.forClass(classOf[File])
      status(result) mustEqual 200
      verify(c.fileRepository).store(eqTo("MyKey"), fileArgCapture.capture())
      Source.fromFile(fileArgCapture.getValue()).mkString mustEqual "foo"
    }

    "returns key" in new Context() { c =>
      when(c.permissionRegistry.grantAccess(any, any)).thenReturn(Future.successful(()))
      when(c.uuidGenerator.generate()).thenReturn("MyKey")
      when(c.fileRepository.store(any, any)).thenReturn(Future.successful(()))

      val result = c.controller.store()(RequestMaker().gen("A"))

      contentAsJson(result) mustEqual Json.obj("key" -> "MyKey")
    }

    "gives user permission to the stored file" in new Context() { c =>
      when(c.uuidGenerator.generate()).thenReturn("theKey")
      when(c.permissionRegistry.grantAccess(c.user, "theKey")).thenReturn(Future.successful(()))
      when(c.fileRepository.store(any, any)).thenReturn(Future.successful(()))
      val request = RequestMaker().gen("content")

      val result = controller.store()(request)

      status(result) mustEqual 200
      verify(c.permissionRegistry).grantAccess(c.user, "theKey")
    }

    "return error if missing file" in new Context() { c =>
      val fakeRequest = RequestMaker().gen()

      val result = c.controller.store()(fakeRequest)

      status(result) mustEqual 400
    }
  }

  ".retrieve" should {

    "retrieve the file for the user from the repository" in new Context { c =>
      val file = TempFileWritter.write("content")
      when(c.cookieUserIdentifier.identifyUser(any)).thenReturn(Future.successful(Some(c.user)))
      when(c.permissionRegistry.hasAccess(c.user, "theKey")).thenReturn(Future.successful(true))
      when(c.fileRepository.read("theKey")).thenReturn(Future.successful(file))
      val request = RequestMaker().gen()

      val result = c.controller.retrieve("theKey")(request)

      status(result) mustEqual 200
      contentAsString(result) mustEqual "content"
    }

    "returns 401 if user not identifiable by cookies" in new Context { c =>
      when(c.cookieUserIdentifier.identifyUser(any)).thenReturn(Future.successful(None))
      val request = RequestMaker().gen()

      val result = c.controller.retrieve("theKey")(request)

      status(result) mustEqual 401
    }

    "returns 404 if user does not has access" in new Context { c =>
      val file = TempFileWritter.write("content")
      when(c.cookieUserIdentifier.identifyUser(any)).thenReturn(Future.successful(Some(c.user)))
      when(c.permissionRegistry.hasAccess(c.user, "theKey")).thenReturn(Future.successful(false))
      when(c.fileRepository.read("theKey")).thenReturn(Future.successful(file))
      val request = RequestMaker().gen()

      val result = c.controller.retrieve("theKey")(request)

      status(result) mustEqual 404
    }

  }

  case class Context() {
    lazy val silhouetteInjector = new SilhouetteInjectorContext {}
    lazy val app = new GuiceApplicationBuilder()
      .overrides(new silhouetteInjector.GuiceModule)
      .build()
    lazy val user = silhouetteInjector.user
    lazy val components = Helpers.stubControllerComponents()
    lazy val fileRepository = mock[FileRepositoryLike]
    lazy val uuidGenerator = mock[UUIDGeneratorLike]
    lazy val permissionRegistry = mock[ResourcePermissionRegistryLike]
    lazy val cookieUserIdentifier = mock[CookieUserIdentifierLike]
    lazy val controller = new StaticAssetsController(
      components,
      silhouetteInjector.silhouette(app),
      fileRepository,
      uuidGenerator,
      permissionRegistry,
      cookieUserIdentifier
    )

    def apply(block: Context => Any) = {
      Helpers.running(app) {
        block(this)
      }
    }
  }

  case class RequestMaker() {
    def gen(fileContent: Option[String]): FakeRequest[AnyContent] = {
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
    def gen(): FakeRequest[AnyContent] = gen(None)
    def gen(x: String): FakeRequest[AnyContent] = gen(Some(x))
  }

}
