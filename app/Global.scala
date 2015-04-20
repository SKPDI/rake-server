import play.api._

import common.RakeAPI._

object Global extends GlobalSettings {

  override def onStart(app: Application) {
    Logger.info("Application has started")
  }

  override def onStop(app: Application): Unit = {
    loggerAPI.close()
    Logger.info("Application has stopped")
  }
}