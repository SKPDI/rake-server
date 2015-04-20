package common

import java.util.concurrent.TimeUnit

import com.codahale.metrics.{Meter, ConsoleReporter, MetricRegistry}

trait Metrics {
  val metrics = new MetricRegistry

  val reporter = ConsoleReporter.forRegistry(metrics)
    .convertRatesTo(TimeUnit.SECONDS)
    .convertDurationsTo(TimeUnit.MILLISECONDS)
    .build()
//  reporter.start(10, TimeUnit.SECONDS)
}

trait OldLogMetrics extends Metrics {
  val requests = metrics.meter("requests")

  val invalidToken = metrics.meter("invalid_token")

  val noToken = metrics.meter("no_token")

  val tokenMeterMap = scala.collection.mutable.Map[String, Meter]()

  def getTokenMeter(token: String) = tokenMeterMap.getOrElseUpdate(token, metrics.meter(token))
}
