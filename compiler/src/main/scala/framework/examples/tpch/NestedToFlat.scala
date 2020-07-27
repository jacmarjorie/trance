package framework.examples.tpch

import framework.common._
import framework.examples.Query
import framework.nrc.MaterializeNRC

/** Benchmark Query: Nested to Flat **/

object Test0Agg0 extends TPCHBase {

  val name = "Test0Agg0"
  val tbls: Set[String] = Set("L", "P")

  val (parts, partRef) = varset(Test0Full.name, "l", Test0Full.program(Test0Full.name).varRef.asInstanceOf[BagExpr])

  val query = ReduceByKey(
    ForeachUnion(partRef, parts,
      ForeachUnion(pr, relP,
        IfThenElse(Cmp(OpEq, partRef("l_partkey"), pr("p_partkey")),
          Singleton(Tuple("p_name" -> pr("p_name"), "total" -> 
            partRef("l_quantity").asNumeric * pr("p_retailprice").asNumeric))))),
      List("p_name"), List("total"))

  val program = Program(Assignment(name, query))
}

object Test0Agg0Full extends TPCHBase {

  val name = "Test0Agg0Full"
  val tbls: Set[String] = Set("L", "P")

  val (parts, partRef) = varset(Test0Full.name, "l", Test0Full.program(Test0Full.name).varRef.asInstanceOf[BagExpr])

  val query = ReduceByKey(
    ForeachUnion(partRef, parts,
      ForeachUnion(pr, relP,
        IfThenElse(Cmp(OpEq, partRef("l_partkey"), pr("p_partkey")),
          projectTuple(pr, Map("total" -> 
            partRef("l_quantity").asNumeric * pr("p_retailprice").asNumeric), List("p_retailprice"))))),
      List("p_name", "p_mfgr", "p_brand", "p_type", "p_size", "p_container", "p_comment"), List("total"))

  val program = Program(Assignment(name, query))

}

object Test1Agg1 extends TPCHBase {

  val name = "Test1Agg1"
  val tbls: Set[String] = Set("L", "O", "P")

  val (orders, orderRef) = varset(Test1Full.name, "o", Test1Full.program(Test1Full.name).varRef.asInstanceOf[BagExpr])
  val (parts, partRef) = varset("parts", "l", BagProject(orderRef, "o_parts"))

  val query = ReduceByKey(
    ForeachUnion(orderRef, orders,
      ForeachUnion(partRef, BagProject(orderRef, "o_parts"),
        ForeachUnion(pr, relP,
          IfThenElse(Cmp(OpEq, partRef("l_partkey"), pr("p_partkey")),
            Singleton(Tuple("o_orderdate" -> orderRef("o_orderdate"), "total" -> 
              partRef("l_quantity").asNumeric * pr("p_retailprice").asNumeric)))))),
      List("o_orderdate"), List("total"))

  val program = Program(Assignment(name, query))
}

object Test1Agg1Full extends TPCHBase {

  val name = "Test1Agg1Full"
  val tbls: Set[String] = Set("L", "O", "P")

  val (orders, orderRef) = varset(Test1Full.name, "o", Test1Full.program(Test1Full.name).varRef.asInstanceOf[BagExpr])
  val (parts, partRef) = varset("parts", "l", BagProject(orderRef, "o_parts"))

  val query = ReduceByKey(
    ForeachUnion(orderRef, orders,
      ForeachUnion(partRef, BagProject(orderRef, "o_parts"),
        ForeachUnion(pr, relP,
          IfThenElse(Cmp(OpEq, partRef("l_partkey"), pr("p_partkey")),
            projectTuple(orderRef, Map("total" -> partRef("l_quantity").asNumeric * pr("p_retailprice").asNumeric),
              List("o_parts")))))),
      List("o_shippriority","o_orderdate","o_custkey","o_orderpriority","o_clerk","o_orderstatus",
        "o_totalprice","o_orderkey","o_comment"), 
      List("total"))

  val program = Program(Assignment(name, query))

}

object Test2Agg2 extends TPCHBase {

  val name = "Test2Agg2"
  val tbls: Set[String] = Set("L", "O", "C", "P")

  val (customers, customerRef) = varset(Test2Full.name, "c", Test2Full.program(Test2Full.name).varRef.asInstanceOf[BagExpr])
  val (orders, orderRef) = varset("orders", "o", BagProject(customerRef, "c_orders"))
  val (parts, partRef) = varset("parts", "l", BagProject(orderRef, "o_parts"))

  val query = ReduceByKey(ForeachUnion(customerRef, customers,
    ForeachUnion(orderRef, BagProject(customerRef, "c_orders"),
      ForeachUnion(partRef, BagProject(orderRef, "o_parts"),
        ForeachUnion(pr, relP,
          IfThenElse(Cmp(OpEq, partRef("l_partkey"), pr("p_partkey")),
            Singleton(Tuple("c_name" -> customerRef("c_name"), "total" -> partRef("l_quantity").asNumeric * pr("p_retailprice").asNumeric))))))),
      List("c_name"), List("total"))

  val program = Program(Assignment(name, query))
}

object Test2Agg2Full extends TPCHBase {

  val name = "Test2Agg2Full"
  val tbls: Set[String] = Set("L", "O", "C", "P")

  val (customers, customerRef) = varset(Test2Full.name, "c", Test2Full.program(Test2Full.name).varRef.asInstanceOf[BagExpr])
  val (orders, orderRef) = varset("orders", "o", BagProject(customerRef, "c_orders"))
  val (parts, partRef) = varset("parts", "l", BagProject(orderRef, "o_parts"))

  val query = ReduceByKey(ForeachUnion(customerRef, customers,
    ForeachUnion(orderRef, BagProject(customerRef, "c_orders"),
      ForeachUnion(partRef, BagProject(orderRef, "o_parts"),
        ForeachUnion(pr, relP,
          IfThenElse(Cmp(OpEq, partRef("l_partkey"), pr("p_partkey")),
            projectTuple(customerRef, Map("total" ->
              partRef("l_quantity").asNumeric * pr("p_retailprice").asNumeric), List("c_orders"))))))),
      List("c_name", "c_acctbal", "c_nationkey", "c_custkey", "c_comment", "c_address", "c_mktsegment", "c_phone"), 
      List("total"))

  val program = Program(Assignment(name, query))
}

object Test2Agg2S extends TPCHBase {

  val name = "Test2Agg2S"
  val tbls: Set[String] = Set("L", "O", "C", "P")

  val (customers, customerRef) = varset(Test2Full.name, "c", Test2Full.program(Test2Full.name).varRef.asInstanceOf[BagExpr])
  val (orders, orderRef) = varset("orders", "o", BagProject(customerRef, "c_orders"))
  val (parts, partRef) = varset("parts", "l", BagProject(orderRef, "o_parts"))

  val step1 = ForeachUnion(customerRef, customers, 
      projectTuple(customerRef, ("c_orders" ->
        ForeachUnion(orderRef, BagProject(customerRef, "c_orders"),
          projectTuple(orderRef, ("o_parts" ->
            ReduceByKey(
                ForeachUnion(partRef, BagProject(orderRef, "o_parts"),
                  ForeachUnion(pr, relP,
                    IfThenElse(Cmp(OpEq, partRef("l_partkey"), pr("p_partkey")),
                      Singleton(Tuple("subtotal" -> 
                        partRef("l_quantity").asNumeric * pr("p_retailprice").asNumeric))))),
                Nil,
                List("subtotal"))))))))

  val (s1, s1Ref) = varset("step1", "s1", step1)
  val (subOrders, sorderRef) = varset("osub", "o", BagProject(s1Ref, "c_orders"))
  val (subTotals, subtotRef) = varset("tsub", "t", BagProject(sorderRef, "o_parts"))
  val step2 = ForeachUnion(s1Ref, s1, 
    projectTuple(s1Ref, ("c_orders" -> 
      ForeachUnion(sorderRef, BagProject(s1Ref, "c_orders"),
        ForeachUnion(subtotRef, BagProject(sorderRef, "o_parts"),
          Singleton(Tuple("stot" -> subtotRef("subtotal"))))))))
  
  val (s2, s2Ref) = varset("step2", "s2", step2)
  val (s2s, s2sRef) = varset("ssub", "s3", BagProject(s2Ref, "c_orders"))

  val query = ReduceByKey(
    ForeachUnion(s2Ref, s2, 
      ForeachUnion(s2sRef, BagProject(s2Ref, "c_orders"),
        Singleton(Tuple("c_name" -> s2Ref("c_name"), "total" -> s2sRef("stot"))))),
      List("c_name"), 
      List("total"))

  val program = Program(Assignment("step1", step1), Assignment("step2", step2), Assignment(name, query))

}

object Test2Agg2FullS extends TPCHBase {

  val name = "Test2Agg2FullS"
  val tbls: Set[String] = Set("L", "O", "C", "P")

  val (customers, customerRef) = varset(Test2Full.name, "c", Test2Full.program(Test2Full.name).varRef.asInstanceOf[BagExpr])
  val (orders, orderRef) = varset("orders", "o", BagProject(customerRef, "c_orders"))
  val (parts, partRef) = varset("parts", "l", BagProject(orderRef, "o_parts"))

  val step1 = ForeachUnion(customerRef, customers, 
      projectTuple(customerRef, ("c_orders" ->
        ForeachUnion(orderRef, BagProject(customerRef, "c_orders"),
          projectTuple(orderRef, ("o_parts" ->
            ReduceByKey(
                ForeachUnion(partRef, BagProject(orderRef, "o_parts"),
                  ForeachUnion(pr, relP,
                    IfThenElse(Cmp(OpEq, partRef("l_partkey"), pr("p_partkey")),
                      Singleton(Tuple("subtotal" -> 
                        partRef("l_quantity").asNumeric * pr("p_retailprice").asNumeric))))),
                Nil,
                List("subtotal"))))))))

  val (s1, s1Ref) = varset("step1", "s1", step1)
  val (subOrders, sorderRef) = varset("osub", "o", BagProject(s1Ref, "c_orders"))
  val (subTotals, subtotRef) = varset("tsub", "t", BagProject(sorderRef, "o_parts"))
  val step2 = ForeachUnion(s1Ref, s1, 
    projectTuple(s1Ref, ("c_orders" -> 
      ForeachUnion(sorderRef, BagProject(s1Ref, "c_orders"),
        ForeachUnion(subtotRef, BagProject(sorderRef, "o_parts"),
          Singleton(Tuple("stot" -> subtotRef("subtotal"))))))))
  
  val (s2, s2Ref) = varset("step2", "s2", step2)
  val (s2s, s2sRef) = varset("ssub", "s3", BagProject(s2Ref, "c_orders"))

  val query = ReduceByKey(
    ForeachUnion(s2Ref, s2, 
      ForeachUnion(s2sRef, BagProject(s2Ref, "c_orders"),
        projectTuple(s2Ref, Map("total" -> s2sRef("stot")), List("c_orders")))),
      List("c_name", "c_acctbal", "c_nationkey", "c_custkey", "c_comment", "c_address", "c_mktsegment", "c_phone"), 
      List("total"))

  val program = Program(Assignment("step1", step1), Assignment("step2", step2), Assignment(name, query))

}

object Test3Agg3 extends TPCHBase {

  val name = "Test3Agg3"
  val tbls: Set[String] = Set("L", "O", "C", "P", "N")

  val (nations, nationRef) = varset(Test3Full.name, "n", Test3Full.program(Test3Full.name).varRef.asInstanceOf[BagExpr])
  val (customers, customerRef) = varset("orders", "c", BagProject(nationRef, "n_custs"))
  val (orders, orderRef) = varset("orders", "o", BagProject(customerRef, "c_orders"))
  val (parts, partRef) = varset("parts", "l", BagProject(orderRef, "o_parts"))

  val query = ReduceByKey(
    ForeachUnion(nationRef, nations,
      ForeachUnion(customerRef, BagProject(nationRef, "n_custs"),
        ForeachUnion(orderRef, BagProject(customerRef, "c_orders"),
          ForeachUnion(partRef, BagProject(orderRef, "o_parts"),
            ForeachUnion(pr, relP,
              IfThenElse(Cmp(OpEq, partRef("l_partkey"), pr("p_partkey")),
                Singleton(Tuple("n_name" -> nationRef("n_name"), "total" -> 
                  partRef("l_quantity").asNumeric * pr("p_retailprice").asNumeric)))))))),
          List("n_name"), List("total"))

  val program = Program(Assignment(name, query))
}

object Test3Agg3Full extends TPCHBase {

  val name = "Test3Agg3Full"
  val tbls: Set[String] = Set("L", "O", "C", "P", "N")

  val (nations, nationRef) = varset(Test3Full.name, "n", Test3Full.program(Test3Full.name).varRef.asInstanceOf[BagExpr])
  val (customers, customerRef) = varset("orders", "c", BagProject(nationRef, "n_custs"))
  val (orders, orderRef) = varset("orders", "o", BagProject(customerRef, "c_orders"))
  val (parts, partRef) = varset("parts", "l", BagProject(orderRef, "o_parts"))

  val query = ReduceByKey(
    ForeachUnion(nationRef, nations,
      ForeachUnion(customerRef, BagProject(nationRef, "n_custs"),
        ForeachUnion(orderRef, BagProject(customerRef, "c_orders"),
          ForeachUnion(partRef, BagProject(orderRef, "o_parts"),
            ForeachUnion(pr, relP,
              IfThenElse(Cmp(OpEq, partRef("l_partkey"), pr("p_partkey")),
                Singleton(Tuple("n_name" -> nationRef("n_name"), "total" -> 
                  partRef("l_quantity").asNumeric * pr("p_retailprice").asNumeric)))))))),
          List("n_name"), List("total"))

  val program = Program(Assignment(name, query))
}

