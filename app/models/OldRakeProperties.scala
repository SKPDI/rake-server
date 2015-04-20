package models

import java.sql.Timestamp

import play.api.db.slick.Config.driver.simple._

case class OldRakeProperty(pKey: String, pValue: String, updatedTime: Timestamp)

class OldRakeProperties(tag: Tag) extends Table[OldRakeProperty](tag, "tb_property") {
  def pKey = column[String]("pkey", O.PrimaryKey, O.NotNull, O.DBType("varchar(128)"))

  def pValue = column[String]("pvalue", O.Nullable, O.DBType("varchar(1024)"))

  def updatedTime = column[Timestamp]("update_time", O.NotNull)

  def * = (pKey, pValue, updatedTime) <>((OldRakeProperty.apply _).tupled, OldRakeProperty.unapply)

  /*
  +-------------+---------------+------+-----+-------------------+-----------------------------+
  | Field       | Type          | Null | Key | Default           | Extra                       |
  +-------------+---------------+------+-----+-------------------+-----------------------------+
  | pkey        | varchar(128)  | NO   | PRI | NULL              |                             |
  | pvalue      | varchar(1024) | YES  |     | NULL              |                             |
  | update_time | timestamp     | NO   |     | CURRENT_TIMESTAMP | on update CURRENT_TIMESTAMP |
  +-------------+---------------+------+-----+-------------------+-----------------------------+
  */
}

object OldRakeProperties {
  val oldRakeProperties = TableQuery[OldRakeProperties]
}