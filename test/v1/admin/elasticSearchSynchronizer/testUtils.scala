package v1.admin.testUtils

import com.sksamuel.elastic4s.ElasticClient
import com.sksamuel.elastic4s.http.JavaClient
import com.sksamuel.elastic4s.ElasticProperties
import scala.util.Try
import com.google.inject.AbstractModule
import net.codingwell.scalaguice.ScalaModule
import v1.card.repository.CardElasticClientLike
import v1.card.elasticclient.CardElasticClientImpl


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

  protected def cleanIndex(): Unit = cleanIndex("_all")

  /**
    * Deletes and then creates an index on the test client.
    */
  protected def cleanIndex(indexName: String): Unit = {
    val query = deleteByQuery(indexName, matchAllQuery())
    client.execute(query).await
    refreshIdx(indexName)
  }

  protected def refreshIdx(): Unit = refreshIdx("_all")

  protected def refreshIdx(indexName: String): Unit = Try {
    val query = refreshIndex(indexName)
    client.execute(query).await
  }

  /**
    * Provides a Guice Module to inject the test client as dependency.
    */
  class TestEsFakeModule extends AbstractModule with ScalaModule {
    override def configure() = Seq(
      bind[ElasticClient].toInstance(client),
      bind[CardElasticClientLike].to[CardElasticClientImpl]
    )
  }

}
