package common

import java.util.Properties

import com.rake.rakeapi.LoggerAPI

import play.api.Play.{configuration => playConf, current}

object RakeAPI {
  private val PREFIX_KEY="loggerapi"
  private val PREFIX_KEY_LENGTH=PREFIX_KEY.length
  private val properties = new Properties()
  playConf.keys.filter(_.startsWith(PREFIX_KEY)).foreach(k => properties.put(k.substring(PREFIX_KEY_LENGTH), playConf.getString(k)))

  val loggerAPI: LoggerAPI = LoggerAPI.getInstance(properties)
}