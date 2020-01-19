package v1.card

import javax.inject.Inject
import play.api.mvc._

class CardController @Inject()(val controllerComponents: ControllerComponents)
    extends BaseController {

  def index = Action { implicit request =>
    Ok("Gotcha!")
  }

}
