package services.filerepository

import scala.concurrent.Future
import java.io.InputStream
import play.api.Logger

import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.auth.credentials.AwsSessionCredentials
import java.net.URI
import scala.concurrent.ExecutionContext

/**
  * A common trait for all File Repositories.
  */
trait FileRepositoryLike {

  implicit val ec: ExecutionContext

  /**
    * Uploads and saves a file.
    *  @param key - A unique key for the file (e.g. a uuid)
    *  @param file - The file
    */
  //!!!! TODO Rename to store
  def upload(key: String, file: InputStream): Future[Unit]
}

/**
  * An implementation for File Repositories that uses s3+backblaze.
  */
class BackblazeS3FileRepository(
  config: BackblazeS3Config
)(
  implicit val ec: ExecutionContext
) extends FileRepositoryLike {

  private val logger = Logger(getClass)

  def upload(key: String, file: InputStream): Future[Unit] = Future {
    logger.info(f"Started upload with key ${key}.")
    val bucket = config.bucket
    val region = Region.of(config.region)
    val accessKey = config.accessKey
    val secretAccessKey = config.secretAccessKey
    val endpoint = URI.create(config.endpoint)
    val sessionCredentials = AwsSessionCredentials.create(accessKey, secretAccessKey, "")
    val credentialsProvider = StaticCredentialsProvider.create(sessionCredentials)
    val putRequest = PutObjectRequest.builder().bucket(bucket).key(key).build()
    val requestBody = RequestBody.fromBytes(file.readAllBytes())
    S3Client
      .builder()
      .region(region)
      .credentialsProvider(credentialsProvider)
      .endpointOverride(endpoint)
      .build()
      .putObject(putRequest, requestBody)
    logger.info(f"Finished upload with key ${key}")
    ()
  }
}

case class BackblazeS3Config(
  bucket: String,
  region: String,
  accessKey: String,
  secretAccessKey: String,
  endpoint: String,
)

/**
  * A test implementation.
  */
class MockFileRepository()(implicit val ec: ExecutionContext) extends FileRepositoryLike {

  def upload(key: String, file: InputStream): Future[Unit] = Future.successful(())

}
