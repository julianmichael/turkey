import turkey._
import turkey.tasks._
import turkey.sample._
val hitDataService = new InMemoryHITDataService
implicit val config = SandboxTaskConfig(
  "turkey-sample",
  "localhost",
  hitDataService)
val exp = new SampleExperiment
def exit = {
  config.actorSystem.terminate
  // TODO exit the console without killing SBT... how?
}
exp.server
