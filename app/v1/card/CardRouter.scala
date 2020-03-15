package v1.card

import javax.inject.Inject

import play.api.routing.SimpleRouter
import play.api.routing.Router.Routes
import play.api.routing.sird._

class CardRouter @Inject()(controller: CardController) extends SimpleRouter {

  override def routes: Routes = {
    case GET(p"/" ? q_?"page=$page" & q_?"pageSize=$pageSize") => controller.list(page, pageSize)
    case GET(p"/$id") => controller.get(id)
    case POST(p"/") => controller.create
    case POST(p"/$id") => controller.update(id)
    case DELETE(p"/$id") => controller.delete(id)
  }

}
