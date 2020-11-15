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


class StaticAssetsControllerSpec
    extends PlaySpec
    with ScalaFutures
    with MockitoSugar
    with ArgumentMatchersSugar {

  implicit val ec: ExecutionContext = ExecutionContext.global

  case class ControllerBuilder(
    c: SilhouetteInjectorContext,
    f: Option[FileRepositoryLike] = None,
    u: Option[UUIDGeneratorLike] = None
  ) {
    def withFileRepository(f2: FileRepositoryLike): ControllerBuilder = copy(f=Some(f2))
    def withUUIDGenerator(u2: UUIDGeneratorLike): ControllerBuilder = copy(u=Some(u2))
    def build(): StaticAssetsController = {
      val f2 = f.getOrElse(mock[FileRepositoryLike])
      val u2 = u.getOrElse(mock[UUIDGeneratorLike])
      val components = Helpers.stubControllerComponents()
      new StaticAssetsController(components, c.silhouette, f2, u2)
    }
  }

  ".store()" should {

    "upload a file" in {
      SilhouetteTestUtils.running() { c =>
        Helpers.running(c.app) {
          val uuidGenerator = mock[UUIDGenerator]
          when(uuidGenerator.generate()).thenReturn("MyKey")
          val fileRepository = mock[FileRepositoryLike]
          when(fileRepository.upload(any, any)).thenReturn(Future.successful(()))
          val controller = ControllerBuilder(c)
            .withFileRepository(fileRepository)
            .withUUIDGenerator(uuidGenerator)
            .build()
          val file = TempFileWritter.write("foo")
          val filePart = MultipartFormData.FilePart("upload", "foo.txt", None, file)
          val formData = new MultipartFormData(Map(), Seq(filePart), Seq())
          val fakeRequest = FakeRequest().withMultipartFormDataBody(formData)

          val result = controller.store()(fakeRequest)

          val fileArgCapture = ArgumentCaptor.forClass(classOf[InputStream])
          status(result) mustEqual 200
          verify(fileRepository).upload(eqTo("MyKey"), fileArgCapture.capture())
          Source.fromInputStream(fileArgCapture.getValue()).mkString mustEqual "foo"
        }
      }
    }

    "return error if missing file" in {
      SilhouetteTestUtils.running() { c =>
        Helpers.running(c.app) {
          val key = "key"
          val controller = ControllerBuilder(c).build()
          val fakeRequest = FakeRequest()

          val result = controller.store()(fakeRequest)

          status(result) mustEqual 400
        }
      }
    }
  }

}
