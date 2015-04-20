package controllers

import java.net.InetAddress
import java.util.Properties

import scala.concurrent.Future

import org.joda.time.format.DateTimeFormat
import sun.misc.BASE64Decoder

import play.api._
import play.api.Play.current
import play.api.db.slick._
import play.api.db.slick.Config.driver.simple._
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import play.api.libs.json._
import play.api.mvc._
import play.core.parsers.FormUrlEncodedParser

import common.OldLogMetrics
import common.RakeAPI._
import models._


object OldLog extends Controller with OldLogMetrics {
  val logger = Logger("OldLog")

  val tokenTopicMap = scala.collection.mutable.Map[String, String]()

  val properties = new Properties()

  val base64Decoder = new BASE64Decoder

  val dateTimeFmt = DateTimeFormat.forPattern("yyyyMMddHHmmssSSS")

  val hostname = InetAddress.getLocalHost.getHostName

  val PROPERTIES_KEY = "properties"

  val readClientVersion = (__ \ PROPERTIES_KEY \ 'rakeLibVersion).json.pick

  val readClientVersionSnake = (__ \ PROPERTIES_KEY \ 'rake_lib_version).json.pick

  val readClientType = (__ \ PROPERTIES_KEY \ 'rakeLib).json.pick

  val readClientTypeSnake = (__ \ PROPERTIES_KEY \ 'rake_lib).json.pick

  def getValue[A <: JsValue](json: JsValue, r1: Reads[A], r2: Reads[A]): Option[A] = {
    json.transform(r1).asOpt match {
      case Some(v) => Some(v)
      case None => json.transform(r2).asOpt match {
        case Some(v) => Some(v)
        case None => None
      }
    }
  }

  def isSnakeCase: JsValue => Boolean = { log =>
    val clientVersion = getValue(log, readClientVersion, readClientVersionSnake)
    val clientType = getValue(log, readClientType, readClientTypeSnake)
    (clientVersion, clientType) match {
      case (Some(v), Some(t)) =>
        val cVersion = v.as[String]
        val cType = t.as[String]

        val cVersionList = cVersion.split("_").toList
        logger.trace(s"Client version length: [${cVersionList.length}], value: [${cVersionList.tail.head}]")
        2 == cVersionList.length match {
          case false => false
          case true =>
            cType.trim.toLowerCase match {
              case "android" => eqAndGt(cVersionList(1), "c0.3.3")
              case "iphone" => eqAndGt(cVersionList(1), "c1.7")
              case "web" => eqAndGt(cVersionList(1), "c1.2")
              case wrongType =>
                logger.error(s"Client Type is not valid: $wrongType")
                false
            }
        }
      case _ => false // Very old version
    }
  }

  def eqAndGt(a: String, b: String): Boolean = {
    val aList = a.replace("c", "").split("\\.").toList
    val bList = b.replace("c", "").split("\\.").toList

    def eqAndGtRec(a: List[String], b: List[String]): Boolean = {
      (a.nonEmpty, b.nonEmpty) match {
        case (true, false) => true
        case (false, true) => false
        case (false, false) => true
        case (true, true) => a.head == b.head match {
          case true => eqAndGtRec(a.tail, b.tail)
          case false =>
            try {
              a.head.toInt > b.head.toInt
            } catch {
              case nfe: NumberFormatException =>
                logger.error(s"Version numbers are wrong: [${a.head}], [${b.head}]")
                false
              case e: Exception =>
                logger.error(s"Wrong!!")
                false
            }
        }
      }
    }

    eqAndGtRec(aList, bList)
  }

  val logTransform: (JsValue, String) => JsValue = {
    (log, clientIp) =>
      val recvDateTime = dateTimeFmt.print(System.currentTimeMillis())
      val tokenValue = getValueFromRootAndProperties(log, "token", "NO_TOKEN")
      log.transform(makePropertiesUpdateReads("ip", clientIp)).map {
        json => isSnakeCase(log) match {
          case true => json.transform {
            makeBothUpdateReads("recv_time", recvDateTime) andThen
              makeBothUpdateReads("recv_host", hostname) andThen
              makeBothUpdateReads("token", tokenValue) andThen
              makeBothUpdateReads("base_time", getValueFromRootAndProperties(json, "base_time", "")) andThen
              makeBothUpdateReads("local_time", getValueFromRootAndProperties(json, "local_time", ""))
          }.asOpt.getOrElse(json)
          case false => json.transform {
            makeBothUpdateReads("recvTimeStamp", recvDateTime) andThen
              makeBothUpdateReads("recvHost", hostname) andThen
              makeBothUpdateReads("token", tokenValue) andThen
              makeBothUpdateReads("baseTime", getValueFromRootAndProperties(json, "baseTime", "")) andThen
              makeBothUpdateReads("localTime", getValueFromRootAndProperties(json, "localTime", ""))
          }.asOpt.getOrElse {
            logger.error(s"logTransform failed: $log")
            json
          }
        }
      }.getOrElse {
        logger.error(s"logTransform failed: $log")
        log
      }
  }

  def getValueFromRootAndProperties(json: JsValue, key: String, default: String = "") =
    (json \ key).asOpt[String].getOrElse((json \ PROPERTIES_KEY \ key).asOpt[String].getOrElse(default))

  def makeBothUpdateReads(key: String, value: String) = makeRootUpdateReads(key, value) andThen makePropertiesUpdateReads(key, value)

  def makeUpdateReads(jsPath: JsPath, key: String, value: String) = jsPath.json.update(__.read[JsObject].map {
    o => o ++ Json.obj(key -> value)
  })

  def makePropertiesUpdateReads(key: String, value: String) = makeUpdateReads(__ \ PROPERTIES_KEY, key, value)

  def makeRootUpdateReads(key: String, value: String) = makeUpdateReads(__, key, value)

  val sendToKafka: JsValue => Boolean = {
    log =>
      val tokenCandidates = (log \ "token").asOpt[String]
      logger.trace(s"token $tokenCandidates")
      tokenCandidates match {
        case Some(token) if tokenTopicMap.contains(token) =>
          getTokenMeter(token).mark()
          loggerAPI.log(token, log.toString())
        case Some(token) if !tokenTopicMap.contains(token) =>
          invalidToken.mark()
          loggerAPI.log("INVALID", log.toString())
        case _ =>
          noToken.mark()
          loggerAPI.log("NO_TOKEN", log.toString())
      }
  }

  val checkResults: Seq[Boolean] => Result = seq => seq.forall(p => p) match {
    case true => Ok("send Data")
    case false => BadRequest("Somethings are wrong")
  }

  def processLog = Action.async {
    request =>
      requests.mark()
      Future {
        logger.debug(request.headers.toString())
        request.body.asText match {
          case Some(body) =>
            val urlDecodedData = FormUrlEncodedParser.parse(body)
            (urlDecodedData.get("compress"), urlDecodedData.get("data")) match {
              case (Some(compressCodec), Some(data)) if 1 == compressCodec.length && 1 == data.length =>
                val jsonData = base64Decoder.decodeBuffer(data.head)
                val clientIp = request.remoteAddress
                checkResults {
                  Json.parse(jsonData).asOpt[List[JsValue]].getOrElse[List[JsValue]](List()).map {
                    log => sendToKafka(logTransform(log, clientIp))
                  }
                }
              case _ => BadRequest("no required fields or wrong data")
            }
          case _ => BadRequest("no body")
        }
      }
  }

  def Logging[A](action: Action[A]) = Action.async(action.parser) {
    request =>
      logger.debug("request in")
      val returnAction = action(request)
      logger.debug("request out")
      returnAction
  }

  def logTrack = Logging(processLog)

  def updateTokenTopicMap = DBAction { implicit rs =>
    val updatedList = scala.collection.mutable.HashSet[String]()
    updatedList.clear()
    Tokens.tokens.list.map { tokenRow: Token =>
      val token = tokenRow.token
      val topic = tokenRow.writerKey
      logger.debug(s"token: [$token], topic: [$topic]")
      if (!tokenTopicMap.contains(token) || !tokenTopicMap.getOrElse(token, "").equals(topic)) {
        tokenTopicMap.synchronized {
          tokenTopicMap.put(token, topic)
          logger.info(s"Updated. token: [$token], topic: [$topic]")
          updatedList.add(token)
        }
      }
    }
    Ok(updatedList.mkString(","))
  }

  def updateProperty = DBAction { implicit rs =>
    val updatedList = scala.collection.mutable.HashSet[String]()
    updatedList.clear()
    OldRakeProperties.oldRakeProperties.list.map { orp: OldRakeProperty =>
      val key = orp.pKey
      val value = orp.pValue
      logger.debug(s"key: [$key], value: [$value]")
      if (!properties.containsKey(key) || !properties.getProperty(key, "").equals(value)) {
        properties.synchronized {
          properties.put(key, value)
          logger.info(s"Updated. key: [$key], value: [$value]")
          updatedList.add(key)
        }
      }
    }
    Ok(updatedList.mkString(","))
  }


}