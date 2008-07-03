package sample

object ClusterClient extends util.Logged {
  def main(args : Array[String]) : Unit = {
    log.info("Attaching to WorkManager")
    val master = new Master
    master.init
    
    for (i <- 1 to 10) {
      val work = List(1,2,3,4,5,6)
      log.info("Sending sequence to master for Parallel Fold: {}", work)
      master ! ParallelFoldReq(work, (x:Int ,y:Int) => x+y, 0, "host://protocol/requesterLocation")
    }
      
    // Kill a worker
    // log.info("Crippling the Master")
    // master ! Maim("cripple")
  }
}
