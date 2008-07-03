/*                     __                                               *\
**     ________ ___   / /  ___     Scala API                            **
**    / __/ __// _ | / /  / _ |    (c) 2005-2007, LAMP/EPFL             **
**  __\ \/ /__/ __ |/ /__/ __ |    http://scala-lang.org/               **
** /____/\___/_/ |_/____/_/ | |                                         **
**                          |/                                          **
\*                                                                      */

// $Id: FJTaskScheduler2.scala 14416 2008-03-19 01:17:25Z mihaylov $

package scala.actors

import compat.Platform

import java.lang.{Runnable, Thread, InterruptedException, System, Runtime}

import scala.collection.Set
import scala.collection.mutable.{ArrayBuffer, Buffer, HashMap, Queue, Stack, HashSet}

import java.lang.ref.{WeakReference, ReferenceQueue}

/**
 * FJTaskScheduler2
 *
 * @version 0.9.12
 * @author Philipp Haller
 */
class FJTaskScheduler2 extends Thread with IScheduler {
  // as long as this thread runs, JVM should not exit 
  setDaemon(false)

  var printStats = false

  val rt = Runtime.getRuntime()
  val minNumThreads = 4

  val coreProp = try {
    System.getProperty("actors.corePoolSize")
  } catch {
    case ace: java.security.AccessControlException =>
      null
  }
  val maxProp =
    try {
      System.getProperty("actors.maxPoolSize")
    } catch {
      case ace: java.security.AccessControlException =>
        null
    }

  val initCoreSize =
    if (null ne coreProp) Integer.parseInt(coreProp)
    else {
      val numCores = rt.availableProcessors()
      if (2 * numCores > minNumThreads)
        2 * numCores
      else
        minNumThreads
    }

  val maxSize =
    if (null ne maxProp) Integer.parseInt(maxProp)
    else 256

  private var coreSize = initCoreSize

  private val executor =
    new FJTaskRunnerGroup(coreSize)

  private var terminating = false
  private var suspending = false

  private var lastActivity = Platform.currentTime

  private var submittedTasks = 0

  private var pendingReactions = 0

  def pendReaction: Unit = synchronized {
    pendingReactions += 1
  }

  def unPendReaction: Unit = synchronized {
    pendingReactions -= 1
  }

  def getPendingCount = synchronized {
    pendingReactions
  }

  def setPendingCount(cnt: Int) = synchronized {
    pendingReactions = cnt
  }

  def printActorDump {}
  def terminated(a: Actor) {}

  private val TICK_FREQ = 50
  private val CHECK_FREQ = 100

  def onLockup(handler: () => Unit) =
    lockupHandler = handler

  def onLockup(millis: Int)(handler: () => Unit) = {
    //LOCKUP_CHECK_FREQ = millis / CHECK_FREQ
    lockupHandler = handler
  }

  private var lockupHandler: () => Unit = null

  override def run() {
    try {
      while (!terminating) {
        this.synchronized {
          try {
            wait(CHECK_FREQ)
          } catch {
            case _: InterruptedException =>
              if (terminating) throw new QuitException
          }

          if (!suspending) {

            // check for unreachable actors
            def drainRefQ() {
              val wr = refQ.poll
              if (wr != null) {
                unPendReaction
                // continue draining
                drainRefQ()
              }
            }
            drainRefQ()

            // check if we need more threads
            if (Platform.currentTime - lastActivity >= TICK_FREQ
                && coreSize < maxSize
                && executor.checkPoolSize()) {
                  //Debug.info(this+": increasing thread pool size")
                  coreSize += 1
                  lastActivity = Platform.currentTime
                }
            else {
              if (pendingReactions <= 0) {
                // if all worker threads idle terminate
                if (executor.getActiveCount() == 0) {
                  Debug.info(this+": initiating shutdown...")

                  // Note that we don't have to shutdown
                  // the FJTaskRunnerGroup since there is
                  // no separate thread associated with it,
                  // and FJTaskRunner threads have daemon status.

                  // terminate timer thread
                  TimerThread.shutdown()
                  throw new QuitException
                }
              }
            }
          }
        } // sync

      } // while (!terminating)
    } catch {
      case _: QuitException =>
        // allow thread to exit
        if (printStats) executor.stats()
    }
  }

  /**
   *  @param item the task to be executed.
   */
  def execute(task: Runnable) {
    executor.execute(task)
  }

  private val refQ = new ReferenceQueue[Actor]
  private var storedRefs: List[WeakReference[Actor]] = List()

  def start(task: Runnable) {
    if (task.isInstanceOf[Reaction]) {
      val reaction = task.asInstanceOf[Reaction]
      val wr = new WeakReference[Actor](reaction.a, refQ)
      //Debug.info("created "+wr+" pointing to "+reaction.a)
      storedRefs = wr :: storedRefs
    }
    pendReaction
    executor.execute(task)
  }

  /**
   *  @param worker the worker thread executing tasks
   *  @return       the executed task
   */
  def getTask(worker: WorkerThread) = null

  /**
   *  @param a the actor
   */
  def tick(a: Actor) {
    lastActivity = Platform.currentTime
  }

  /** Shuts down all idle worker threads.
   */
  def shutdown(): Unit = synchronized {
    terminating = true
    // terminate timer thread
    TimerThread.shutdown()
  }

  def snapshot(): LinkedQueue = {
    suspending = true
    executor.snapshot()
  }

}
