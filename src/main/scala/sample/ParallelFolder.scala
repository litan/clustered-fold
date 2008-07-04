/*
 * Copyright (C) 2008 Lalit Pant <lalit_pant@yahoo.com>
 *
 * The contents of this file are subject to the GNU General Public License 
 * Version 3 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of
 * the License at http://www.gnu.org/copyleft/gpl.html
 *
 * Software distributed under the License is distributed on an "AS
 * IS" basis, WITHOUT WARRANTY OF ANY KIND, either express or
 * implied. See the License for the specific language governing
 * rights and limitations under the License.
 *
 */
package sample

import java.util.ArrayList
import java.util.concurrent._
import java.util.concurrent.locks._
import scala.actors._
import scala.actors.Actor._
import scala.collection.mutable.HashMap

case class ParallelFoldReq[A](data: Seq[A], folder: (A, A) => A, seed: A, requester: AnyRef)
case class ParallelFoldWorkReq[A](taskId: Int, data: Iterable[A], folder: (A, A) => A, fromMaster: Actor)
case class ParallelFoldWorkResult[A](taskId: Int, result: A, folder: (A, A) => A, fromWorker: Actor)
case class Tick(s: String) // cluster-support
case class Maim(s: String)
case object Quit

class Master extends Actor {
  val (worker1, worker2, numWorkers) = (new Worker, new Worker, 2)
  val taskStatus = new HashMap[Int, (Any, Int, AnyRef)]
  var nextTaskId = 0

  def go = {
    worker1.go; worker2.go
    this.start
  }
  
  def act = {
    init // cluster-support
    log.debug("Scheduling ticks for Master")
    ActorPing.scheduleAtFixedRate(this, Tick("master"), 0L, 5000L) // cluster-support
    loop {
      react {
      case ParallelFoldReq(data, folder, seed, requester) =>
        log.info("Master Received Request. Current Task Id: {}", nextTaskId)

        val partitions = partition(data) 
        worker1 ! ParallelFoldWorkReq(nextTaskId, partitions(0), folder, self) 
        worker2 ! ParallelFoldWorkReq(nextTaskId, partitions(1), folder, self) 
        
        taskStatus += nextTaskId -> (seed, 0, requester)
        nextTaskId += 1
        
      case ParallelFoldWorkResult(taskId, wresult, folder, worker) =>
        log.info("Master received result: {} - from worker {}. Task Id: " + taskId, wresult, worker)
        
        var result = taskStatus(taskId)._1        
        result = folder(result, wresult)
        
        var resultsReceived = taskStatus(taskId)._2        
        resultsReceived += 1
        log.info("Results received: {}. Current Taskk Id: {}", resultsReceived, taskId)
        
        if (resultsReceived == numWorkers) {
          sendResultToRequester(taskId, result, taskStatus(taskId)._3)
          taskStatus -= taskId
        }
        else {
          taskStatus.update(taskId, (result, resultsReceived, taskStatus(taskId)._3))
          log.info("Waiting for more results from worker. Current Taskk Id: {}", taskId)
        }
  
      case Maim(x) =>
        log.info("Master asked to Cripple itself")
        worker2 ! Quit
        
      case Tick(x) => 
          log.debug("Master got a Tick")
      }
    }
  }
  
  def partition[A](data: Seq[A]) = {
    val sliceLen = data.size / numWorkers
    for(i <- 0 until numWorkers) yield {
      if (i == numWorkers-1) data.slice(i*sliceLen) else data.slice(i*sliceLen, (i+1)*sliceLen)
    }
  }
  
  def sendResultToRequester[A](taskId:Int, result: A, requester: AnyRef) = requester match {
  case r: String => 
    log.info("Result available for Requester: {}. Current Taskk Id: {}. The Result is: " + result, r, taskId)
  case a: Actor => 
    log.info("Sending result - {} - back to Requester: {}. Current Taskk Id: " + taskId, result, a)
    a ! result
    log.info("Result Sent")
    
  }
}

object WorkerIdGenerator {
  var nid = 0
  def nextId = {nid += 1; nid} 
}

class Worker extends Actor {
  val id = WorkerIdGenerator.nextId
  log.debug("Worker created with Id: {}", id)

  def go = start
  
  def act = {
    init // cluster-support
    log.debug("Scheduling ticks for Worker: {}", id)
    ActorPing.scheduleAtFixedRate(this, Tick("worker"), 0L, 5000L) // cluster-support
    loop {
      react {
        
      case ParallelFoldWorkReq(taskId, data, folder, master) =>
        log.info("Worker {} received request. Current Task Id: {}", id, taskId)
        Thread.sleep(1000 * 1)
        val result = data.reduceLeft(folder)
        master ! ParallelFoldWorkResult(taskId, result, folder, self)
        
      case Quit => 
        log.info("Worker asked to Quit: {}", id)
        throw new RuntimeException("Bye from: " + this)
        
      case Tick(x) => 
          log.debug("Worker {} got a Tick", id)
      }
    }
  }
}

object ParallelFolder extends util.Logged {
  log.info("Parallel Folder Starting...")
  val master = new Master
  master.go
  log.info("Parallel Folder ready to go.")

  def main(args: Array[String]): Unit = {
    if (args.size == 0 || !args(0).trim.equals("-c")) {
      // not running in cluster. Initiate some work right here
      for (i <- 1 to 1) {
        val work = List(1,2,3,4,5,6)
        log.info("Sending sequence to master for Parallel Fold: {}", work)
        master ! ParallelFoldReq(work, (x:Int ,y:Int) => x+y, 0, "host://protocol/requesterLocation")
      }
    }
  }
  
  def fold[A](data: Seq[A], folder: (A, A) => A, x: A): A = {
    master ! ParallelFoldReq(data, folder, x, self)
    val ret = self.receive({case x => x})
    ret.asInstanceOf[A]
  } 
}

// =============================================
/**
 * Pings an actor every X seconds.
 * 
 * Borrowed from Scala TIM sample; which borrows from:
 * 
 * Code based on code from the ActorPing class in the /lift/ repository (http://liftweb.net).
 * Copyright: 
 *
 * (c) 2007 WorldWide Conferencing, LLC
 * Distributed under an Apache License
 * http://www.apache.org/licenses/LICENSE-2.0
 */
object ActorPing { 

  def scheduleAtFixedRate(to: Actor, msg: Any, initialDelay: Long, period: Long): ScheduledFuture[T] forSome {type T} = {
    val cmd = new Runnable { 
      def run { 
        // println("***ActorPing Event***");
        try {
          to ! msg 
        }
        catch {
        case t:Throwable => t.printStackTrace
        }
      } 
    }
    service.scheduleAtFixedRate(cmd, initialDelay, period, TimeUnit.MILLISECONDS)
  }
  
  private val service = Executors.newSingleThreadScheduledExecutor(threadFactory)  
  
  private object threadFactory extends ThreadFactory {
    val threadFactory = Executors.defaultThreadFactory()
    def newThread(r: Runnable) : Thread = {
      val d: Thread = threadFactory.newThread(r)
      d setName "ActorPing"
      d setDaemon true
      d
    }
  }
}


