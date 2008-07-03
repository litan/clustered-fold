/*                     __                                               *\
**     ________ ___   / /  ___     Scala API                            **
**    / __/ __// _ | / /  / _ |    (c) 2005-2007, LAMP/EPFL             **
**  __\ \/ /__/ __ |/ /__/ __ |    http://scala-lang.org/               **
** /____/\___/_/ |_/____/_/ | |                                         **
**                          |/                                          **
\*                                                                      */

// $Id: ActorProxy.scala 12257 2007-07-09 20:55:26Z phaller $


package scala.actors


import java.lang.Thread

/**
 * The class <code>ActorProxy</code> provides a dynamic actor proxy for normal
 * Java threads.
 *
 * @version 0.9.8
 * @author Philipp Haller
 */
private[actors] class ActorProxy(t: Thread) extends Actor {

  def act() {}

  /**
   * Terminates with exit reason <code>'normal</code>.
   */
  override def exit(): Nothing = {
    shouldExit = false
    // links
    if (!links.isEmpty)
      exitLinked()
    throw new InterruptedException
  }

}
