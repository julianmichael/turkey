package turkey

import scala.util.{Try, Success, Failure}

import scala.collection.mutable
import scala.collection.TraversableOnce

import scala.language.implicitConversions

import com.typesafe.scalalogging.Logger

import java.io.StringWriter
import java.io.PrintWriter

package object util {
  implicit class RichTry[A](val t: Try[A]) extends AnyVal {
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

  protected[turkey] implicit class RichMutableStack[A](val s: mutable.Stack[A]) extends AnyVal {
    def popOption: Option[A] = if(!s.isEmpty) Some(s.pop) else None
  }

  protected[turkey] implicit class RichMutableQueue[A](val q: mutable.Queue[A]) extends AnyVal {
    def dequeueOption: Option[A] = if(!q.isEmpty) Some(q.dequeue) else None
  }

}
