package v1.card.testUtils
import play.api.db.Database
import services.UUIDGeneratorLike
import v1.card._
import v1.card.cardrepositorycomponents.CardRepositoryComponentsLike
import v1.card.cardrepositorycomponents.CardRepositoryComponents
import services.referencecounter.{ReferenceCounterLike,ReferenceCounter}
import org.mockito.MockitoSugar
import javax.sql.DataSource
import play.api.db.TransactionIsolationLevel
import java.sql.Connection
import com.mohiva.play.silhouette.api.util.{Clock=>SilhouetteClock}

/**
  * A fixture factory for the CardRepositoryComponentsLike that defaults to mock everything.
  */
case class ComponentsBuilder(
  val db: Option[Database] = None,
  val uuidGenerator: Option[UUIDGeneratorLike] = None,
  val refGenerator: Option[ReferenceCounterLike] = None,
  val clock: Option[SilhouetteClock] = None
) extends MockitoSugar {

  def withDb(db: Database) = copy(db=Some(db))
  def withUUIDGenerator(uuidGenerator: UUIDGeneratorLike) = copy(uuidGenerator=Some(uuidGenerator))
  def withRefGenerator(refGenerator: ReferenceCounterLike) = copy(refGenerator=Some(refGenerator))
  def withClock(clock: SilhouetteClock) = copy(clock=Some(clock))

  /**
    * Shortcut to construct a Components from a CreateContext.
    */
  def withContext(context: CardCreationContext) = {
    val uuidGenerator_ = mock[UUIDGeneratorLike]
    when(uuidGenerator_.generate()).thenReturn(context.id)
    val refGenerator_ = mock[ReferenceCounterLike]
    when(refGenerator_.nextRef()).thenReturn(context.ref)
    val clock_ = mock[SilhouetteClock]
    when(clock_.now).thenReturn(context.now)

    this.withClock(clock_).withUUIDGenerator(uuidGenerator_).withRefGenerator(refGenerator_)
  }

  def build(): CardRepositoryComponentsLike = {
    val db_ = db.getOrElse(mock[Database])
    new CardRepositoryComponents(
      db_,
      uuidGenerator.getOrElse(mock[UUIDGeneratorLike]),
      refGenerator.getOrElse(new ReferenceCounter(db_)),
      clock.getOrElse(mock[SilhouetteClock])
    )
  }
}


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
