package v1.admin.testUtils

import com.sksamuel.elastic4s.ElasticClient
import com.sksamuel.elastic4s.http.JavaClient
import com.sksamuel.elastic4s.ElasticProperties
import scala.util.Try
import com.google.inject.AbstractModule
import net.codingwell.scalaguice.ScalaModule


//!!!! TODO -> Move to package v1.testUtils
/**
  * Provides a ES client for testing purposes.
  */
trait TestEsClient {

  import com.sksamuel.elastic4s.ElasticDsl._

  /**
    * Reads from the environment the host and port used for tests.
    */
  lazy val elasticHost = sys.env.get("OHMYCARDS_TEST_ES_HOST").orNull
  lazy val elasticPort = sys.env.get("OHMYCARDS_TEST_ES_PORT").orNull

  /**
    * An instance of the test elastic client
    */
  lazy val client = ElasticClient(JavaClient(ElasticProperties(s"http://$elasticHost:$elasticPort")))

  /**
    * Deletes and index from the test client.
    */
  protected def deleteIdx(indexName: String): Unit = {
    Try {
      client.execute {
        deleteIndex(indexName)
      }.await
    }
  }

  /**
    * Creates an index on the test client.
    */
  protected def createIdx(name: String) = Try {
    client.execute {
      createIndex(name)
    }.await
  }

  /**
    * Deletes and then creates an index on the test client.
    */
  protected def cleanIndex(indexName: String): Unit = {
    deleteIdx(indexName)
    createIdx(indexName)
  }

  protected def refreshIdx(indexName: String): Unit = Try {
    client.execute {
      refreshIndex(indexName)
    }.await
  }

  /**
    * Provides a Guice Module to inject the test client as dependency.
    */
  class TestEsFakeModule extends AbstractModule with ScalaModule {
    override def configure() = bind[ElasticClient].toInstance(client)
  }

}
