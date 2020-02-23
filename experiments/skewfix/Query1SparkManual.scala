
package experiments
/** 
This is manually written code based on the experiments
provided from Slender.s
**/
import org.apache.spark.SparkConf
import org.apache.spark.sql.SparkSession
import sprkloader._
import sprkloader.SkewPairRDD._
/** Inherit same case class from generated code **/
case class Record165(lbl: Unit)
case class Record166(l_orderkey: Int, l_quantity: Double, l_partkey: Int)
case class Record167(p_name: String, p_partkey: Int)
case class Record168(l_orderkey: Int, p_partkey: Int, l_qty: Double)
case class Record169(c_name: String, c_custkey: Int)
case class Record170(c__Fc_custkey: Int)
case class Record171(c_name: String, c_orders: Record170)
case class Record172(lbl: Record170)
case class Record173(o_orderdate: String, o_orderkey: Int, o_custkey: Int)
case class Record175(o__Fo_orderkey: Int)
case class Record176(o_orderdate: String, o_parts: Record175)
case class Record177(lbl: Record175)
case class Record179(p_partkey: Int, l_qty: Double)
case class Record232(o_orderdate: String, o_parts: Iterable[Record179])
case class Record233(c_name: String, c_orders: Iterable[Record232])
object Query1SparkManual {
  def main(args: Array[String]){
    val sf = Config.datapath.split("/").last
    val conf = new SparkConf().setMaster(Config.master).setAppName("Query1SparkManual"+sf)
    val spark = SparkSession.builder().config(conf).getOrCreate()

    val tpch = TPCHLoader(spark)
    val C = tpch.loadCustomersProj
    C.cache
    spark.sparkContext.runJob(C, (iter: Iterator[_]) => {})
    val O = tpch.loadOrdersProjBzip
    O.cache
    spark.sparkContext.runJob(O, (iter: Iterator[_]) => {})
    val L = tpch.loadLineitemProjBzip
    L.cache
    spark.sparkContext.runJob(L, (iter: Iterator[_]) => {})
    val P = tpch.loadPartProj
    P.cache
    spark.sparkContext.runJob(P, (iter: Iterator[_]) => {})
       
	  //tpch.triggerGC

    var start0 = System.currentTimeMillis()

    val l = L.map(l => l.l_partkey -> Record166(l.l_orderkey, l.l_quantity, l.l_partkey))
    val p = P.map(p => p.p_partkey -> Record167(p.p_name, p.p_partkey))
    val lpj = l.joinSkew(p)

    val OrderParts = lpj.map{ case (l, p) => l.l_orderkey -> Record179(p.p_partkey, l.l_quantity) }

    val CustomerOrders = O.map(o => o.o_orderkey -> Record173(o.o_orderdate, o.o_orderkey, o.o_custkey)).cogroup(OrderParts).flatMap{
		  case (_, (orders, parts)) => orders.map(order => order.o_custkey -> Record232(order.o_orderdate, parts))
	  }

    val c = C.map(c => c.c_custkey -> Record169(c.c_name, c.c_custkey)).cogroup(CustomerOrders).flatMap{ 
	  			case (_, (cnames, orders)) => cnames.map(c => Record233(c.c_name, orders)) 
			}
    spark.sparkContext.runJob(c, (iter: Iterator[_]) => {})
    var end0 = System.currentTimeMillis() - start0
    println("Query1SparkManual"+sf+","+Config.datapath+","+end0+",query,"+spark.sparkContext.applicationId)
  
    /**c.flatMap(c => 
      if (c.c_orders.isEmpty) List((c.c_name, null, null, null))
      else c.c_orders.flatMap(o => if (o.o_parts.isEmpty) List((c.c_name, o.o_orderdate, null, null))
         else o.o_parts.map(p => (c.c_name, o.o_orderdate, p.p_partkey, p.l_qty)))).sortBy(_._1).collect.foreach(println(_))**/

  }
}
