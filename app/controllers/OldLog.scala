package controllers

import java.net.InetAddress

import scala.concurrent.Future

import com.di.security.CryptoUtils
import org.joda.time.format.DateTimeFormat
import sun.misc.BASE64Decoder

import play.api._
import play.api.Play.current
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import play.api.libs.json._
import play.api.mvc._

import common.OldLogMetrics
import common.RakeAPI._
import models._

object OldLog extends Controller with OldLogMetrics {
  val logger = Logger("OldLog")

  val base64Decoder = new BASE64Decoder

  val rsaPublicKey = CryptoUtils.getPublicKey(OldRakeProperties.getProperty("encModulus"), OldRakeProperties.getProperty("encPubExponent"))

  val dateTimeFmt = DateTimeFormat.forPattern("yyyyMMddHHmmssSSS")

  val hostname = InetAddress.getLocalHost.getHostName

  val PROPERTIES_KEY = "properties"

  val readClientVersion = (__ \ PROPERTIES_KEY \ "rakeLibVersion").json.pick

  val readClientVersionSnake = (__ \ PROPERTIES_KEY \ "rake_lib_version").json.pick

  val readClientType = (__ \ PROPERTIES_KEY \ "rakeLib").json.pick

  val readClientTypeSnake = (__ \ PROPERTIES_KEY \ "rake_lib").json.pick

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

  def logTransform: (JsValue, String) => JsValue = {
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

  def makeUpdateJsValueReads(jsPath: JsPath, key: String, value: JsValue) = jsPath.json.update(__.read[JsObject].map {
    o => o ++ Json.obj(key -> value)
  })

  def makeUpdateJsObjectReads(jsPath: JsPath, key: String, value: JsObject) = jsPath.json.update(__.read[JsObject].map {
    o => o ++ Json.obj(key -> value)
  })

  def makeUpdateJsArrayReads(jsPath: JsPath, key: String, value: JsArray) = jsPath.json.update(__.read[JsObject].map {
    o => o ++ Json.obj(key -> value)
  })

  def makePropertiesBodyUpdateReads(key: String, value: String) = makeUpdateReads(__ \ PROPERTIES_KEY \ "_$body", key, value)

  def makePropertiesUpdateReads(key: String, value: String) = makeUpdateReads(__ \ PROPERTIES_KEY, key, value)

  def makeRootUpdateReads(key: String, value: String) = makeUpdateReads(__, key, value)

  def sendToKafka: JsValue => Boolean = {
    log =>
      val tokenCandidates = (log \ "token").asOpt[String]
      logger.trace(s"token $tokenCandidates")
      tokenCandidates match {
        case Some(token) =>
          val topic = Tokens.getTopic(token)
          getTokenMeter(topic).mark()
          loggerAPI.log(topic, log.toString())
        case _ =>
          noToken.mark()
          loggerAPI.log("NO_TOKEN", log.toString())
      }
  }

  def checkResults: Seq[Boolean] => Result = seq => seq.forall(p => p) match {
    case true => Ok("send Data")
    case false => BadRequest("Somethings are wrong")
  }

  def removePI: JsValue => JsValue = {
    log: JsValue =>
      val topic = Tokens.getTopic((log \ "token").as[String])
      val isInMdnSet = OldRakeProperties.getProperty("removeMdnAppender").split(",").to[Set].contains(topic)
      val isInDeviceIdSet = OldRakeProperties.getProperty("removeDeviceIdAppender").split(",").to[Set].contains(topic)
      isSnakeCase(log) match {
        case true => List(log)
          .map(j => if (isInMdnSet) j.transform(makeBothUpdateReads("mdn", "")).asOpt.getOrElse(j) else j)
          .map(j => if (isInDeviceIdSet) j.transform(makeBothUpdateReads("device_id", "")).asOpt.getOrElse(j) else j)
          .head
        case false => List(log)
          .map(j => if (isInMdnSet) j.transform(makeBothUpdateReads("mdn", "")).asOpt.getOrElse(j) else j)
          .map(j => if (isInDeviceIdSet) j.transform(makeBothUpdateReads("deviceId", "")).asOpt.getOrElse(j) else j)
          .head
      }
  }

  def encryptJsArray: JsArray => JsArray = { jsArray =>
    JsArray(jsArray.value.seq.map(encryptJsValue(_)))
  }

  def encryptJsObject: JsObject => JsObject = { jsObject =>
    JsObject(jsObject.value.map(p => (p._1, encryptJsValue(p._2))).toSeq)
  }

  def encryptJsValue: JsValue => JsValue = {
    case JsString(str) => Json.toJson(CryptoUtils.encrypt(str, rsaPublicKey))
    case JsNumber(num) => Json.toJson(CryptoUtils.encrypt(num.toString, rsaPublicKey))
    case JsBoolean(bool) => Json.toJson(CryptoUtils.encrypt(bool.toString, rsaPublicKey))
    case _ => JsNull
  }

  def encrypt(jsPath: JsPath): JsValue => JsValue = { log =>
    var vlog = log
    (vlog \ "_$encryptionFields").asOpt[JsArray] match {
      case Some(jsArray) =>
        jsArray.value.map(_.as[String]).foreach(key => vlog.transform((jsPath \ key).json.pick).asOpt match {
          case Some(json) if json.isInstanceOf[JsArray] =>
            vlog = vlog.transform(makeUpdateJsArrayReads(jsPath, key, encryptJsArray(json.asInstanceOf[JsArray]))).asOpt.getOrElse(vlog)
          case Some(json) if json.isInstanceOf[JsObject] =>
            vlog = vlog.transform(makeUpdateJsObjectReads(jsPath, key, encryptJsObject(json.asInstanceOf[JsObject]))).asOpt.getOrElse(vlog)
          case Some(json) if json.isInstanceOf[JsValue] =>
            vlog = vlog.transform(makeUpdateJsValueReads(jsPath, key, encryptJsValue(json))).asOpt.getOrElse(vlog)
          case None =>
        })
      case None =>
    }
    vlog
  }

  def processLog = Action.async {
    request =>
      requests.mark()
      Future {
        calReponseTime {
          logger.debug(request.headers.toString())
          request.body.asFormUrlEncoded match {
            case Some(body) =>
              (body.get("compress"), body.get("data")) match {
                case (Some(compressCodec), Some(data)) if 1 == compressCodec.length && 1 == data.length =>
                  val jsonData = base64Decoder.decodeBuffer(data.head)
                  val clientIp = request.remoteAddress
                  checkResults {
                    Json.parse(jsonData).asOpt[List[JsValue]].getOrElse[List[JsValue]](List()).map {
                      log => sendToKafka(encrypt(__ \ PROPERTIES_KEY \ "_$body")(encrypt(__ \ PROPERTIES_KEY)(removePI(logTransform(log, clientIp)))))
                    }
                  }
                case _ =>
                  logger.debug("no required fields or wrong data")
                  logger.debug(request.body.toString)
                  BadRequest("no required fields or wrong data")
              }
            case _ =>
              logger.debug("no body")
              logger.debug(request.body.toString)
              BadRequest("no body")
          }
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

}