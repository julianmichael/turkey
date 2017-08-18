package turkey
package tasks

import turkey.util._

import com.amazonaws.mturk.requester.AssignmentStatus

import scala.util.{Try, Success, Failure}
import scala.concurrent.duration._
import scala.language.postfixOps

import akka.actor.Actor
import akka.actor.ActorRef

import upickle.default._

import com.typesafe.scalalogging.StrictLogging

/**
  * Manages a particular kind of task; corresponds to a single TaskSpecification / HIT Type.
  * In here will be all of the logic related to how to review HITs, do quality control, keep track of auxiliary data,
  * schedule which HITs should be uploaded when, etc.
  */
abstract class HITManager[Prompt, Response](
  helper: HITManager.Helper[Prompt, Response]
) extends Actor {

  import helper.Message._

  final override def receive = receiveHelperMessage orElse receiveAux

  // delegates to helper when given a standard message defined in the helper
  private[this] final val receiveHelperMessage: PartialFunction[Any, Unit] = {
    case DisableAll => helper.disableAll
    case ReviewHITs => reviewHITs
    case AddPrompt(p) => addPrompt(p)
  }

  /** Override to add more incoming message types and message-processing logic */
  def receiveAux: PartialFunction[Any, Unit] =
    PartialFunction.empty[Any, Unit]

  /** Queries Turk and refreshes the task state, sending assignments for approval/validation,
    * approving/rejecting them, disposing HITs, etc. as necessary */
  def reviewHITs: Unit

  /** Adds a prompt to the set of prompts that this HITManager should be responsible for sourcing responses for. */
  def addPrompt(prompt: Prompt): Unit
}

object HITManager {
  /** Manages the ongoing state for a task with a particular HIT type;
    * keeps track of HITs and assignments that are active, saved, etc.
    * and gives convenience methods for interfacing with Turk. */
  class Helper[P, R](
    val taskSpec: TaskSpecification { type Prompt = P ; type Response = R })(
    implicit val promptReader: Reader[P],
    val responseReader: Reader[R],
    val responseWriter: Writer[R],
    val config: TaskConfig
  ) extends StrictLogging {
    private type Prompt = P
    private type Response = R

    import scala.collection.mutable

    object Message {
      sealed trait Message
      case object DisableAll extends Message
      case object ReviewHITs extends Message
      case class AddPrompt(prompt: Prompt) extends Message
    }
    import Message._
    import taskSpec.hitTypeId

    // disable method, not really complete yet

    def disableAll: Unit = {
      // TODO get all currently pending assignments and manually approve them?
      // saving the results in another location? THEN disable all HITs?
      // NOTE the above is an old todo. not sure if I still want to do it. not taking the time to think about it
      // TODO XXX integrate this with helper state. as of now weird things prob will happen
      // so you need to restart any time you disable. fortunately you probably want to anyway...
      config.service.searchAllHITs()
        .filter(hit => hit.getHITTypeId().equals(hitTypeId))
        .foreach(hit => {
                   config.service.disableHIT(hit.getHITId())
                   logger.info(s"Disabled HIT: ${hit.getHITId()}\nHIT type for disabled HIT: ${hitTypeId}")
                 })
    }

    // HITs Active stuff

    // active HITs are currently up on Turk
    private[this] val activeHITs = {
      val active = mutable.Set.empty[HIT[Prompt]]
      for {
        mTurkHIT <- config.service.searchAllHITs
        if mTurkHIT.getHITTypeId.equals(hitTypeId)
        hit <- config.hitDataService.getHIT[Prompt](hitTypeId, mTurkHIT.getHITId).toOptionLogging(logger)
      } yield (active += hit)
      active
    }

    // finished means the HIT is not on turk (i.e., all assignments are done)
    // active includes HITs for which some assignments are done and some are not
    private[this] val (finishedHITInfosByPrompt, activeHITInfosByPrompt) = {
      val finishedRes = mutable.Map.empty[Prompt, List[HITInfo[Prompt, Response]]]
      val activeRes = mutable.Map.empty[Prompt, List[HITInfo[Prompt, Response]]]
      config.hitDataService.getAllHITInfo[Prompt, Response](hitTypeId).get
        .groupBy(_.hit.prompt)
        .foreach { case (prompt, infos) =>
          infos.foreach { hitInfo =>
            if(activeHITs.contains(hitInfo.hit)) {
              activeRes.put(prompt, hitInfo :: activeRes.get(prompt).getOrElse(Nil))
            } else {
              finishedRes.put(prompt, hitInfo :: activeRes.get(prompt).getOrElse(Nil))
            }
          }
      }
      (finishedRes, activeRes)
    }

    def finishedHITInfosByPromptIterator: Iterator[(Prompt, List[HITInfo[Prompt, Response]])] =
      finishedHITInfosByPrompt.iterator
    def finishedHITInfos(p: Prompt): List[HITInfo[Prompt, Response]] =
      finishedHITInfosByPrompt.get(p).getOrElse(Nil)
    def activeHITInfosByPromptIterator: Iterator[(Prompt, List[HITInfo[Prompt, Response]])] =
      activeHITInfosByPrompt.iterator
    def activeHITInfos(p: Prompt): List[HITInfo[Prompt, Response]] =
      activeHITInfosByPrompt.get(p).getOrElse(Nil)
    def allCurrentHITInfosByPromptIterator: Iterator[(Prompt, List[HITInfo[Prompt, Response]])] =
      activeHITInfosByPromptIterator ++ finishedHITInfosByPromptIterator
    def allCurrentHITInfos(p: Prompt): List[HITInfo[Prompt, Response]] =
      activeHITInfos(p) ++ finishedHITInfos(p)

    /** Create a HIT with the specific parameters.
      * This should be used in order to ensure the helper has a consistent state.
      */
    def createHIT(prompt: Prompt, numAssignments: Int): Try[HIT[Prompt]] = {
      val attempt = taskSpec.createHIT(prompt, numAssignments)
      attempt match {
        case Success(hit) =>
          activeHITs += hit
          val newHITInfo = HITInfo[Prompt, Response](hit, Nil)
          activeHITInfosByPrompt.put(prompt, newHITInfo :: activeHITInfos(prompt))
          logger.info(s"Created HIT: ${hit.hitId}\n${config.service.getWebsiteURL}/mturk/preview?groupId=${hit.hitTypeId}")
        case Failure(e) =>
          logger.error(e.getMessage)
          e.printStackTrace
      }
      attempt
    }

    def isActive(prompt: Prompt): Boolean = activeHITInfosByPrompt.contains(prompt)
    def isActive(hit: HIT[Prompt]): Boolean = activeHITs.contains(hit)
    def isActive(hitId: String): Boolean = activeHITs.exists(_.hitId == hitId)
    def numActiveHITs = activeHITs.size

    /** Disposes of a disposable HIT and takes care of bookkeeping.
      * Assumes the HIT is disposable.
      */
    def finishHIT(hit: HIT[Prompt]): Unit = {
      config.service.disposeHIT(hit.hitId)
      if(!isActive(hit)) {
        logger.error(s"Trying to finish HIT that isn't active? $hit")
      }
      activeHITs -= hit
      // add to other appropriate data structures
      val finishedData = finishedHITInfos(hit.prompt)
      val activeData = activeHITInfos(hit.prompt)
      val curInfo = activeData
        .find(_.hit.hitId == hit.hitId)
        .getOrElse {
        logger.error("Could not find active HIT to move to finished");
        HITInfo(
          hit,
          config.hitDataService.getAssignmentsForHIT[Response](hitTypeId, hit.hitId).get)
      }
      val newActiveData = activeData.filterNot(_.hit.hitId == hit.hitId)
      val newFinishedData = curInfo :: finishedData
      activeHITInfosByPrompt.put(hit.prompt, newActiveData)
      finishedHITInfosByPrompt.put(hit.prompt, newFinishedData)
    }

    // Assignment reviewing

    /** Represents an assignment waiting for a reviewing result. */
    class AssignmentInReview protected[Helper] (val assignment: Assignment[Response])

    private[this] val assignmentsInReview = mutable.Set.empty[AssignmentInReview]

    def isInReview(assignment: Assignment[Response]): Option[AssignmentInReview] =
      assignmentsInReview.find(_.assignment == assignment)
    def isInReview(assignmentId: String): Option[AssignmentInReview] =
      assignmentsInReview.find(_.assignment.assignmentId == assignmentId)
    def numAssignmentsInReview = assignmentsInReview.size

    /** Mark an assignment as under review. */
    def startReviewing(assignment: Assignment[Response]): AssignmentInReview = {
      val aInRev = new AssignmentInReview(assignment)
      assignmentsInReview += aInRev
      aInRev
    }

    /** Process and record the result of reviewing an assignment. */
    def evaluateAssignment(
      hit: HIT[Prompt],
      aInRev: AssignmentInReview,
      evaluation: AssignmentEvaluation
    ): Unit = {
      import aInRev.assignment
      evaluation match {
        case Approval(message) =>
          config.service.approveAssignment(assignment.assignmentId, message)
          assignmentsInReview -= aInRev
          val curData = activeHITInfos(hit.prompt)
          val curInfo = curData.find(_.hit.hitId == hit.hitId)
            .getOrElse {
            logger.error(s"Could not find active data for hit $hit")
            HITInfo[Prompt, Response](hit, Nil)
          }
          val filteredData = curData.filterNot(_.hit.hitId == hit.hitId)
          val newInfo = curInfo.copy(assignments = assignment :: curInfo.assignments)
          activeHITInfosByPrompt.put(hit.prompt, newInfo :: filteredData)
          logger.info(s"Approved assignment for worker ${assignment.workerId}: ${assignment.assignmentId}\n" +
                        s"HIT for approved assignment: ${assignment.hitId}; $hitTypeId")
          config.hitDataService.saveApprovedAssignment(assignment).recover { case e =>
            logger.error(s"Failed to save approved assignment; data:\n${write(assignment)}")
        }
        case Rejection(message) =>
          config.service.rejectAssignment(assignment.assignmentId, message)
          assignmentsInReview -= aInRev
          logger.info(s"Rejected assignment: ${assignment.assignmentId}\n" +
                        s"HIT for rejected assignment: ${assignment.hitId}; ${hitTypeId}\n" +
                        s"Reason: $message")
          config.hitDataService.saveRejectedAssignment(assignment) recover { case e =>
            logger.error(s"Failed to save approved assignment; data:\n${write(assignment)}")
          }
      }
    }
  }
}

