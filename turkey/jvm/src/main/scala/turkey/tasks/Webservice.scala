package turkey
package tasks

import turkey.util._

import java.util.Date

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.language.postfixOps

import akka.actor.ActorSystem
import akka.stream.stage._

import akka.http.scaladsl.server.Directives
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.ws.{ Message, TextMessage, BinaryMessage }
import akka.stream.Materializer
import akka.stream.scaladsl.Flow
import akka.stream.scaladsl._

import upickle.default._

import com.typesafe.scalalogging.StrictLogging

/** Implements the logic of the web server that hosts the given MTurk tasks.
  * Each TaskSpecification has its own Flow that specifies how to respond to WebSocket messages from clients.
  * This web service hosts the JS code that clients GET, and delegates Websocket messages to their tasks' flows.
  *
  * It also hosts a sample version of each task at http://localhost:<http port>/?taskKey=<task key>.
  */
class Webservice(
  tasks: List[TaskSpecification])(
  implicit fm: Materializer,
  config: TaskConfig) extends Directives with StrictLogging {
  // TODO verify that we're getting a JS file. don't just serve anything they ask for

  // assume keys are unique
  val taskIndex = tasks.map(t => (t.taskKey -> t)).toMap

  // we use the akka-http routing DSL to specify the server's behavior
  def route = get {
    pathSingleSlash {
      parameter('taskKey) { taskKey =>
        rejectEmptyResponse {
          complete {
            taskIndex.get(taskKey) map { taskSpec =>
              HttpEntity(
                ContentTypes.`text/html(UTF-8)`,
                TaskPage.htmlPage(taskSpec.samplePrompt, taskSpec, useHttps = false, headTags = taskSpec.taskPageHeadElements, bodyEndTags = taskSpec.taskPageBodyElements)(taskSpec.promptWriter, config).render
              )
            }
          }
        }
      }
    } ~ path("websocket") {
      parameter('taskKey) { taskKey =>
        handleWebSocketMessages(websocketFlow(taskKey))
      }
    }
  } ~ getFromResourceDirectory("")

  // task-specific flow for a websocket connection with a client
  private[this] def websocketFlow(taskKey: String): Flow[Message, Message, Any] = {
    val taskOpt = taskIndex.get(taskKey)
    taskOpt match {
      case None =>
        logger.warn(s"Got API request for task $taskKey which matches no task")
        Flow[Message].filter(_ => false)
      case Some(taskSpec) =>
        import taskSpec._ // to import ApiRequest and ApiResponse types and serializers
        Flow[Message].map {
          case TextMessage.Strict(msg) =>
            Future.successful(List(read[HeartbeatingWebSocketMessage[ApiRequest]](msg)))
          case TextMessage.Streamed(stream) => stream // necessary to handle large messages
              .limit(10000)                 // Max frames we are willing to wait for
              .completionTimeout(5 seconds) // Max time until last frame
              .runFold("")(_ + _)           // Merges the frames
              .flatMap(msg => Future.successful(List(read[HeartbeatingWebSocketMessage[ApiRequest]](msg))))
          case bm: BinaryMessage =>
            // ignore binary messages but drain content to avoid the stream being clogged
            bm.dataStream.runWith(Sink.ignore)
            Future.successful(Nil)
        }.mapAsync(parallelism = 3)(identity).mapConcat(identity)
          .collect { case WebSocketMessage(request) => request } // ignore heartbeats
          .via(taskSpec.apiFlow) // this is the key line that delegates to task-specific logic
          .map(WebSocketMessage(_): HeartbeatingWebSocketMessage[ApiResponse])
          .keepAlive(30 seconds, () => Heartbeat) // send heartbeat every 30 seconds to keep connection alive
          .map(message => TextMessage.Strict(write[HeartbeatingWebSocketMessage[ApiResponse]]((message))))
    }
  }
}
