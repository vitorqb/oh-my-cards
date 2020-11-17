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
import java.io.InputStream
import org.mockito.ArgumentMatchersSugar
import scala.concurrent.Future
import scala.io.Source
import testutils.silhouettetestutils.SilhouetteInjectorContext
import services.UUIDGenerator
import services.UUIDGeneratorLike
import play.api.mvc.AnyContent
import services.resourcepermissionregistry.ResourcePermissionRegistryLike


class StaticAssetsControllerSpec
    extends PlaySpec
    with ScalaFutures
    with MockitoSugar
    with ArgumentMatchersSugar {

  implicit val ec: ExecutionContext = ExecutionContext.global

  case class ControllerBuilder(
    c: SilhouetteInjectorContext,
    f: Option[FileRepositoryLike] = None,
    u: Option[UUIDGeneratorLike] = None,
    r: Option[ResourcePermissionRegistryLike] =  None
  ) {
    def withFileRepository(f2: FileRepositoryLike): ControllerBuilder = copy(f=Some(f2))
    def withUUIDGenerator(u2: UUIDGeneratorLike): ControllerBuilder = copy(u=Some(u2))
    def withPermissionRegistry(r2: ResourcePermissionRegistryLike): ControllerBuilder = copy(r=Some(r2))
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
      val components = Helpers.stubControllerComponents()
      new StaticAssetsController(components, c.silhouette, f2, u2, r2)
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
            .build()
          val fakeRequest = mkFakeRequest("foo")

          val result = controller.store()(fakeRequest)

          val fileArgCapture = ArgumentCaptor.forClass(classOf[InputStream])
          status(result) mustEqual 200
          verify(fileRepository).store(eqTo("MyKey"), fileArgCapture.capture())
          Source.fromInputStream(fileArgCapture.getValue()).mkString mustEqual "foo"
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

}
