package turkey
package tasks

import akka.actor.ActorSystem

import com.amazonaws.mturk.service.axis.RequesterService
import com.amazonaws.mturk.util.PropertiesClientConfig
import com.amazonaws.mturk.util.ClientConfig
import com.amazonaws.mturk.dataschema.QuestionFormAnswersType

import upickle.default._

/** Contains the global configuration of our usage of the MTurk API,
  * including relevant values (URLs, API hooks) and whether we are running
  * on production or in the sandbox.
  */
sealed trait TaskConfig {
  /** The API hook with which we communicate with MTurk.
    * We need a different hook depending on whether we're in sandbox or production,
    * because it uses a different URL.
    */
  val service: RequesterService

  /** The URL used by HTMLQuestion and ExternalQuestion question types to submit assignments.
    * (See http://docs.aws.amazon.com/AWSMechTurk/latest/AWSMturkAPI/ApiReference_QuestionAnswerDataArticle.html
    * for documentation on these question types.)
    * In particular, if we want to provide our own HTML with which to render the task (which we usually do),
    * instead of using the default "Submit HIT" button, we must make our own HTML form and embed it in the HIT.
    * That form then needs to submit to this URL.
    */
  val externalSubmitURL: String

  /** Whether we are working in production as opposed to the sandbox. */
  val isProduction: Boolean

  /** The ActorSystem we're using to manage tasks and host the server. */
  val actorSystem: ActorSystem

  /** The domain at which we're hosting our server. */
  val serverDomain: String

  /** The interface (IP address) we're using to host the server. */
  val interface: String

  /** What port we're hosting HTTP at. */
  val httpPort: Int

  /** What port we're hosting HTTPS at. */
  val httpsPort: Int

  /** Service for storing and getting finished HIT data */
  val hitDataService: HITDataService

  /** Name of the project we're building your JS files with; used to determine what JS
    * files the client asks for. Needs to agree with the project name in your `build.sbt` */
  val projectName: String
}

object TaskConfig {
  /** Convenience method to load configuration that is common to sandbox and production.
    * In particular, this loads our API keys from the `mturk.properties` file.
    */
  protected[tasks] def loadGlobalConfig(): ClientConfig = {
    val config = new PropertiesClientConfig("mturk.properties")
    import scala.collection.JavaConverters._
    config.setRetriableErrors(Set("Server.ServiceUnavailable").asJava)
    config.setRetryAttempts(10)
    config.setRetryDelayMillis(1000L)
    config
  }
}

/** Complete configuration for running on production. */
case class ProductionTaskConfig(
  override val projectName: String,
  override val serverDomain: String,
  override val hitDataService: HITDataService
) extends TaskConfig {
  private[this] val config = TaskConfig.loadGlobalConfig()
  config.setServiceURL(ClientConfig.PRODUCTION_SERVICE_URL)
  override val service = new RequesterService(config)
  override val externalSubmitURL = "https://www.mturk.com/mturk/externalSubmit"
  override val isProduction = true

  override val actorSystem = ActorSystem("production")
  private[this] val akkaConfig = actorSystem.settings.config
  override val interface = akkaConfig.getString("app.interface")
  override val httpPort = akkaConfig.getInt("app.httpPort")
  override val httpsPort = akkaConfig.getInt("app.httpsPort")
}

/** Complete configuration for running on the sandbox. */
case class SandboxTaskConfig(
  override val projectName: String,
  override val serverDomain: String,
  override val hitDataService: HITDataService) extends TaskConfig {
  private[this] val config = TaskConfig.loadGlobalConfig()
  config.setServiceURL(ClientConfig.SANDBOX_SERVICE_URL)
  override val service = new RequesterService(config)
  override val externalSubmitURL = "https://workersandbox.mturk.com/mturk/externalSubmit"
  override val isProduction = false

  override val actorSystem = ActorSystem("sandbox")
  private[this] val akkaConfig = actorSystem.settings.config
  override val interface = akkaConfig.getString("app.interface")
  override val httpPort = akkaConfig.getInt("app.httpPort")
  override val httpsPort = akkaConfig.getInt("app.httpsPort")
}
