import controllers.OldLog
import org.specs2.mutable._
import org.specs2.runner._
import org.junit.runner._

import play.api.test._
import play.api.test.Helpers._

/**
 * Add your spec here.
 * You can mock out a whole application including requests, plugins etc.
 * For more information, consult the wiki.
 */
@RunWith(classOf[JUnitRunner])
class ApplicationSpec extends Specification {

  "Application" should {

    "send 404 on a bad request" in new WithApplication {
      route(FakeRequest(GET, "/boum")) must beNone
    }

    "render the index page" in new WithApplication {
      val home = route(FakeRequest(GET, "/")).get

      status(home) must equalTo(OK)
      contentType(home) must beSome.which(_ == "text/html")
      contentAsString(home) must contain("Your new application is ready.")

      val data = "compress=plain&data=W3siXyRzY2hlbWFJZCI6IjU1MjYxOTA2ZTRiMGVmNTZjMmMxOGVkMiIsIl8kZW5jcnlwdGlvbkZpZWxkcyI6W10sInByb3BlcnRpZXMiOnsibG9nX3ZlcnNpb24iOiIxNS4wNC4wOToxLjUuMjY6NTciLCJuZXR3b3JrX3R5cGUiOiJOT1QgV0lGSSIsImFwcF92ZXJzaW9uIjoiMS4wIiwic2NyZWVuX3dpZHRoIjoyNTYwLCJkZXZpY2VfaWQiOiJhOGNhYzdhMWNhNTgwNzY4IiwicmVzb2x1dGlvbiI6IjE0NDAqMjU2MCIsInJlY3ZfaG9zdCI6IiIsImlwIjoiIiwib3NfdmVyc2lvbiI6IjQuNC40IiwicmVjdl90aW1lIjoiIiwibG9jYWxfdGltZSI6IjIwMTUwNDEzMTcyMzQ2MjgxIiwibGFuZ3VhZ2VfY29kZSI6IktSIiwiZGV2aWNlX21vZGVsIjoiU00tTjkxMFMiLCJyYWtlX2xpYl92ZXJzaW9uIjoicjAuNS4wX2MwLjMuMTQiLCJvc19uYW1lIjoiQW5kcm9pZCIsInRva2VuIjoiMTdkN2M2MzczNWQxZDFlYzgxYTk3ZTRjNDRkNDdhY2M4NDIwZWQxNSIsInJha2VfbGliIjoiYW5kcm9pZCIsIm1hbnVmYWN0dXJlciI6InNhbXN1bmciLCJhY3Rpb24iOiJhY3Rpb240IiwiXyRib2R5Ijp7ImZpZWxkMSI6ImZpZWxkMSB2YWx1ZSIsImZpZWxkNCI6ImZpZWxkNCB2YWx1ZSIsImZpZWxkMyI6ImZpZWxkMyB2YWx1ZSJ9LCJiYXNlX3RpbWUiOiIyMDE1MDQxMzE3MjM0NjI4MSIsImNhcnJpZXJfbmFtZSI6IlNLVGVsZWNvbSIsInNjcmVlbl9oZWlnaHQiOjE0NDB9LCJfJGZpZWxkT3JkZXIiOnsibmV0d29ya190eXBlIjoxMiwibG9nX3ZlcnNpb24iOjIwLCJzY3JlZW5fd2lkdGgiOjksImFwcF92ZXJzaW9uIjoxNiwiZGV2aWNlX2lkIjozLCJyZXNvbHV0aW9uIjo4LCJyZWN2X2hvc3QiOjE1LCJyZWN2X3RpbWUiOjIsIm9zX3ZlcnNpb24iOjcsImlwIjoxNCwibG9jYWxfdGltZSI6MSwiZGV2aWNlX21vZGVsIjo0LCJsYW5ndWFnZV9jb2RlIjoxMywicmFrZV9saWJfdmVyc2lvbiI6MTgsIm9zX25hbWUiOjYsInRva2VuIjoxOSwibWFudWZhY3R1cmVyIjo1LCJyYWtlX2xpYiI6MTcsImFjdGlvbiI6MjEsIl8kYm9keSI6MjIsImJhc2VfdGltZSI6MCwiY2Fycmllcl9uYW1lIjoxMSwic2NyZWVuX2hlaWdodCI6MTB9LCJfJHByb2plY3RJZCI6InByb2plY3RJZCJ9XQ%3D%3D"
//      val result = "[{\"_$schemaId\":\"55261906e4b0ef56c2c18ed2\",\"_$encryptionFields\":[],\"properties\":{\"log_version\":\"15.04.09:1.5.26:57\",\"network_type\":\"NOT WIFI\",\"app_version\":\"1.0\",\"screen_width\":2560,\"device_id\":\"a8cac7a1ca580768\",\"resolution\":\"1440*2560\",\"recv_host\":\"\",\"ip\":\"\",\"os_version\":\"4.4.4\",\"recv_time\":\"\",\"local_time\":\"20150413172346281\",\"language_code\":\"KR\",\"device_model\":\"SM-N910S\",\"rake_lib_version\":\"r0.5.0_c0.3.14\",\"os_name\":\"Android\",\"token\":\"17d7c63735d1d1ec81a97e4c44d47acc8420ed15\",\"rake_lib\":\"android\",\"manufacturer\":\"samsung\",\"action\":\"action4\",\"_$body\":{\"field1\":\"field1 value\",\"field4\":\"field4 value\",\"field3\":\"field3 value\"},\"base_time\":\"20150413172346281\",\"carrier_name\":\"SKTelecom\",\"screen_height\":1440},\"_$fieldOrder\":{\"network_type\":12,\"log_version\":20,\"screen_width\":9,\"app_version\":16,\"device_id\":3,\"resolution\":8,\"recv_host\":15,\"recv_time\":2,\"os_version\":7,\"ip\":14,\"local_time\":1,\"device_model\":4,\"language_code\":13,\"rake_lib_version\":18,\"os_name\":6,\"token\":19,\"manufacturer\":5,\"rake_lib\":17,\"action\":21,\"_$body\":22,\"base_time\":0,\"carrier_name\":11,\"screen_height\":10},\"_$projectId\":\"projectId\"}]"
      val result ="send Data"
      val re = route(FakeRequest(POST, "/log/track").withTextBody(data)).get
      contentAsString(re) must contain(result)
    }
  }

  "Functional Test" should {
    "eqAndGet" in new WithApplication {
//      OldLog.eqAndGt("0.0.1", "0.0") must equalTo(true)
//      OldLog.eqAndGt("0.0.1", "0.0.1") must equalTo(true)
//      OldLog.eqAndGt("0.0.1", "0.0.2") must equalTo(false)
//      OldLog.eqAndGt("0.1.0", "0.0.10") must equalTo(true)
//      OldLog.eqAndGt("0.1.0", "0.2.10") must equalTo(false)
      OldLog.eqAndGt("c0.3.14", "c0.3.3") must equalTo(true)
    }
  }
}
