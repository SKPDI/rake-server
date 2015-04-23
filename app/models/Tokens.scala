package models

import java.sql.Timestamp

import play.api.Play.current
import play.api.db.slick.Config.driver.simple._

case class Token(accessTokenId: Int, userId: String, token: String, createTime: Timestamp,
                 expireTime: Timestamp, appender: String, file: String, conversionPattern: String,
                 datePattern: String, encoding: String, used: Int, updatedTime: Timestamp,
                 writerKey: String, writer: String)

class Tokens(tag: Tag) extends Table[Token](tag, "tb_access_token") {
  def accessTokenId = column[Int]("access_token_id", O.DBType("int(11)"), O.NotNull, O.PrimaryKey, O.AutoInc)

  def userId = column[String]("user_id", O.DBType("varchar(128)"), O.NotNull)

  def token = column[String]("token", O.DBType("varchar(128)"), O.NotNull)

  def createTime = column[Timestamp]("create_time", O.Nullable)

  def expireTime = column[Timestamp]("expire_time", O.Nullable)

  def appender = column[String]("appender", O.DBType("varchar(128)"), O.Nullable)

  def file = column[String]("file", O.DBType("varchar(512)"), O.Nullable)

  def conversionPattern = column[String]("conversion_pattern", O.DBType("varchar(512)"), O.Nullable, O.Default("%m%n"))

  def datePattern = column[String]("date_pattern", O.DBType("varchar(512)"), O.Nullable, O.Default("'.'yyyy-MM-dd"))

  def encoding = column[String]("encoding", O.DBType("varchar(32)"), O.Nullable, O.Default("UTF-8"))

  def used = column[Int]("used", O.DBType("int(11)"), O.Nullable, O.Default(1))

  def updatedTime = column[Timestamp]("update_time", O.NotNull)

  def writerKey = column[String]("writer_key", O.DBType("varchar(64)"), O.Nullable)

  def writer = column[String]("writer", O.DBType("varchar(64)"), O.Nullable)

  def * = (accessTokenId, userId, token, createTime, expireTime, appender, file, conversionPattern, datePattern, encoding,
    used, updatedTime, writerKey, writer) <>((Token.apply _).tupled, Token.unapply)

  /*
    mysql> desc tb_access_token;
  +--------------------+--------------+------+-----+-------------------+-----------------------------+
  | Field              | Type         | Null | Key | Default           | Extra                       |
  +--------------------+--------------+------+-----+-------------------+-----------------------------+
  | access_token_id    | int(11)      | NO   | PRI | NULL              | auto_increment              |
  | user_id            | varchar(128) | NO   | MUL | NULL              |                             |
  | token              | varchar(128) | NO   |     | NULL              |                             |
  | create_time        | datetime     | YES  |     | NULL              |                             |
  | expire_time        | datetime     | YES  |     | NULL              |                             |
  | appender           | varchar(128) | YES  |     | NULL              |                             |
  | file               | varchar(512) | YES  |     | NULL              |                             |
  | conversion_pattern | varchar(512) | YES  |     | %m%n              |                             |
  | date_pattern       | varchar(512) | YES  |     | '.'yyyy-MM-dd     |                             |
  | encoding           | varchar(32)  | YES  |     | UTF-8             |                             |
  | used               | int(11)      | YES  |     | 1                 |                             |
  | update_time        | timestamp    | NO   |     | CURRENT_TIMESTAMP | on update CURRENT_TIMESTAMP |
  | writer_key         | varchar(64)  | YES  |     | NULL              |                             |
  | writer             | varchar(64)  | YES  |     | NULL              |                             |
  +--------------------+--------------+------+-----+-------------------+-----------------------------+
  14 rows in set (0.01 sec)
  */
}

object Tokens {
  val tokens = TableQuery[Tokens]

  val tokenTopicMap = scala.collection.mutable.Map[String, String]()

  play.api.db.slick.DB.withSession { implicit session =>
    tokens.list.map(t => tokenTopicMap.put(t.token, t.writerKey))
  }

  def getTopic(token: String) = play.api.db.slick.DB.withSession { implicit session =>
    tokenTopicMap.getOrElseUpdate(
      token,
      tokens.filter(t => t.token === token).list match {
        case x :: xs => x.writerKey
        case _ => "INVALID"
      }
    )
  }
}
