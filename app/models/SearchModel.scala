package models

import org.joda.time.{DateTime,DateTimeZone}
import org.joda.time.format.DateTimeFormat
import play.api.cache.Cache
import play.api.{Logger,Play}
import play.api.libs.json.{Json,JsValue}
import play.api.Play.current
import scala.concurrent.{future,Future}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.Failure
import wabisabi.Client

object SearchModel {

  // 2 lazy 4 json
  val metricMapping = """
  {
    "metric": {
      "properties": {
        "metrics": {
          "type": "object"
        },
        "dateCreated": {
          "type": "date",
          "format": "basic_date_time_no_millis"
        }
      }
    }
  }
  """

  // XXX This should be configurable!
  val indexSettings = "{\"settings\": { \"index\": { \"number_of_shards\": 1 } } }"

  val dateFormatter = DateTimeFormat.forPattern("yyyyMMdd'T'HHmmss'Z'").withZoneUTC()
  val config = Play.configuration
  val esURL = config.getString("es.url")
  // Bad place for this, since it doesn't have reconnect logic or anything harumph.
  val esClient = new Client(esURL.getOrElse("http://localhost:9200"))

  // Prolly need a synchronous mode here XXX
  def index(client: String = "unknown", index: String, t: String, d: JsValue): Future[Any] = {

    // Create the data to store.
    var finalData = Json.obj(
      "client" -> client,
      "dateCreated" -> dateFormatter.print(new DateTime(DateTimeZone.UTC))
    ).toString
    Logger.debug(finalData)

    // First we must verify the index exists. We will automatically create indexes
    // on behalf of the user if we're allowed and if it doesn't exist. We'll use
    // the cache to save looking it up each time.
    val cacheKey = "index_presence||" + index
    
    val exists = if(Cache.getAs[Boolean](cacheKey).isDefined) {
      // Everything's here so return a Future that already has the value in it
      // So our map works later.
      future { "In Cache!" }
    } else {
      // We didn't use getOrElse on Cache because we want to do check
      // the ES index ourselves. We only want to set it if we find one!
      esClient.verifyIndex(name = index) map { res =>
        if(res.getStatusCode == 404) {
          Logger.info(s"New index: $index")
          // We don't have an index by the requested name. Make one!
          esClient.createIndex(name = index, settings = Some(indexSettings)) map { f =>
            // Await the health check so that we get confirmation of the operation.
            esClient.health(indices = Seq(index), waitForNodes = Some("1")) // XXX Number of shards should be configurable
          } map { f =>
            Logger.info(s"Putting mapping for: $index")
            esClient.putMapping(indices = Seq(index), `type` = t, body = metricMapping)
          }
        } else {
          Logger.info(s"Found index $index and cached it")
          Cache.set(cacheKey, true)
        }
      }
    }

    // We have to map here to block on the completion of the above. We can't
    // index until we've made the index!
    exists map { x =>
      Logger.debug("Indexed!")
      esClient.index(index = index, `type` = t, data = finalData)
    }
  }
}