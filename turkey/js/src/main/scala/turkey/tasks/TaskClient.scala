package turkey.tasks

import scalajs.js
import scalajs.js.JSApp
import org.scalajs.jquery.jQuery
import org.scalajs.dom

import upickle.default._

/** Superclass for an implementation of a client/interface for a turk task.
  * Gives access by field to all of the information written into the `TaskPage` on the server.
  */
abstract class TaskClient[Prompt : Reader, Response : Writer] {
  import scala.scalajs.js.Dynamic.global

  lazy val assignmentId: String = {
    global.turkSetAssignmentID()
    jQuery("#assignmentId").attr("value").get
  }

  lazy val isNotAssigned = assignmentId == "ASSIGNMENT_ID_NOT_AVAILABLE"

  lazy val taskKey: String = {
    read[String](jQuery(s"#$taskKeyLabel").attr("value").get)
  }

  lazy val serverDomain: String = {
    read[String](jQuery(s"#$serverDomainLabel").attr("value").get)
  }

  lazy val httpPort: Int = {
    read[Int](jQuery(s"#$httpPortLabel").attr("value").get)
  }

  lazy val httpsPort: Int = {
    read[Int](jQuery(s"#$httpsPortLabel").attr("value").get)
  }

  lazy val websocketUri: String = {
    val isHttps = dom.document.location.protocol == "https:"
    val wsProtocol = if (isHttps) "wss" else "ws"
    val serverPort = if(isHttps) httpsPort else httpPort
    s"$wsProtocol://$serverDomain:$serverPort/websocket?taskKey=$taskKey"
  }

  lazy val prompt: Prompt = {
    read[Prompt](jQuery(s"#$promptLabel").attr("value").get)
  }

  lazy val externalSubmitURL: String = {
    jQuery(s"form#$mturkFormLabel").attr("action").get
  }

  def setResponse(response: Response): Unit = {
    jQuery(s"#$responseLabel").attr("value", write(response))
  }

  def main(): Unit
}
