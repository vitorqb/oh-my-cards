package v1.card.testUtils
import play.api.db.Database
import javax.sql.DataSource
import play.api.db.TransactionIsolationLevel
import java.sql.Connection

/**
  * A mocked db that only implement a bunch of useful methods
  */
trait MockDb extends Database {

  override def name: String = ???

  override def dataSource: DataSource = ???

  override def url: String = ???

  override def getConnection(): Connection = ???

  override def getConnection(autocommit: Boolean): Connection = ???

  override def withConnection[A](block: Connection => A): A = ???

  override def withConnection[A](autocommit: Boolean)(block: Connection => A): A = ???

  override def withTransaction[A](block: Connection => A): A = ???

  override def withTransaction[A](isolationLevel: TransactionIsolationLevel)(block: Connection => A): A = ???

  override def shutdown(): Unit = ???

}
