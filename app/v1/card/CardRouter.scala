package v1.card

import javax.inject.Inject

import play.api.routing.SimpleRouter
import play.api.routing.Router.Routes
import play.api.routing.sird._

class CardRouter @Inject()(controller: CardController) extends SimpleRouter {

  val prefix = "/v1/cards"

  override def routes: Routes = {
    case GET(p"/") => controller.index
  }

}
