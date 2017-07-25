package turkey

/** Provides classes for managing tasks on Mechanical Turk.
  */
package object tasks {
  sealed trait HeartbeatingWebSocketMessage[+A]
  case object Heartbeat extends HeartbeatingWebSocketMessage[Nothing]
  case class WebSocketMessage[A](content: A) extends HeartbeatingWebSocketMessage[A]

  val responseLabel = "response"
  val promptLabel = "prompt"
  val serverDomainLabel = "serverDomain"
  val httpPortLabel = "httpPort"
  val httpsPortLabel = "httpsPort"
  val mturkFormLabel = "mturkForm"
  val rootClientDivLabel = "taskContent"
  val taskKeyLabel = "taskKey"
  val feedbackLabel = "feedback"
  val submitButtonLabel = "submitButton"
  val bottomDivLabel = "bottomDiv"
}
