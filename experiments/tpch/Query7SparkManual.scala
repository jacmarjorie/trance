
package experiments
/** 
For n in N Union
  Sng((n_name := n.n_name, suppliers := For co in Query3 Union
    For x2 in co.suppliers Union
      If (x2.s_nationkey = n.n_nationkey)
      Then Sng((s_name := x2.s_name)), customers := For co in Query3 Union
    For x1 in co.customers Union
      If (x1.c_nationkey = n.n_nationkey)
      Then Sng((c_name := x1.c_name))))
**/
import org.apache.spark.SparkConf
import org.apache.spark.sql.SparkSession
import sprkloader._
import sprkloader.SkewPairRDD._
case class Record1102(p_name: String, p_partkey: Int, uniqueId: Long) extends CaseClassRecord
case class Record1103(ps_suppkey: Int, ps_partkey: Int, uniqueId: Long) extends CaseClassRecord
case class Record1104(s_name: String, s_nationkey: Int, s_suppkey: Int, uniqueId: Long) extends CaseClassRecord
case class Record1106(s_name: String, s_nationkey: Int, uniqueId: Long) extends CaseClassRecord
case class Record1107(l_orderkey: Int, l_partkey: Int, uniqueId: Long) extends CaseClassRecord
case class Record1108(o_custkey: Int, o_orderkey: Int, uniqueId: Long) extends CaseClassRecord
case class Record1109(c_name: String, c_nationkey: Int, c_custkey: Int, uniqueId: Long) extends CaseClassRecord
case class Record1111(c_name: String, c_nationkey: Int, uniqueId: Long) extends CaseClassRecord
case class Query3Out(p_name: String, suppliers: Iterable[Record1106], customers: Iterable[Record1111], uniqueId: Long) extends CaseClassRecord
object Query7SparkManual {
 def main(args: Array[String]){
   val sf = Config.datapath.split("/").last
   val conf = new SparkConf().setMaster(Config.master).setAppName("Query7SparkManual"+sf)
   val spark = SparkSession.builder().config(conf).getOrCreate()
   
val tpch = TPCHLoader(spark)
val C = tpch.loadCustomers
C.cache
C.count
val O = tpch.loadOrders
O.cache
O.count
val L = tpch.loadLineitem
L.cache
L.count
val P = tpch.loadPart
P.cache
P.count
val PS = tpch.loadPartSupp
PS.cache
PS.count
val S = tpch.loadSupplier
S.cache
S.count
val N = tpch.loadNation
N.cache
N.count

   val partsupp = PS.map(ps => ps.ps_suppkey -> ps.ps_partkey)
   val suppliers = S.map(s => s.s_suppkey -> (s.s_name, s.s_nationkey))
   val psjs = partsupp.joinSkewLeft(suppliers).map{ case (_, (ps_partkey, s_info)) => ps_partkey -> s_info }
   val orders = O.map(o => o.o_orderkey -> o.o_custkey)//o.o_custkey -> o.o_orderkey) 
   val customers = C.map(c => c.c_custkey -> (c.c_name, c.c_nationkey))
   val lineitem = L.map(l => l.l_orderkey -> l.l_partkey)

   val ojcjl = orders.joinSkewLeft(customers).map{ case (_, (o_orderkey, c_info)) => 
                o_orderkey -> c_info }.join(lineitem).map{ case (_, (c_info, l_partkey)) => l_partkey -> c_info }
   val parts = P.map(p => p.p_partkey -> p.p_name) 
   val result = parts.cogroup(psjs, ojcjl).flatMap{ case (_, (p_names, suppliers, customers)) => 
                  p_names.map(p_name => (p_name, suppliers.toArray, customers.toArray)) }
   
   result.cache
   result.count

   var start0 = System.currentTimeMillis()
   val nation = N.map(n => n.n_nationkey -> n.n_name)
   val keyedSuppliers = result.flatMap{
      case (p_name, suppliers, customers) => suppliers.map{ case (sname, snk) => snk -> sname }
   }
   val keyedCustomers = result.flatMap{
      case (p_name, suppliers, customers) => customers.map{ case (cname, cnk) => cnk -> cname }
   }
   val exportedparts = nation.cogroup(keyedSuppliers, keyedCustomers).flatMap{
      case (nk, (nations, suppliers, customers)) => nations.map{
        n => (n, suppliers, customers)
      }
   }
   //exportedparts.collect.foreach(println(_))
   exportedparts.count
   var end0 = System.currentTimeMillis() - start0
   //result.saveAsObjectFile("/nfs_qc4/query3/result")
   println("Query7SparkManual"+sf+","+Config.datapath+","+end0,spark.sparkContext.applicationId)
 }
}
