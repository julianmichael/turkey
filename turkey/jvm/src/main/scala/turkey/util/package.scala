package turkey

import scala.util.{Try, Success, Failure}

import scala.collection.mutable
import scala.collection.TraversableOnce

import scala.language.implicitConversions

import com.typesafe.scalalogging.Logger

import java.io.StringWriter
import java.io.PrintWriter

import com.amazonaws.services.mturk.AmazonMTurk
import com.amazonaws.services.mturk.model.{HIT => MTurkHIT}
import com.amazonaws.services.mturk.model.ListHITsRequest

/** Utility classes, methods, and extension methods for turkey. */
package object util {
  // this is basically what we want to do with most errors
  protected[turkey] implicit class RichTry[A](val t: Try[A]) extends AnyVal {
    def toOptionLogging(logger: Logger): Option[A] = t match {
      case Success(a) =>
        Some(a)
      case Failure(e) =>
        val sw = new StringWriter()
        val pw = new PrintWriter(sw, true)
        e.printStackTrace(pw)
        logger.error(e.getLocalizedMessage + "\n" + sw.getBuffer.toString)
        None
    }
  }

  // the two ext. methods are mainly nice for the LazyStackQueue implementation.
  // also they should seriously already exist...

  protected[turkey] implicit class RichMutableStack[A](val s: mutable.Stack[A]) extends AnyVal {
    def popOption: Option[A] = if(!s.isEmpty) Some(s.pop) else None
  }

  protected[turkey] implicit class RichMutableQueue[A](val q: mutable.Queue[A]) extends AnyVal {
    def dequeueOption: Option[A] = if(!q.isEmpty) Some(q.dequeue) else None
  }

  // convenience methods for mturk
  implicit class RichAmazonMTurk(val client: AmazonMTurk) extends AnyVal {

    import scala.collection.JavaConverters._
    import scala.annotation.tailrec

    def listAllHITs = {
      @tailrec def getAllHITsAux(hitsSoFar: Vector[MTurkHIT], request: ListHITsRequest): Vector[MTurkHIT] = {
        val nextPage = client.listHITs(request)
        val nextToken = nextPage.getNextToken
        if(nextToken == null) {
          hitsSoFar
        } else {
          getAllHITsAux(hitsSoFar ++ nextPage.getHITs.asScala, request.withNextToken(nextToken))
        }
      }
      getAllHITsAux(Vector.empty[MTurkHIT], new ListHITsRequest().withMaxResults(10))
    }
  }

}
