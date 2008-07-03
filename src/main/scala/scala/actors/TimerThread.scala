/*                     __                                               *\
**     ________ ___   / /  ___     Scala API                            **
**    / __/ __// _ | / /  / _ |    (c) 2005-2007, LAMP/EPFL             **
**  __\ \/ /__/ __ |/ /__/ __ |    http://scala-lang.org/               **
** /____/\___/_/ |_/____/_/ | |                                         **
**                          |/                                          **
\*                                                                      */

// $Id: TimerThread.scala 12257 2007-07-09 20:55:26Z phaller $


package scala.actors

import java.lang.{InterruptedException, Runnable, Thread}

import scala.collection.mutable.PriorityQueue
import scala.compat.Platform

/**
 * This class allows the (local) sending of a message to an actor after
 * a timeout.  Used by the library to build <code>receiveWithin(time: long)</code>.
 * Note that the library deletes non-received <code>TIMEOUT</code> message if a
 * message is received before the time-out occurs.
 *
 * @version 0.9.8
 * @author Sebastien Noir, Philipp Haller
 */

object TimerThread {

  private case class WakedActor(actor: Actor, f: PartialFunction[Any, Unit], time: Long)
               extends Ordered[WakedActor] {
    var valid = true
    def compare(that: WakedActor): Int = -(this.time compare that.time)
  }

  private var queue = new PriorityQueue[WakedActor]
  private var lateList: List[WakedActor] = Nil

  private val timerTask = new Runnable {
    override def run = {
      try {
        while(true) {
          timerThread.synchronized {
            try {
              val sleepTime = dequeueLateAndGetSleepTime
              if (lateList.isEmpty) timerThread.wait(sleepTime)
            } catch {
              case t: Throwable => { throw t }
            }
          }

          // process guys waiting for signal and empty list
          for (wa <- lateList) {
            if (wa.valid) {
              wa.actor ! TIMEOUT
            }
          }
          lateList = Nil
        }
      } catch {
        case consumed: InterruptedException =>
          // allow thread to quit
      }
    }
  }

  private var timerThread: Thread = {
    val t = new Thread(timerTask)
    t.start()
    t
  }

  def shutdown() {
    timerThread.interrupt()
  }

  def restart() {
    timerThread = {
      val t = new Thread(timerTask)
      t.start()
      t
    }
  }

  def requestTimeout(a: Actor, f: PartialFunction[Any, Unit],
                     waitMillis: Long): Unit = timerThread.synchronized {
    val wakeTime = now + waitMillis
    if (waitMillis <= 0) {
      a ! TIMEOUT
      return
    }

    if (queue.isEmpty) { // add to queue and restart sleeping
      queue += WakedActor(a, f, wakeTime)
      timerThread.notify()
    } else
      if (queue.max.time > wakeTime) { // add to 1st position and restart sleeping
        queue += WakedActor (a, f, wakeTime)
        timerThread.notify()
      }
      else // simply add to queue
        queue += WakedActor (a, f, wakeTime)
  }

  def trashRequest(a: Actor) = timerThread.synchronized {
    // keep in mind: killing dead people is a bad idea!
    queue.elements.find((wa: WakedActor) => wa.actor == a && wa.valid) match {
      case Some(b) =>
        b.valid = false
      case None =>
        lateList.find((wa2: WakedActor) => wa2.actor == a && wa2.valid) match {
          case Some(b2) =>
            b2.valid = false
          case None =>
        }
    }
  }

  private def dequeueLateAndGetSleepTime: Long = {
    val FOREVER: Long = 0
    var waitingList: List[WakedActor] = Nil

    while (!queue.isEmpty) {
      val next = queue.max.time
      val amount = next - now
      if (amount > 0) { // guy in queue is not late
        lateList = waitingList // give back the list of waiting guys for signaling
        return amount
      }
      else // we're late: dequeue and examine next guy
        waitingList = queue.dequeue :: waitingList
    }

    // empty queue => sleep forever
    lateList = waitingList
    FOREVER
  }

  private def now = Platform.currentTime
}
