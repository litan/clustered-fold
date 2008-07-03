package sample

import org.junit.Test
import org.junit.Assert


class TestParallelFolder {
  
  @Test
  def test1 = {
    Assert.assertTrue(21 == ParallelFolder.fold(List(1,2,3,4,5,6), (x: Int, y:Int) => x+y, 0))
  }

  @Test
  def test2 = {
    Assert.assertTrue(28 == ParallelFolder.fold(List(1,2,3,4,5,6,7), (x: Int, y:Int) => x+y, 0))
  }

  @Test
  def test3 = {
    Assert.assertTrue(4 == ParallelFolder.fold(List(1,2), (x: Int, y:Int) => x+y, 1))
  }

  @Test
  def test4 = {
    Assert.assertEquals(6.7, ParallelFolder.fold(List(1.1,2.2,3.3), (x: Double, y:Double) => x+y, 0.1), 0.001)
  }
}

