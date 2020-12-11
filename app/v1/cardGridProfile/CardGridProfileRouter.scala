package v1.cardGridProfile

import com.google.inject.Inject
import play.api.routing.SimpleRouter
import play.api.routing.Router.Routes
import play.api.routing.sird._

class CardGridProfileRouter @Inject() (controller: CardGridProfileController)
    extends SimpleRouter {

  override def routes: Routes = {
    case GET(p"/$name")  => controller.read(name)
    case POST(p"")       => controller.create
    case POST(p"/$name") => controller.update(name)
  }

}
