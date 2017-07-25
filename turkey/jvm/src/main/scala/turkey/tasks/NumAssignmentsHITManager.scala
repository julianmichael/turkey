package turkey
package tasks

import turkey.util._

import scala.collection.mutable
import scala.util.{Try, Success, Failure}

import upickle.default.Reader

import akka.actor.ActorRef

import com.amazonaws.mturk.requester.AssignmentStatus
import com.amazonaws.mturk.requester.HITStatus

import com.typesafe.scalalogging.StrictLogging

case class SetNumHITsActive(value: Int)

object NumAssignmentsHITManager {
  def constAssignments[Prompt, Response](
    helper: HITManager.Helper[Prompt, Response],
    numAssignmentsPerPrompt: Int,
    initNumHITsToKeepActive: Int,
    _promptSource: Iterator[Prompt]) = new NumAssignmentsHITManager[Prompt, Response](
    helper, _ => numAssignmentsPerPrompt, initNumHITsToKeepActive, _promptSource)
}

/** Simplest HIT manager, which gets a fixed number of assignments for every prompt
  * and approves all assignments immediately.
  */
class NumAssignmentsHITManager[Prompt, Response](
  helper: HITManager.Helper[Prompt, Response],
  numAssignmentsForPrompt: Prompt => Int,
  initNumHITsToKeepActive: Int,
  _promptSource: Iterator[Prompt]) extends HITManager[Prompt, Response](helper) with StrictLogging {

  var numHITsToKeepActive: Int = initNumHITsToKeepActive

  /** Override to add more possible incoming message types and message-processing logic. */
  def receiveAux2: PartialFunction[Any, Unit] =
    PartialFunction.empty[Any, Unit]

  override lazy val receiveAux: PartialFunction[Any, Unit] = (
    { case SetNumHITsActive(n) => numHITsToKeepActive = n }: PartialFunction[Any, Unit]
  ) orElse receiveAux2

  import helper.config
  import helper.taskSpec.hitTypeId
  import helper.promptReader

  // override for more interesting review policy
  def reviewAssignment(hit: HIT[Prompt], assignment: Assignment[Response]): Unit = {
    helper.evaluateAssignment(hit, helper.startReviewing(assignment), Approval(""))
    if(!assignment.feedback.isEmpty) {
      logger.info(s"Feedback: ${assignment.feedback}")
    }
  }

  // override to do something interesting after a prompt finishes
  def promptFinished(prompt: Prompt): Unit = ()

  // override if you want fancier behavior
  override def addPrompt(prompt: Prompt): Unit = {
    queuedPrompts.enqueue(prompt)
  }

  val queuedPrompts = new LazyStackQueue[Prompt](_promptSource)

  def isFinished(prompt: Prompt) =
    helper.finishedHITInfos(prompt).map(_.assignments.size).sum >= numAssignmentsForPrompt(prompt)

  final override def reviewHITs: Unit = {
    for {
      allMTurkHITs <- Try(config.service.searchAllHITs()).toOptionLogging(logger).toList
      mTurkHIT <- allMTurkHITs.filter(_.getHITTypeId() == hitTypeId)
      hit <- config.hitDataService.getHIT[Prompt](hitTypeId, mTurkHIT.getHITId).toOptionLogging(logger)
    } yield {
      val submittedAssignments = config.service.getAllAssignmentsForHIT(hit.hitId, Array(AssignmentStatus.Submitted))
      // review all submitted assignments (that are not currently in review)
      for(a <- submittedAssignments) {
        val assignmentTry = Try(helper.taskSpec.makeAssignment(hit.hitId, a))

        assignmentTry match {
          case Success(assignment) => if(helper.isInReview(assignment).isEmpty) {
            reviewAssignment(hit, assignment)
          }
          case Failure(e) =>
            logger.error(s"Error parsing assignment: ${e.getMessage}; assignment:\n$a")
            config.service.approveAssignment(
              a.getAssignmentId,
              "There was an error in parsing your response to the HIT. Please notify the requester.")
        }
      }
      // if the HIT is "reviewable", and all its assignments are reviewed (i.e., no longer "Submitted"), we can dispose
      if(mTurkHIT.getHITStatus == HITStatus.Reviewable && submittedAssignments.isEmpty) {
        helper.finishHIT(hit)
        if(isFinished(hit.prompt)) {
          promptFinished(hit.prompt)
        }
      }
    }

    // refresh: upload new hits to fill gaps
    val numToUpload = numHITsToKeepActive - helper.numActiveHITs
    for(_ <- 1 to numToUpload) {
      queuedPrompts.filterPop(p => !isFinished(p)) match {
        case None => () // we're finishing off, woo
        case Some(nextPrompt) =>
          if(helper.isActive(nextPrompt)) {
            // if this prompt is already active, queue it for later
            // TODO probably want to delay it by a constant factor instead
            queuedPrompts.enqueue(nextPrompt)
          } else helper.createHIT(nextPrompt, numAssignmentsForPrompt(nextPrompt)) recover {
            case _ => queuedPrompts.enqueue(nextPrompt) // put it back at the bottom to try later
          }
      }
    }
  }
}
