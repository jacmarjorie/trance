package sprkloader

import scala.reflect.ClassTag
import scala.reflect.runtime.universe.TypeTag
import scala.collection.mutable.HashMap
import org.apache.spark.broadcast.Broadcast
import org.apache.spark.sql._
import org.apache.spark.sql.functions._
import org.apache.spark.sql.expressions.scalalang._
import PairRDDOperations._

object SkewDataset{

  implicit class DatasetOps[T: Encoder: ClassTag](left: Dataset[T]) extends Serializable {

    def print: Unit = left.collect.foreach(println(_))

    def empty: Dataset[T] = left.sparkSession.emptyDataset[T].repartition(1)

    def empty[U: Encoder : TypeTag]: Dataset[U] = left.sparkSession.emptyDataset[U].repartition(1)

    def emptyDF: DataFrame = left.sparkSession.emptyDataFrame.repartition(1)

    def lfilter[K](col: Column, hkeys: Broadcast[Set[K]]): Dataset[T] = {
      left.filter(!col.isInCollection(hkeys.value) || col.isNull)
    }

    def hfilter[K](col: Column, hkeys: Broadcast[Set[K]]): Dataset[T] = {
      left.filter((col.isInCollection(hkeys.value)))
    }

    def equiJoinWith[S: Encoder : ClassTag](right: Dataset[S], usingColumns: Seq[String], joinType: String = "inner"): Dataset[(T,S)] = {
      left.joinWith(right, col(usingColumns(0)) === col(usingColumns(1)), joinType)
    }

    def equiJoin[S: Encoder : ClassTag](right: Dataset[S], usingColumns: Seq[String], joinType: String = "inner"): DataFrame = {
      left.join(right, col(usingColumns(0)) === col(usingColumns(1)), joinType)
    }

    def outerjoin[S: Encoder : ClassTag](right: Dataset[S], usingColumns: Seq[String], joinType: String = "full_outer"): Dataset[(T,S)] = {
      left.joinWith(right, col(usingColumns(0)) === col(usingColumns(1)), joinType)
    }

    def lookup[S: Encoder : ClassTag, R : Encoder: ClassTag, K](right: Dataset[S], lkey: (T) => K, rkey: (S) => K)
      (f: (K, Iterator[T], Iterator[S]) => TraversableOnce[R])(implicit arg0: Encoder[K]): Dataset[R] =
        left.groupByKey(lkey).cogroup(right.groupByKey(rkey))(f)

    def cogroup[S: Encoder : ClassTag, R : Encoder: ClassTag, K : Encoder: ClassTag](right: KeyValueGroupedDataset[K,S], lkey: (T) => K)
      (f: (K, Iterator[T], Iterator[S]) => TraversableOnce[R]): Dataset[R] = {
      left.groupByKey(lkey).cogroup(right)(f)
    }

    def cogroup[S: Encoder : ClassTag, R : Encoder: ClassTag, K](right: Dataset[S], lkey: (T) => K, rkey: (S) => K, key: Option[String] = None)
      (f: (K, Iterator[T], Iterator[S]) => TraversableOnce[R])(implicit arg0: Encoder[K]): Dataset[R] =
        left.groupByKey(lkey).cogroup(right.groupByKey(rkey))(f)

    def groupByLabel[K: Encoder](f: (T) => K): KeyValueGroupedDataset[K, T] = left.groupByKey(f)

    def reduceByKey[K: Encoder](key: (T) => K, value: (T) => Double): Dataset[(K, Double)] = {
      left.groupByKey(key).agg(typed.sum[T](value))
    }

    def broadcastCogroup[S: Encoder : ClassTag, R: Encoder : ClassTag, K: Encoder](right: KeyValueGroupedDataset[K, S], key1: (T) => K)
      (f: (K, Iterator[T], Iterator[S]) => TraversableOnce[R])(implicit arg0: Encoder[Seq[S]], arg1: Encoder[(K, Seq[S])]): Dataset[R] = {

        val rheavyMap = right.mapGroups{
          case (key, value) => (key, value.toSeq)
        }.collect.toMap

        val rheavyBC = left.sparkSession.sparkContext.broadcast(rheavyMap)
        left.groupByKey(key1).flatMapGroups{
          case (key, values) => rheavyBC.value get key match {
            case Some(ks) => f(key, values, ks.iterator)
            case _ => f(key, values, Iterator())
          }}
    }

  }

  implicit class DataframeOps(left: DataFrame) extends Serializable {

    def print: Unit = left.collect.foreach(println(_))

    def empty[U: Encoder : TypeTag]: Dataset[U] = left.sparkSession.emptyDataset[U].repartition(1)

    def emptyDF: DataFrame = left.sparkSession.emptyDataFrame.repartition(1)

  }


  implicit class SkewDataframeKeyOps[K: ClassTag](dfs: (DataFrame, DataFrame, Option[String], Broadcast[Set[K]])) extends Serializable {

    val light = dfs._1
    val heavy = dfs._2
    val key = dfs._3
    val heavyKeys = dfs._4

    def print: Unit = (light, heavy).print

    def select(col: String, cols: String*): (DataFrame, DataFrame, Option[String], Broadcast[Set[K]])= {
      (light.select(col, cols:_*), heavy.select(col, cols:_*), key, heavyKeys)
    }

    def as[U: Encoder : TypeTag]: (Dataset[U], Dataset[U], Option[String], Broadcast[Set[K]]) = {
      if (heavy.rdd.getNumPartitions == 1) {
        (light.as[U], light.empty[U], key, heavyKeys)
      }
      else (light.as[U], heavy.as[U], key, heavyKeys)
    }

    def withColumn(colName: String, col: Column): (DataFrame, DataFrame, Option[String], Broadcast[Set[K]]) = {
      (light.withColumn(colName, col), heavy.withColumn(colName, col), key, heavyKeys)
    }

    def withColumnRenamed(existingName: String, newName: String): (DataFrame, DataFrame, Option[String], Broadcast[Set[K]]) = {
      (light.withColumnRenamed(existingName, newName), 
        heavy.withColumnRenamed(existingName, newName), key, heavyKeys)
    }

  }

  implicit class SkewDatasetKeyOps[T: Encoder : ClassTag, K: Encoder: ClassTag](dfs: (Dataset[T], Dataset[T], Option[String], Broadcast[Set[K]])) extends Serializable {
    val light = dfs._1
    val heavy = dfs._2
    val key = dfs._3 
    val heavyKeys = dfs._4
    val partitions = light.rdd.getNumPartitions

    def print: Unit = (light, heavy).print

    def count: Long = (light, heavy).count

    def cache: Unit = (light, heavy).cache

    // don't repartition a set with known heavy keys
    def repartition[S](partitionExpr: Column): (Dataset[T], Dataset[T], Option[String], Broadcast[Set[K]]) = {
      key match {
        case Some(k) if col(k) == partitionExpr => 
          //(light.repartition(Seq(partitionExpr):_*), heavy, key, heavyKeys)
          (light, heavy, key, heavyKeys)
		    case _ => 
          (light.repartition(Seq(partitionExpr):_*), heavy.repartition(Seq(partitionExpr):_*), None, 
            light.sparkSession.sparkContext.broadcast(Set.empty[K]))
      }

    }

    def union: Dataset[T] = (light, heavy).union

    def select(col: String, cols: String*): (DataFrame, DataFrame) = {
      (light.select(col, cols:_*), heavy.select(col, cols:_*))
    }

    def as[U: Encoder : TypeTag]: (Dataset[U], Dataset[U], Option[String], Broadcast[Set[K]]) = {
      if (heavy.rdd.getNumPartitions == 1){
        (light.as[U], light.empty[U], key, heavyKeys)
      }else (light.as[U], heavy.as[U], key, heavyKeys)
    }

    def withColumn(colName: String, col: Column): (DataFrame, DataFrame, Option[String], Broadcast[Set[K]]) = {
      (light.withColumn(colName, col), heavy.withColumn(colName, col), key, heavyKeys)
    }

    def withColumnRenamed(existingName: String, newName: String): (DataFrame, DataFrame, Option[String], Broadcast[Set[K]]) = {
      key match{
        case Some(k) if k == existingName =>
        (light.withColumnRenamed(existingName, newName), 
          heavy.withColumnRenamed(existingName, newName), Some(newName), heavyKeys)
        case _ => 
          (light.withColumnRenamed(existingName, newName), 
            heavy.withColumnRenamed(existingName, newName), key, heavyKeys)
      }

    }

    def mapPartitions[U: Encoder : ClassTag](func: (Iterator[T]) ⇒ Iterator[U]): (Dataset[U], Dataset[U]) = {
      (light, heavy).mapPartitions(func)
    }

    def flatMap[U: Encoder : ClassTag](func: (T) ⇒ TraversableOnce[U]): (Dataset[U], Dataset[U], Option[String], Broadcast[Set[K]]) = {
      (light.flatMap(func), heavy.flatMap(func), key, heavyKeys)
    }

    def reduceByKey[K: Encoder](key: (T) => K, value: (T) => Double)(implicit arg0: Encoder[(K, Double)]): (Dataset[(K, Double)], Dataset[(K, Double)]) = {
      (light, heavy).reduceByKey(key, value)
    }

    def groupByLabel(f: (T) => K)(implicit arg0: Encoder[(K, T)]): KeyValueGroupedDataset[K, T] = {
      (light, heavy).groupByLabel(f)
    }

    def groupByKey(f: (T) => K)(implicit arg0: Encoder[(K, T)]): KeyValueGroupedDataset[K, T] = {
      (light, heavy).groupByKey(f)
    }

    // def groupByKey[S: Encoder : ClassTag](f: (T) => S)(implicit arg0: Encoder[(S, T)]): (KeyValueGroupedDataset[S, T], KeyValueGroupedDataset[S, T], Broadcast[Set[S]]) = {
    def groupByKey[S: Encoder : ClassTag](f: (T) => S)(implicit arg0: Encoder[(S, T)]): KeyValueGroupedDataset[S, T] = {
      (light, heavy).groupByKey(f)
    }

    def lookup[S: Encoder : ClassTag, R : Encoder: ClassTag](right: (Dataset[S], Dataset[S]), lkey: (T) => K, rkey: (S) => K)
      (f: (K, Iterator[T], Iterator[S]) => TraversableOnce[R]): (Dataset[R], Dataset[R]) = 
      (light, heavy).lookup(right, lkey, rkey)(f)

    def cogroup[S: Encoder : ClassTag, R : Encoder: ClassTag, K : Encoder: ClassTag](right:  KeyValueGroupedDataset[K,S], lkey: (T) => K)
      (f: (K, Iterator[T], Iterator[S]) => TraversableOnce[R]): (Dataset[R], Dataset[R]) = {
      (light, heavy).cogroup(right, lkey)(f)
    }

    def cogroup[S: Encoder : ClassTag, R : Encoder: ClassTag](right: (Dataset[S], Dataset[S]), lkey: (T) => K, rkey: (S) => K)
      (f: (K, Iterator[T], Iterator[S]) => TraversableOnce[R]): (Dataset[R], Dataset[R]) = {
        (light, heavy).cogroup(right, lkey, rkey)(f)
    }

    def cogroup[S: Encoder : ClassTag, R : Encoder: ClassTag](right: (Dataset[S], Dataset[S], Option[String], Broadcast[Set[K]]), lkey: (T) => K, rkey: (S) => K)
      (f: (K, Iterator[T], Iterator[S]) => TraversableOnce[R]): (Dataset[R], Dataset[R]) = {
        (light, heavy).cogroup((right._1, right._2), lkey, rkey)(f)
    }

    def cogroupSkew[S: Encoder : ClassTag, R : Encoder: ClassTag](right: (Dataset[S], Dataset[S]), lkey: (T) => K, rkey: (S) => K, ckey: Option[String])
      (f: (K, Iterator[T], Iterator[S]) => TraversableOnce[R]): (Dataset[R], Dataset[R], Option[String], Broadcast[Set[K]]) = {
        if (heavyKeys.value.nonEmpty && ckey == key){
          val runion = right.union
          val rlight = runion.filter((x:S) => rkey(x) match { 
            case null => true;
            case k => !heavyKeys.value(k) }).groupByKey(rkey)
          val lresult = light.groupByKey(lkey).cogroup(rlight)(f)

          val rheavy = runion.filter((x:S) => rkey(x) match { 
            case null => true;
            case k => heavyKeys.value(k) }).collect.groupBy(rkey).toMap
          val rheavyBC = heavy.sparkSession.sparkContext.broadcast(rheavy)
          val hresult = heavy.groupByKey(lkey).flatMapGroups{
            case (key, values) => rheavyBC.value get key match {
              case Some(ks) => f(key, values, ks.iterator)
              case _ => f(key, values, Iterator())
            }}        
          (lresult, hresult, key, heavyKeys)
        }else (light, heavy).cogroupSkew(right, lkey, rkey, ckey)(f)
    }

    def cogroupSkew[S: Encoder : ClassTag, R : Encoder: ClassTag](right: (Dataset[S], Dataset[S], Option[String], Broadcast[Set[K]]), 
      lkey: (T) => K, rkey: (S) => K, ckey: Option[String])
      (f: (K, Iterator[T], Iterator[S]) => TraversableOnce[R]): (Dataset[R], Dataset[R], Option[String], Broadcast[Set[K]]) = {
        dfs.cogroupSkew((right._1, right._2), lkey, rkey, ckey)(f)
      }

    def equiJoinWith[S: Encoder : ClassTag](right: (Dataset[S], Dataset[S]), usingColumns: Seq[String], joinType: String = "inner")
    (implicit arg0: Encoder[(T,S)]): (Dataset[(T, S)], Dataset[(T,S)]) = {
      // using a heavy key
      val hkeys = key match {
        case Some(k) if usingColumns.contains(k) => heavyKeys
        case _ => light.sparkSession.sparkContext.broadcast(Set.empty[K])
      }
      if (hkeys.value.nonEmpty && !key.isEmpty){
        val rkey = usingColumns(1)
        val runion = right.union
        val rlight = runion.lfilter(col(rkey), hkeys)
        val lresult = light.joinWith(rlight, col(key.get) === col(rkey), joinType)

        val rheavy = runion.hfilter(col(rkey), hkeys)
        val hresult = heavy.joinWith(rheavy.hint("broadcast"), col(key.get) === col(rkey), joinType)

        (lresult, hresult)
      }else{
        (light, heavy).equiJoinWith[S, K](right, usingColumns, joinType)
      }

    }

    def equiJoin[S: Encoder : ClassTag](right: (Dataset[S], Dataset[S]), usingColumns: Seq[String], joinType: String = "inner"): (DataFrame, DataFrame, Option[String], Broadcast[Set[K]]) = {
      // using a heavy key
      val hkeys = key match {
        case Some(k) if usingColumns.contains(k) => heavyKeys
        case _ => light.sparkSession.sparkContext.broadcast(Set.empty[K])
      }
      if (hkeys.value.nonEmpty && !key.isEmpty){
        val rkey = usingColumns(1)
        val runion = right.union
        val rlight = runion.lfilter(col(rkey), hkeys)
        val lresult = light.join(rlight, col(key.get) === col(rkey), joinType)

        val rheavy = runion.hfilter(col(rkey), hkeys)
        val hresult = heavy.join(rheavy.hint("broadcast"), col(key.get) === col(rkey), joinType)

        (lresult, hresult, key, hkeys)
      }else{
        (light, heavy).equiJoin[S, K](right, usingColumns, joinType)
      }

    }

    def outerjoin[S: Encoder : ClassTag](right: (Dataset[S], Dataset[S]), usingColumns: Seq[String], joinType: String = "full_outer")(implicit arg0: Encoder[(T,S)]): 
    (Dataset[(T,S)], Dataset[(T,S)], Option[String], Broadcast[Set[K]]) = {
      // using a heavy key
      val hkeys = key match {
        case Some(k) if usingColumns.contains(k) => heavyKeys
        case _ => light.sparkSession.sparkContext.broadcast(Set.empty[K])
      }
      if (hkeys.value.nonEmpty && !key.isEmpty){
        val rkey = usingColumns(1)
        val runion = right.union
        val rlight = runion.lfilter(col(rkey), hkeys)
        val lresult = light.joinWith(rlight, col(key.get) === col(rkey), joinType)

        val rheavy = runion.hfilter(col(rkey), hkeys)
        val hresult = heavy.joinWith(rheavy.hint("broadcast"), col(key.get) === col(rkey), joinType)

        (lresult, hresult, key, hkeys)
      }else{
        (light, heavy).outerjoin[S, K](right, usingColumns, joinType)
      }
    }

  }

  implicit class SkewDataframeOps(dfs: (DataFrame, DataFrame)) extends Serializable {

    val light = dfs._1
    val heavy = dfs._2

    def print: Unit = {
      println("light")
      light.collect.foreach(println(_))
      println("heavy")
      heavy.collect.foreach(println(_))
    }

    def select(col: String, cols: String*): (DataFrame, DataFrame) = {
      (light.select(col, cols:_*), heavy.select(col, cols:_*))
    }

    def as[U: Encoder : TypeTag]: (Dataset[U], Dataset[U]) = {
      if (heavy.rdd.getNumPartitions == 1){
        (light.as[U], light.empty[U])
      }else (light.as[U], heavy.as[U])
    }

    def withColumn(colName: String, col: Column): (DataFrame, DataFrame) = {
      (light.withColumn(colName, col), heavy.withColumn(colName, col))
    }

    def withColumnRenamed(existingName: String, newName: String): (DataFrame, DataFrame) = {
      (light.withColumnRenamed(existingName, newName), 
        heavy.withColumnRenamed(existingName, newName))
    }

  }

  implicit class SkewDatasetOps[T: Encoder : ClassTag](dfs: (Dataset[T], Dataset[T])) extends Serializable {
    
    val light = dfs._1
    val heavy = dfs._2
    val partitions = light.rdd.getNumPartitions
    val random = scala.util.Random

    def print: Unit = {
      println("light")
      light.collect.foreach(println(_))
      println("heavy")
      heavy.collect.foreach(println(_))
    }

    def count: Long = {
      val lc = light.count
      val hc = heavy.count
      lc + hc
    }

    def cache: Unit = {
      light.cache
      heavy.cache
    }

    def repartition[K: ClassTag](partitionExpr: Column): (Dataset[T], Dataset[T], Option[String], Broadcast[Set[K]]) = {
      val key = partitionExpr.toString
      val (dfull, hkeys) = heavyKeys[K](key)
      if (hkeys.nonEmpty){
        val hk = dfull.sparkSession.sparkContext.broadcast(hkeys)
        (dfull.lfilter[K](col(key), hk).repartition(Seq(partitionExpr):_*), dfull.hfilter[K](col(key), hk), Some(key), hk)
      }else (light.repartition(Seq(partitionExpr):_*), heavy.repartition(Seq(partitionExpr):_*),
        None, light.sparkSession.sparkContext.broadcast(Set.empty[K]))
    }

    def union: Dataset[T] = if (heavy.rdd.getNumPartitions == 1) light 
      else light.union(heavy)

    def select(col: String, cols: String*): (DataFrame, DataFrame) = {
      (light.select(col, cols:_*), heavy.select(col, cols:_*))
    }

    def as[U: Encoder : TypeTag]: (Dataset[U], Dataset[U]) = if (heavy.rdd.getNumPartitions == 1) (light.as[U], light.empty[U])
      else (light.as[U], heavy.as[U])

    def withColumn(colName: String, col: Column): (DataFrame, DataFrame) = {
      (light.withColumn(colName, col), heavy.withColumn(colName, col))
    }

    def withColumnRenamed(existingName: String, newName: String): (DataFrame, DataFrame) = {
      (light.withColumnRenamed(existingName, newName), 
        heavy.withColumnRenamed(existingName, newName))
    }

    def heavyKeys[K: ClassTag](key: String): (Dataset[T], Set[K]) = {
      val dfull = dfs.union
      val keys = dfull.select(key).rdd.mapPartitions(it => {
        var cnt = 0
        val acc = HashMap.empty[Row, Int].withDefaultValue(0)
        it.foreach{ c => cnt +=1; if (random.nextDouble <= .1) acc(c) += 1 }
        acc.filter(_._2 > (cnt*.1)*.0025).map(r => r._1.getAs[K](0)).iterator
      }).collect.toSet
      (dfull, keys)
    }

    def heavyKeys[K:ClassTag](f: (T) => K): (Dataset[T], Set[K]) = {
      val dfull = dfs.union
      val keys = dfull.rdd.map(f).mapPartitions(it => {
        var cnt = 0
        val acc = HashMap.empty[K, Int].withDefaultValue(0)
        it.foreach{ c => cnt +=1; if (random.nextDouble <= .1) acc(c) += 1 }
        acc.filter(_._2 > (cnt*.1)*.0025).map(r => r._1).iterator
      }).collect.toSet
      (dfull, keys)
    }

    def mapPartitions[U: Encoder : ClassTag](func: (Iterator[T]) ⇒ Iterator[U]): (Dataset[U], Dataset[U]) = {
      (light.mapPartitions(func), heavy.mapPartitions(func))
    }

    def flatMap[U: Encoder : ClassTag](func: (T) ⇒ TraversableOnce[U]): (Dataset[U], Dataset[U]) = {
      (light.flatMap(func), heavy.flatMap(func))
    }

    def reduceByKey[K: Encoder](key: (T) => K, value: (T) => Double)(implicit arg0: Encoder[(K, Double)]): (Dataset[(K, Double)], Dataset[(K, Double)]) = {
      val dfull = dfs.union
      val result = dfull.reduceByKey(key, value)
      (result, result.empty)
    }

    def groupByLabel[K: Encoder : ClassTag](f: (T) => K)(implicit arg0: Encoder[(K, T)]): KeyValueGroupedDataset[K, T] = {
      dfs.union.groupByKey(f)
    }

    def groupByKey[K: Encoder : ClassTag](f: (T) => K)(implicit arg0: Encoder[(K, T)]): KeyValueGroupedDataset[K, T] = {
      dfs.union.groupByKey(f)
    }

    def cogroup[S: Encoder : ClassTag, R : Encoder: ClassTag, K : Encoder: ClassTag](right:  KeyValueGroupedDataset[K,S], lkey: (T) => K)
      (f: (K, Iterator[T], Iterator[S]) => TraversableOnce[R]): (Dataset[R], Dataset[R]) = {
      val result = dfs.union.cogroup(right, lkey)(f)
      (result, result.empty)
    }

    def cogroup[S: Encoder : ClassTag, R : Encoder: ClassTag, K : Encoder: ClassTag](right: (Dataset[S], Dataset[S]), lkey: (T) => K, rkey: (S) => K)
      (f: (K, Iterator[T], Iterator[S]) => TraversableOnce[R]): (Dataset[R], Dataset[R]) = {
        val result = dfs.union.cogroup(right.union, lkey, rkey)(f)
        (result, result.empty)
    }

    def cogroup[S: Encoder : ClassTag, R : Encoder: ClassTag, K : Encoder: ClassTag](right: (Dataset[S], Dataset[S], Option[String], Broadcast[Set[K]]), lkey: (T) => K, rkey: (S) => K)
      (f: (K, Iterator[T], Iterator[S]) => TraversableOnce[R]): (Dataset[R], Dataset[R]) = {
        dfs.cogroup((right._1, right._2), lkey, rkey)(f)
    }

    def cogroupSkew[S: Encoder : ClassTag, R : Encoder: ClassTag, K : Encoder: ClassTag](right: (Dataset[S], Dataset[S]), 
      lkey: (T) => K, rkey: (S) => K, key: Option[String])
      (f: (K, Iterator[T], Iterator[S]) => TraversableOnce[R]): (Dataset[R], Dataset[R], Option[String], Broadcast[Set[K]]) = {
        val (dfull, hk) = heavyKeys[K](key.get)
        if (hk.nonEmpty){
          val hkeys = dfull.sparkSession.sparkContext.broadcast(hk)
          val dlight = dfull.filter((x:T) => lkey(x) match { 
            case null => true; 
            case k => !hkeys.value(k) })
          val dheavy = dfull.filter((x:T) => lkey(x) match { 
            case null => true; 
            case k => hkeys.value(k) })
          (dlight, dheavy, key, hkeys).cogroupSkew(right, lkey, rkey, key)(f)
        }else{
          val result = dfull.cogroup(right.union, lkey, rkey)(f)
          (result, result.empty, key, result.sparkSession.sparkContext.broadcast(Set.empty[K]))
        }
    }

    def cogroupSkew[S: Encoder : ClassTag, R : Encoder: ClassTag, K : Encoder: ClassTag](right: (Dataset[S], Dataset[S], Option[String], Broadcast[Set[K]]), 
      lkey: (T) => K, rkey: (S) => K, key: Option[String])
      (f: (K, Iterator[T], Iterator[S]) => TraversableOnce[R]): (Dataset[R], Dataset[R], Option[String], Broadcast[Set[K]]) = {
        dfs.cogroupSkew((right._1, right._2), lkey, rkey, key)(f)
      }

    def lookup[S: Encoder : ClassTag, R : Encoder: ClassTag, K : Encoder: ClassTag](right: (Dataset[S], Dataset[S]), lkey: (T) => K, rkey: (S) => K)
      (f: (K, Iterator[T], Iterator[S]) => TraversableOnce[R]): (Dataset[R], Dataset[R]) = {
        val result = dfs.union.cogroup((right._1, right._2).union, lkey, rkey)(f)
        (result, result.empty)
    }

    def lookup[S: Encoder : ClassTag, R : Encoder: ClassTag, K : Encoder: ClassTag](right: (Dataset[S], Dataset[S], Option[String], Broadcast[Set[K]]), 
      lkey: (T) => K, rkey: (S) => K)
      (f: (K, Iterator[T], Iterator[S]) => TraversableOnce[R]): (Dataset[R], Dataset[R]) = {
        val result = dfs.union.cogroup((right._1, right._2).union, lkey, rkey)(f)
        (result, result.empty)
    }

    def equiJoinWith[S: Encoder : ClassTag, K: Encoder : ClassTag](right: (Dataset[S], Dataset[S]), usingColumns: Seq[String], joinType: String = "inner")
      (implicit arg0: Encoder[(T,S)]): (Dataset[(T, S)], Dataset[(T,S)]) = {
      val nkey = usingColumns(0)
      val (dfull, hk) = heavyKeys[K](nkey)
      if (hk.nonEmpty){
        val hkeys = dfull.sparkSession.sparkContext.broadcast(hk)
        (dfull.lfilter[K](col(nkey), hkeys), dfull.hfilter[K](col(nkey), hkeys), Some(nkey), hkeys).equiJoinWith(right, usingColumns, joinType)
      }else{
        val result = dfull.equiJoinWith(right.union, usingColumns, joinType)
        (result, result.sparkSession.emptyDataset[(T,S)].repartition(1))
      }
    }

    def equiJoin[S: Encoder : ClassTag, K: ClassTag](right: (Dataset[S], Dataset[S]), usingColumns: Seq[String], joinType: String = "inner")(implicit arg0: Encoder[K]): 
    (DataFrame, DataFrame, Option[String], Broadcast[Set[K]]) = {
      val nkey = usingColumns(0)
      val (dfull, hk) = heavyKeys[K](nkey)
      if (hk.nonEmpty){
        val hkeys = dfull.sparkSession.sparkContext.broadcast(hk)
        (dfull.lfilter[K](col(nkey), hkeys), dfull.hfilter[K](col(nkey), hkeys), Some(nkey), hkeys).equiJoin(right, usingColumns, joinType)
      }else{
        (dfull.equiJoin(right.union, usingColumns, joinType), light.emptyDF, Some(nkey), light.sparkSession.sparkContext.broadcast(Set.empty[K]))
      }
    }

    def outerjoin[S: Encoder : ClassTag, K: Encoder : ClassTag](right: (Dataset[S], Dataset[S]), usingColumns: Seq[String], joinType: String = "full_outer")(implicit arg0: Encoder[(T,S)]): 
    (Dataset[(T,S)], Dataset[(T,S)], Option[String], Broadcast[Set[K]]) = {
      val nkey = usingColumns(0)
      val (dfull, hk) = heavyKeys[K](nkey)
      if (hk.nonEmpty){
        val hkeys = dfull.sparkSession.sparkContext.broadcast(hk)
        (dfull.lfilter[K](col(nkey), hkeys), dfull.hfilter[K](col(nkey), hkeys), Some(nkey), hkeys).outerjoin(right, usingColumns, joinType)
      }else{
        val result = dfull.outerjoin(right.union, usingColumns, joinType)
        (result, result.empty, Some(nkey), result.sparkSession.sparkContext.broadcast(Set.empty[K]))
      }
    }


  }

  
  implicit class SkewKeyValueDatasetOps[K: Encoder : ClassTag, V: Encoder : ClassTag](dfs: (KeyValueGroupedDataset[K, V], KeyValueGroupedDataset[K, V], Broadcast[Set[K]])) extends Serializable {

    val light = dfs._1
    val heavy = dfs._2
    val key = Some("_1")
    val heavyKeys = dfs._3

    def mapGroups[S: Encoder: ClassTag](func: (K, Iterator[V]) => (K, S))(implicit arg0: Encoder[(K, S)]): (Dataset[(K,S)], Dataset[(K,S)], Option[String], Broadcast[Set[K]]) = {
      (light.mapGroups(func), heavy.mapGroups(func), key, heavyKeys)
    }

  }
  

}