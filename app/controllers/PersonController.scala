package controllers

import javax.inject._

import play.api.Logger
import play.api.libs.functional.syntax._
import play.api.libs.json._
import play.api.mvc._
import play.modules.reactivemongo._
import reactivemongo.api.{Cursor, ReadPreference}
import reactivemongo.play.json._
import reactivemongo.play.json.collection.JSONCollection

import scala.concurrent.{ExecutionContext, Future}

/**
  * A bit more complex controller using a Json Coast-to-coast approach. There is no model for Person and some data is created dynamically on creation
  * Input is directly converted to JsObject to be stored in MongoDB
  */
@Singleton
class PersonController @Inject()(val reactiveMongoApi: ReactiveMongoApi)(implicit exec: ExecutionContext) extends Controller with MongoController with ReactiveMongoComponents {

  def persons: JSONCollection = db.collection[JSONCollection]("persons")

  def create(name: String, age: Int) = Action.async {
    val json = Json.obj(
      "name" -> name,
      "age" -> age,
      "created" -> new java.util.Date().getTime())

    persons.insert(json).map(lastError =>
      Ok("Mongo LastError: %s".format(lastError)))
  }

  def createFromJson = Action.async(parse.json) { request =>
    import play.api.libs.json.Reads._
    /*
     * request.body is a JsValue.
     * There is no implicit Writes that turns this JsValue as a JsObject,
     * so you can call insert() with this JsValue.
     * (insert() takes a JsObject as parameter, or anything that can be
     * turned into a JsObject using a Writes.)
     */
    val transformer: Reads[JsObject] =
      Reads.jsPickBranch[JsString](__ \ "name") and
        Reads.jsPickBranch[JsNumber](__ \ "age") and
        Reads.jsPut(__ \ "created", JsNumber(new java.util.Date().getTime())) reduce

    request.body.transform(transformer).map { result =>
      persons.insert(result).map { lastError =>
        Logger.debug(s"Successfully inserted with LastError: $lastError")
        Created
      }
    }.getOrElse(Future.successful(BadRequest("invalid json")))
  }

  def findByName(name: String) = Action.async {
    // let's do our query
    val cursor: Cursor[JsObject] = persons.
      // find all people with name `name`
      find(Json.obj("name" -> name)).
      // sort them by creation date
      sort(Json.obj("created" -> -1)).
      // perform the query and get a cursor of JsObject
      cursor[JsObject](ReadPreference.primary)

    // everything's ok! Let's reply with a JsValue
    cursor.collect[List]().map { persons =>
      Ok(Json.toJson(persons))
    }
  }
}
