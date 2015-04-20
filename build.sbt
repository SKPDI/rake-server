import NativePackagerHelper._

name := """rake-server"""

version := "0.0.0-SNAPSHOT"

lazy val root = (project in file(".")).enablePlugins(PlayScala)

scalaVersion := "2.10.4"

libraryDependencies ++= Seq(
  jdbc,
  anorm,
  cache,
  ws,
  "com.typesafe.play" %% "play-slick" % "0.8.1",
  "com.rake.rakeapi" % "rake-core" % "0.1.0-SNAPSHOT" exclude("log4j", "log4j"),
  "org.slf4j" % "log4j-over-slf4j" % "1.7.6",
  "io.dropwizard.metrics" % "metrics-core" % "3.1.0",
  "joda-time" % "joda-time" % "2.7",
  "mysql" % "mysql-connector-java" % "5.1.34"
)

resolvers += Resolver.mavenLocal

resolvers += "SKP release" at "http://mvn.skplanet.com/content/repositories/releases"

resolvers += "SKP snapshot" at "http://mvn.skplanet.com/content/repositories/snapshots"

mappings in Universal ++= directory("bin")