package controllers

import play.api._
import play.api.libs.json.Json
import play.api.mvc._
import models.SearchModel

object Antenna extends Controller {

  def write(client: String, collection: String = "metrics", t: String = "metric") = Action { request =>
    request.body.asJson.map({ json =>
      
      // Get the current time.
      SearchModel.index(client = client, index = collection, t = t, d = json)
      Ok(Json.obj("status" -> "OK"))
    }).getOrElse({
      BadRequest("Expecting Json data")
    })
  }
}