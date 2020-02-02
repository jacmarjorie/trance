package sprkloader

import scala.collection.mutable.HashMap

object Util{ 

  def countDistinct[K,V](i: Iterator[(K,V)]) = {
    i.foldLeft(HashMap.empty[K, Int].withDefaultValue(0))((acc, c) =>
      { acc(c._1) += 1; acc } )
  }

  def countDistinctByPartition[K,V](i: Iterator[(K,V)], index: Int) = {
    i.foldLeft(HashMap.empty[(K, Int), Int].withDefaultValue(0))((acc, c) =>
      { acc((c._1, index)) += 1; acc } )
  }

}
