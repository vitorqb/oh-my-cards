package v1.card

import javax.inject.Inject

import play.api.routing.SimpleRouter
import play.api.routing.Router.Routes
import play.api.routing.sird._
import utils.resourceidmatcher.{ResourceIdMatcher, UUID, IntRef, UnknownRef}
import play.api.mvc.Results

class CardRouter @Inject() (controller: CardController)
    extends SimpleRouter
    with Results {

  override def routes: Routes = {
    case GET(p"/" ? q_?"page=$page" & q_?"pageSize=$pageSize") =>
      controller.list()
    case GET(p"/$id/history") => controller.getHistory(id)
    case GET(p"/$id") =>
      ResourceIdMatcher.run(id) match {
        case UUID(uuid)   => controller.get(uuid)
        case IntRef(ref)  => controller.getByRef(ref)
        case UnknownRef() => controller.notFound()
      }
    case POST(p"/")      => controller.create
    case POST(p"/$id")   => controller.update(id)
    case DELETE(p"/$id") => controller.delete(id)
  }

}
