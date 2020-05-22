package v1

import play.api.routing.SimpleRouter
import play.api.routing.Router.Routes
import play.api.routing.sird._
import com.google.inject.Inject
import v1.admin.AdminController
import play.api.Configuration

class DefaultRouter @Inject()(
  controller: AdminController,
  config: Configuration
) extends SimpleRouter {

  val customAdminUrl = config.get[String]("adminDashboardSecretUrl")

  override def routes: Routes = {
    case POST(p"/$url/synchronize-ES") if url == customAdminUrl => {
      controller.index()
    }
  }

}
