package turkey
package tasks

import scalatags.Text.all._
import scalatags.Text.TypedTag
import upickle.default._

/** Contains the general HTML template for all tasks. */
object TaskPage {
  /** Constructs the HTML page for a given prompt and a given task. */
  def htmlPage[Prompt : Writer](
    prompt: Prompt,
    taskSpec: TaskSpecification,
    useHttps: Boolean = true,
    headTags: List[TypedTag[String]],
    bodyEndTags: List[TypedTag[String]])(
    implicit config: TaskConfig) = {
    import config._
    val protocol = if(useHttps) "https:" else "http:"
    val port = if(useHttps) httpsPort else httpPort
    html(
      head(
        meta(
          name := "viewport",
          content := "width=device-width, initial-scale=1, shrink-to-fit=no"
        ),
        script(
          `type` := "text/javascript",
          src := "https://s3.amazonaws.com/mturk-public/externalHIT_v1.js"
        ),
        script(
          `type` := "text/javascript",
          src := s"$protocol//$serverDomain:$port/$projectName-jsdeps.js"),
        script(
          `type` := "text/javascript",
          src := s"$protocol//$serverDomain:$port/$projectName-fastopt.js"),
        script(
          `type` := "text/javascript",
          src := s"$protocol//$serverDomain:$port/$projectName-launcher.js"),
        headTags,
        link(
          rel := "stylesheet",
          `type` := "text/css",
          href := s"$protocol//$serverDomain:$port/styles.css")
      ),
      body()(
        input(
          `type` := "hidden",
          value := write(prompt),
          name := promptLabel,
          id := promptLabel),
        input(
          `type` := "hidden",
          value := write(serverDomain),
          name := serverDomainLabel,
          id := serverDomainLabel),
        input(
          `type` := "hidden",
          value := write(httpPort),
          name := httpPortLabel,
          id := httpPortLabel),
        input(
          `type` := "hidden",
          value := write(httpsPort),
          name := httpsPortLabel,
          id := httpsPortLabel),
        input(
          `type` := "hidden",
          value := write(taskSpec.taskKey),
          name := taskKeyLabel,
          id := taskKeyLabel),
        form(
          name := mturkFormLabel,
          method := "post",
          id := mturkFormLabel,
          action := externalSubmitURL)(
          // where turk puts the assignment ID
          input(
            `type` := "hidden",
            value := "",
            name := "assignmentId",
            id := "assignmentId"),
          // where our client code should put the response
          input(
            `type` := "hidden",
            value := "",
            name := responseLabel,
            id := responseLabel),
          // and here I'll let the client code do its magic
          div(
            id := rootClientDivLabel,
            "Waiting for task data from server... (If this message does not disappear shortly, the server is down. Try refreshing in a minute or so.)"
          )
        ),
        bodyEndTags
      )
    )
  }
}
