package turkey

package object sample {
  // in shared code, you should define a Prompt and Response data type for each task.
  // They should be serializable and you should not expect to have to change these often;
  // all HIT data will be written with serialized versions of the prompts and responses.
  // A good rule of thumb is to keep the minimal necessary amount of information in them.
  case class SamplePrompt(sentence: Int)
  case class SampleResponse(isGood: Boolean)

  // you must also have API request and response types for the WebSocket API.
  sealed trait ApiRequest
  case class SentenceRequest(id: Int) extends ApiRequest

  sealed trait ApiResponse
  case class SentenceResponse(id: Int, sentence: String) extends ApiResponse

  // finally, you must define a task key (string) for every task, which is unique to that task.
  // this will be used as a URL parameter to access the right client code, websocket flow, etc.
  // when interfacing between the client and server.
  val sampleTaskKey = "sample"
}
