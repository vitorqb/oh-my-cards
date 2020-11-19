package services.filerepository

import scala.concurrent.Future
import play.api.Logger

import scala.concurrent.ExecutionContext
import com.backblaze.b2.client.B2StorageClientFactory
import play.api.libs.Files.SingletonTemporaryFileCreator
import com.backblaze.b2.client.contentHandlers.B2ContentFileWriter
import java.io.File
import com.backblaze.b2.client.structures.B2UploadFileRequest
import java.nio.file.Files
import com.backblaze.b2.client.contentSources.B2FileContentSource

/**
  * A common trait for all File Repositories.
  */
trait FileRepositoryLike {

  implicit val ec: ExecutionContext

  /**
    * Stores a file
    *  @param key - A unique key for the file (e.g. a uuid)
    *  @param file - The file
    */
  def store(key: String, file: File): Future[Unit]

  /**
    * Reads a file
    *  @param key - The file key.
    */
  def read(key: String): Future[File]

}

/**
  * An implementation for File Repository using b2 sdk.
  */
class B2FileRepository(
  config: BackblazeS3Config
)(
  implicit val ec: ExecutionContext
) extends FileRepositoryLike {

  private val logger = Logger(getClass)

  lazy val client = {
    logger.info("Initializing b2 client...")
    val out = B2StorageClientFactory
      .createDefaultFactory()
      .create(config.keyId, config.key, "OhMyCards")
    logger.info("Client initialized")
    out
  }

  lazy val bucketName = {
    val allowed = client.getAccountAuthorization().getAllowed()
    if (config.bucketId != allowed.bucketId)
      throw new RuntimeException("B2FileRepository does not has access to the bucket.")
    allowed.bucketName
  }

  override def store(key: String, file: File): Future[Unit] = Future {
    logger.info(f"Started upload with key ${key}.")
    val contentType = Files.probeContentType(file.toPath())
    val source = B2FileContentSource.builder(file).build()
    val req = B2UploadFileRequest.builder(config.bucketId, key, contentType, source).build()
    client.uploadSmallFile(req)
    logger.info(f"Finished upload with key ${key}")
    ()
  }
  override def read(key: String): Future[File] = Future {
    logger.info(f"Started download with key ${key}")
    val file = SingletonTemporaryFileCreator.create()
    val sink = B2ContentFileWriter.builder(file).build()
    client.downloadByName(bucketName, key, sink)
    logger.info(f"Finished download with key ${key}")
    file
  }

}

case class BackblazeS3Config(bucketId: String, keyId: String, key: String)

/**
  * A test implementation.
  */
class MockFileRepository()(implicit val ec: ExecutionContext) extends FileRepositoryLike {

  override def read(key: String): Future[File] = ???

  override def store(key: String, file: File): Future[Unit] = Future.successful(())

}
