package framework.examples.tpch

import framework.common._
import framework.examples.Query
import framework.nrc.MaterializeNRC

/** Benchmark queries used in experiment section: Flat to Nested **/

object Test0 extends TPCHBase {

  val name = "Test0"
  val tbls = Set("Lineitem")
 
  val query = 
  ForeachUnion(lr, relL, 
    Singleton(Tuple("l_partkey" -> lr("l_partkey"), "l_quantity" -> lr("l_quantity"))))

  val program = Program(Assignment(name, query))

}

object Test0Full extends TPCHBase {

  val name = "Test0Full"
  val tbls = Set("Lineitem")

  val query = ForeachUnion(lr, relL, projectBaseTuple(lr))

  val program = Program(Assignment(name, query))

}

object Test1 extends TPCHBase {

  val name = "Test1"
  val tbls = Set("Lineitem", "Order")
 
  val query = 
  ForeachUnion(or, relO,
    Singleton(Tuple("o_orderdate" -> or("o_orderdate"), "o_parts" ->
      ForeachUnion(lr, relL,
        IfThenElse(Cmp(OpEq, or("o_orderkey"), lr("l_orderkey")),
          Singleton(Tuple("l_partkey" -> lr("l_partkey"), "l_quantity" -> lr("l_quantity"))))))))

  val program = Program(Assignment(name, query))

}

object Test1Full extends TPCHBase {

  val name = "Test1Full"
  val tbls = Set("Lineitem", "Order")

  val query = 
  ForeachUnion(or, relO,
    projectTuple(or, "o_parts" ->
      ForeachUnion(lr, relL,
        IfThenElse(Cmp(OpEq, or("o_orderkey"), lr("l_orderkey")),
          projectBaseTuple(lr)))))

  val program = Program(Assignment(name, query))

}

object Test2 extends TPCHBase {

  val name = "Test2"
  val tbls = Set("Lineitem", "Order", "Customer")

  val query = 
  ForeachUnion(cr, relC,
    Singleton(Tuple("c_name" -> cr("c_name"), "c_orders" -> 
      ForeachUnion(or, relO,
        IfThenElse(Cmp(OpEq, cr("c_custkey"), or("o_custkey")),
          Singleton(Tuple("o_orderdate" -> or("o_orderdate"), "o_parts" ->
            ForeachUnion(lr, relL,
              IfThenElse(Cmp(OpEq, or("o_orderkey"), lr("l_orderkey")),
                Singleton(Tuple("l_partkey" -> lr("l_partkey"), "l_quantity" -> lr("l_quantity"))))))))))))
  val program = Program(Assignment(name, query))
}

object Test2Filter extends TPCHBase {

  val name = "Test2Filter"
  val tbls: Set[String] = Set("Lineitem", "Order", "Customer")

  val query = 
  ForeachUnion(cr, relC,
    Singleton(Tuple("c_name" -> cr("c_name"), "c_orders" -> 
      ForeachUnion(or, relO,
        IfThenElse(Cmp(OpEq, cr("c_custkey"), or("o_custkey")),
          IfThenElse(Cmp(OpEq, cr("c_nationkey"), Const(1, IntType)), 
            Singleton(Tuple("o_orderdate" -> or("o_orderdate"), "o_parts" ->
            ForeachUnion(lr, relL,
              IfThenElse(Cmp(OpEq, or("o_orderkey"), lr("l_orderkey")),
                Singleton(Tuple("l_partkey" -> lr("l_partkey"), "l_quantity" -> lr("l_quantity")))))))))))))
  val program = Program(Assignment(name, query))
}

object Test2Full extends TPCHBase {

  val name = "Test2Full"
  val tbls: Set[String] = Set("Lineitem", "Order", "Customer")

  val query = 
  ForeachUnion(cr, relC,
    projectTuple(cr, "c_orders" -> 
      ForeachUnion(or, relO,
        IfThenElse(Cmp(OpEq, cr("c_custkey"), or("o_custkey")),
          projectTuple(or, "o_parts" ->
            ForeachUnion(lr, relL,
              IfThenElse(Cmp(OpEq, or("o_orderkey"), lr("l_orderkey")),
                projectBaseTuple(lr))))))))
  val program = Program(Assignment(name, query))
}

object Test2Flat extends TPCHBase {

  val name = "Test2"
  val tbls: Set[String] = Set("Lineitem", "Order", "Customer")

  val oquery = 
    ForeachUnion(or, relO,
      Singleton(Tuple("o_custkey" -> or("o_custkey"), "o_orderdate" -> or("o_orderdate"), "o_parts" -> 
        ForeachUnion(lr, relL,
          IfThenElse(Cmp(OpEq, or("o_orderkey"), lr("l_orderkey")),
            Singleton(Tuple("l_partkey" -> lr("l_partkey"), "l_quantity" -> lr("l_quantity"))))))))
  val (orders, orderRef) = varset("orders", "order", oquery)
  val query = 
    ForeachUnion(cr, relC,
      Singleton(Tuple("c_name" -> cr("c_name"), "c_orders" -> 
        ForeachUnion(orderRef, orders,
          IfThenElse(Cmp(OpEq, cr("c_custkey"), orderRef("o_custkey")),
            Singleton(Tuple("o_orderdate" -> orderRef("o_orderdate"), 
              "o_parts" -> orderRef("o_parts"))))))))

  val program = Program(Assignment(orders.name, oquery), Assignment(name, query))
}

object Test2FullFlat extends TPCHBase {

  val name = "Test2Full"
  val tbls: Set[String] = Set("Lineitem", "Order", "Customer")

  val oquery = 
    ForeachUnion(or, relO,
      projectTuple(or, "o_parts" -> 
        ForeachUnion(lr, relL,
          IfThenElse(Cmp(OpEq, or("o_orderkey"), lr("l_orderkey")),
            projectBaseTuple(lr)))))

  val (orders, orderRef) = varset("orders", "order", oquery)
  val query = 
    ForeachUnion(cr, relC,
      projectTuple(cr, "c_orders" -> 
        ForeachUnion(orderRef, orders,
          IfThenElse(Cmp(OpEq, cr("c_custkey"), orderRef("o_custkey")),
            projectTuple(orderRef, "o_parts" -> orderRef("o_parts"), List("o_parts"))))))

  val program = Program(Assignment(orders.name, oquery), Assignment(name, query))
}

object Test3 extends TPCHBase {

  val name = "Test3"
  val tbls: Set[String] = Set("Lineitem", "Order", "Customer", "Nation")

  val query = 
  ForeachUnion(nr, relN,
    Singleton(Tuple("n_name" -> nr("n_name"), "n_custs" ->
      ForeachUnion(cr, relC,
        IfThenElse(Cmp(OpEq, nr("n_nationkey"), cr("c_nationkey")),
          Singleton(Tuple("c_name" -> cr("c_name"), "c_orders" -> 
            ForeachUnion(or, relO,
              IfThenElse(Cmp(OpEq, cr("c_custkey"), or("o_custkey")),
                Singleton(Tuple("o_orderdate" -> or("o_orderdate"), "o_parts" ->
                  ForeachUnion(lr, relL,
                    IfThenElse(Cmp(OpEq, or("o_orderkey"), lr("l_orderkey")),
                      Singleton(Tuple("l_partkey" -> lr("l_partkey"), "l_quantity" -> lr("l_quantity")))))))
                )))))))))

  val program = Program(Assignment(name, query))
}

object Test3Full extends TPCHBase {

  val name = "Test3Full"
  val tbls: Set[String] = Set("Lineitem", "Order", "Customer", "Nation")

  val query = 
  ForeachUnion(nr, relN,
    projectTuple(nr, "n_custs" ->
      ForeachUnion(cr, relC,
        IfThenElse(Cmp(OpEq, nr("n_nationkey"), cr("c_nationkey")),
          projectTuple(cr, "c_orders" -> 
            ForeachUnion(or, relO,
              IfThenElse(Cmp(OpEq, cr("c_custkey"), or("o_custkey")),
                projectTuple(or, "o_parts" ->
                  ForeachUnion(lr, relL,
                    IfThenElse(Cmp(OpEq, or("o_orderkey"), lr("l_orderkey")),
                      projectBaseTuple(lr)))))))))))

  val program = Program(Assignment(name, query))
}

object Test3Flat extends TPCHBase {

  val name = "Test3"
  val tbls: Set[String] = Set("Lineitem", "Order", "Customer", "Nation")

  val oquery = 
    ForeachUnion(or, relO,
      Singleton(Tuple("o_custkey" -> or("o_custkey"), "o_orderdate" -> or("o_orderdate"), "o_parts" -> 
        ForeachUnion(lr, relL,
          IfThenElse(Cmp(OpEq, or("o_orderkey"), lr("l_orderkey")),
            Singleton(Tuple("l_partkey" -> lr("l_partkey"), "l_quantity" -> lr("l_quantity"))))))))
  val (orders, orderRef) = varset("orders", "order", oquery)

  val cquery = 
    ForeachUnion(cr, relC,
      Singleton(Tuple("c_nationkey" -> cr("c_nationkey"), "c_name" -> cr("c_name"), "c_orders" -> 
        ForeachUnion(orderRef, orders,
          IfThenElse(Cmp(OpEq, cr("c_custkey"), orderRef("o_custkey")),
            Singleton(Tuple("o_orderdate" -> orderRef("o_orderdate"), 
              "o_parts" -> orderRef("o_parts"))))))))
  val (customers, customerRef) = varset("customers", "customer", cquery)
  val query = 
    ForeachUnion(nr, relN, 
      Singleton(Tuple("n_name" -> nr("n_name"), "n_custs" -> 
        ForeachUnion(customerRef, customers,
          IfThenElse(Cmp(OpEq, nr("n_nationkey"), customerRef("c_nationkey")),
            Singleton(Tuple("c_name" -> customerRef("c_name"), "c_orders" -> customerRef("c_orders"))))))))
  val program = Program(Assignment(orders.name, oquery), Assignment(customers.name, cquery), Assignment(name, query))

}

object Test3FullFlat extends TPCHBase {

  val name = "Test3Full"
  val tbls: Set[String] = Set("Lineitem", "Order", "Customer", "Nation")

  val oquery = 
    ForeachUnion(or, relO,
      projectTuple(or, "o_parts" -> 
        ForeachUnion(lr, relL,
          IfThenElse(Cmp(OpEq, or("o_orderkey"), lr("l_orderkey")),
            projectBaseTuple(lr)))))
  val (orders, orderRef) = varset("orders", "order", oquery)

  val cquery = 
    ForeachUnion(cr, relC,
      projectTuple(cr, "c_orders" -> 
        ForeachUnion(orderRef, orders,
          IfThenElse(Cmp(OpEq, cr("c_custkey"), orderRef("o_custkey")),
            projectTuple(orderRef, "o_parts" -> orderRef("o_parts"), List("o_parts"))))))
  val (customers, customerRef) = varset("customers", "customer", cquery)
  val query = 
    ForeachUnion(nr, relN, 
      projectTuple(nr, "n_custs" -> 
        ForeachUnion(customerRef, customers,
          IfThenElse(Cmp(OpEq, nr("n_nationkey"), customerRef("c_nationkey")),
            projectTuple(customerRef, "c_orders" -> customerRef("c_orders"), List("c_orders"))))))
  val program = Program(Assignment(orders.name, oquery), Assignment(customers.name, cquery), Assignment(name, query))

}

object Test4 extends TPCHBase {

  val name = "Test4"
  val tbls: Set[String] = Set("Lineitem", "Order", "Customer", "Nation", "Region")

  val query = 
  ForeachUnion(rr, relR,
    Singleton(Tuple("r_name" -> rr("r_name"), "r_nations" ->
      ForeachUnion(nr, relN,
        IfThenElse(Cmp(OpEq, nr("n_regionkey"), rr("r_regionkey")),
          Singleton(Tuple("n_name" -> nr("n_name"), "n_custs" ->
            ForeachUnion(cr, relC,
              IfThenElse(Cmp(OpEq, nr("n_nationkey"), cr("c_nationkey")),
                Singleton(Tuple("c_name" -> cr("c_name"), "c_orders" -> 
                  ForeachUnion(or, relO,
                    IfThenElse(Cmp(OpEq, cr("c_custkey"), or("o_custkey")),
                      Singleton(Tuple("o_orderdate" -> or("o_orderdate"), "o_parts" ->
                        ForeachUnion(lr, relL,
                          IfThenElse(Cmp(OpEq, or("o_orderkey"), lr("l_orderkey")),
                            Singleton(Tuple("l_partkey" -> lr("l_partkey"), "l_quantity" -> lr("l_quantity")))))))
                      )))))))))))))
  val program = Program(Assignment(name, query))
}

object Test4Full extends TPCHBase {

  val name = "Test4Full"
  val tbls: Set[String] = Set("Lineitem", "Order", "Customer", "Nation", "Region")

  val query = 
  ForeachUnion(rr, relR,
    projectTuple(rr, "r_nations" ->
      ForeachUnion(nr, relN,
        IfThenElse(Cmp(OpEq, nr("n_regionkey"), rr("r_regionkey")),
          projectTuple(nr, "n_custs" ->
            ForeachUnion(cr, relC,
              IfThenElse(Cmp(OpEq, nr("n_nationkey"), cr("c_nationkey")),
                projectTuple(cr, "c_orders" -> 
                  ForeachUnion(or, relO,
                    IfThenElse(Cmp(OpEq, cr("c_custkey"), or("o_custkey")),
                      projectTuple(or, "o_parts" ->
                        ForeachUnion(lr, relL,
                          IfThenElse(Cmp(OpEq, or("o_orderkey"), lr("l_orderkey")),
                            projectBaseTuple(lr))))))))))))))
  val program = Program(Assignment(name, query))
}

object Test4Flat extends TPCHBase {

  val name = "Test4"
  val tbls: Set[String] = Set("Lineitem", "Order", "Customer", "Nation", "Region")

  val oquery = 
    ForeachUnion(or, relO,
      Singleton(Tuple("o_custkey" -> or("o_custkey"), "o_orderdate" -> or("o_orderdate"), "o_parts" -> 
        ForeachUnion(lr, relL,
          IfThenElse(Cmp(OpEq, or("o_orderkey"), lr("l_orderkey")),
            Singleton(Tuple("l_partkey" -> lr("l_partkey"), "l_quantity" -> lr("l_quantity"))))))))
  val (orders, orderRef) = varset("orders", "order", oquery)

  val cquery = 
    ForeachUnion(cr, relC,
      Singleton(Tuple("c_nationkey" -> cr("c_nationkey"), "c_name" -> cr("c_name"), "c_orders" -> 
        ForeachUnion(orderRef, orders,
          IfThenElse(Cmp(OpEq, cr("c_custkey"), orderRef("o_custkey")),
            Singleton(Tuple("o_orderdate" -> orderRef("o_orderdate"), 
              "o_parts" -> orderRef("o_parts"))))))))
  val (customers, customerRef) = varset("customers", "customer", cquery)

  val nquery = ForeachUnion(nr, relN, 
    Singleton(Tuple("n_regionkey" -> nr("n_regionkey"), "n_name" -> nr("n_name"), "n_custs" -> 
      ForeachUnion(customerRef, customers, 
        IfThenElse(Cmp(OpEq, nr("n_nationkey"), customerRef("c_nationkey")), 
          Singleton(Tuple("c_name" -> customerRef("c_name"), "c_orders" -> customerRef("c_orders"))))))))
  val (nations, nationRef) = varset("nations", "nation", nquery)

  val query = 
    ForeachUnion(rr, relR,
      Singleton(Tuple("r_name" -> rr("r_name"), "r_nations" -> 
        ForeachUnion(nationRef, nations,
          IfThenElse(Cmp(OpEq, rr("r_regionkey"), nationRef("n_regionkey")),
            Singleton(Tuple("n_name" -> nationRef("n_name"), "n_custs" -> nationRef("n_custs"))))))))

  val program = Program(Assignment(orders.name, oquery), 
    Assignment(customers.name, cquery), Assignment(nations.name, nquery), Assignment(name, query))

}

object Test4FullFlat extends TPCHBase {

  val name = "Test4Full"
  val tbls: Set[String] = Set("Lineitem", "Order", "Customer", "Nation", "Region")

  val oquery = 
    ForeachUnion(or, relO,
      projectTuple(or, "o_parts" -> 
        ForeachUnion(lr, relL,
          IfThenElse(Cmp(OpEq, or("o_orderkey"), lr("l_orderkey")),
            projectBaseTuple(lr)))))
  val (orders, orderRef) = varset("orders", "order", oquery)

  val cquery = 
    ForeachUnion(cr, relC,
      projectTuple(cr, "c_orders" -> 
        ForeachUnion(orderRef, orders,
          IfThenElse(Cmp(OpEq, cr("c_custkey"), orderRef("o_custkey")),
            projectTuple(orderRef, "o_parts" -> orderRef("o_parts"), List("o_parts"))))))
  val (customers, customerRef) = varset("customers", "customer", cquery)

  val nquery = ForeachUnion(nr, relN, 
    projectTuple(nr, "n_custs" -> 
      ForeachUnion(customerRef, customers, 
        IfThenElse(Cmp(OpEq, nr("n_nationkey"), customerRef("c_nationkey")), 
          projectTuple(customerRef, "c_orders" -> customerRef("c_orders"), List("c_orders"))))))
  val (nations, nationRef) = varset("nations", "nation", nquery)

  val query = 
    ForeachUnion(rr, relR,
      projectTuple(rr, "r_nations" -> 
        ForeachUnion(nationRef, nations,
          IfThenElse(Cmp(OpEq, rr("r_regionkey"), nationRef("n_regionkey")),
            projectTuple(nationRef, "n_custs" -> nationRef("n_custs"), List("n_custs"))))))

  val program = Program(Assignment(orders.name, oquery), 
    Assignment(customers.name, cquery), Assignment(nations.name, nquery), Assignment(name, query))

}


/** Flat to nested with a join on parts at the bottom **/

object Test0Join extends TPCHBase {

  val name = "Test0"
  val tbls: Set[String] = Set("Lineitem", "Part")

  val query = 
  ForeachUnion(lr, relL,
    ForeachUnion(pr, relP,
      IfThenElse(Cmp(OpEq, lr("l_partkey"), pr("p_partkey")),
        Singleton(Tuple("p_name" -> pr("p_name"), "l_qty" -> lr("l_quantity"))))))

  val program = Program(Assignment(name, query))
}

object Test1Join extends TPCHBase {

  val name = "Test1"
  val tbls: Set[String] = Set("Order", "Lineitem", "Part")

  val query = 
  ForeachUnion(or, relO,
    Singleton(Tuple("orderdate" -> or("o_orderdate"), "o_parts" ->
      ForeachUnion(lr, relL,
        IfThenElse(Cmp(OpEq, or("o_orderkey"), lr("l_orderkey")),
          ForeachUnion(pr, relP,
            IfThenElse(Cmp(OpEq, lr("l_partkey"), pr("p_partkey")),
              Singleton(Tuple("p_name" -> pr("p_name"), "l_qty" -> lr("l_quantity"))))))))))

  val program = Program(Assignment(name, query))
}

object Test1JoinFlat extends TPCHBase {

  val name = "Test1"
  val tbls: Set[String] = Set("Customer", "Order", "Lineitem", "Part")

  val pquery = 
    ForeachUnion(lr, relL,
      ForeachUnion(pr, relP,
        IfThenElse(Cmp(OpEq, lr("l_partkey"), pr("p_partkey")),
          Singleton(Tuple("l_orderkey" -> lr("l_orderkey"),
            "p_name" -> pr("p_name"), "l_qty" -> lr("l_quantity"))))))
  val (parts, partRef) = varset("parts", "part", pquery)

  val query = 
  ForeachUnion(or, relO,
    Singleton(Tuple("o_orderdate" -> or("o_orderdate"), "o_parts" ->
      ForeachUnion(partRef, parts,
        IfThenElse(Cmp(OpEq, or("o_orderkey"), partRef("l_orderkey")),
          Singleton(Tuple("p_name" -> partRef("p_name"), "l_qty" -> partRef("l_qty"))))))))

  val program = Program(Assignment(parts.name, pquery), Assignment(name, query))
}

object Test2Join extends TPCHBase {

  val name = "Test2"
  val tbls: Set[String] = Set("Customer", "Order", "Lineitem", "Part")

  val query = 
  ForeachUnion(cr, relC,
    Singleton(Tuple("c_name" -> cr("c_name"), "c_orders" -> 
      ForeachUnion(or, relO,
        IfThenElse(Cmp(OpEq, cr("c_custkey"), or("o_custkey")),
          Singleton(Tuple("o_orderdate" -> or("o_orderdate"), "o_parts" ->
            ForeachUnion(lr, relL,
              IfThenElse(Cmp(OpEq, or("o_orderkey"), lr("l_orderkey")),
                ForeachUnion(pr, relP,
                  IfThenElse(Cmp(OpEq, lr("l_partkey"), pr("p_partkey")),
                    Singleton(Tuple("p_name" -> pr("p_name"), "l_qty" -> lr("l_quantity"))))))))))))))
  val program = Program(Assignment(name, query))
}

object Test2JoinFlat extends TPCHBase {

  val name = "Test2"
  val tbls: Set[String] = Set("Customer", "Order", "Lineitem", "Part")

  val pquery = 
    ForeachUnion(lr, relL,
      ForeachUnion(pr, relP,
        IfThenElse(Cmp(OpEq, lr("l_partkey"), pr("p_partkey")),
          Singleton(Tuple("l_orderkey" -> lr("l_orderkey"),
            "p_name" -> pr("p_name"), "l_qty" -> lr("l_quantity"))))))
  val (parts, partRef) = varset("parts", "part", pquery)

  val oquery = 
    ForeachUnion(or, relO,
      Singleton(Tuple("o_custkey" -> or("o_custkey"), "o_orderdate" -> or("o_orderdate"), "o_parts" -> 
        ForeachUnion(partRef, parts,
          IfThenElse(Cmp(OpEq, or("o_orderkey"), partRef("l_orderkey")),
            Singleton(Tuple("p_name" -> partRef("p_name"), "l_qty" -> partRef("l_qty"))))))))
  val (orders, orderRef) = varset("orders", "order", oquery)

  val query = 
    ForeachUnion(cr, relC,
      Singleton(Tuple("c_name" -> cr("c_name"), "c_orders" -> 
        ForeachUnion(orderRef, orders,
          IfThenElse(Cmp(OpEq, cr("c_custkey"), orderRef("o_custkey")),
            Singleton(Tuple("o_orderdate" -> orderRef("o_orderdate"), 
              "o_parts" -> orderRef("o_parts"))))))))
  val program = Program(Assignment(parts.name, pquery), 
    Assignment(orders.name, oquery), Assignment(name, query))
}

object Test3Join extends TPCHBase {

  val name = "Test3"
  val tbls: Set[String] = Set("Customer", "Order", "Lineitem", "Nation", "Part")

  val query = 
  ForeachUnion(nr, relN,
    Singleton(Tuple("n_name" -> nr("n_name"), "n_custs" ->
      ForeachUnion(cr, relC,
        IfThenElse(Cmp(OpEq, nr("n_nationkey"), cr("c_nationkey")),
          Singleton(Tuple("c_name" -> cr("c_name"), "c_orders" -> 
            ForeachUnion(or, relO,
              IfThenElse(Cmp(OpEq, cr("c_custkey"), or("o_custkey")),
                Singleton(Tuple("o_orderdate" -> or("o_orderdate"), "o_parts" ->
                  ForeachUnion(lr, relL,
                    IfThenElse(Cmp(OpEq, or("o_orderkey"), lr("l_orderkey")),
                      ForeachUnion(pr, relP,
                        IfThenElse(Cmp(OpEq, lr("l_partkey"), pr("p_partkey")),
                          Singleton(Tuple("p_name" -> pr("p_name"), "l_qty" -> lr("l_quantity")))))))
                  )))))))))))
  val program = Program(Assignment(name, query))
}

object Test3JoinFlat extends TPCHBase {

  val name = "Test3"
  val tbls: Set[String] = Set("Customer", "Order", "Lineitem", "Nation", "Part")

  val pquery = 
    ForeachUnion(lr, relL,
      ForeachUnion(pr, relP,
        IfThenElse(Cmp(OpEq, lr("l_partkey"), pr("p_partkey")),
          Singleton(Tuple("l_orderkey" -> lr("l_orderkey"),
            "p_name" -> pr("p_name"), "l_qty" -> lr("l_quantity"))))))
  val (parts, partRef) = varset("parts", "part", pquery)

  val oquery = 
    ForeachUnion(or, relO,
      Singleton(Tuple("o_custkey" -> or("o_custkey"), "o_orderdate" -> or("o_orderdate"), "o_parts" -> 
        ForeachUnion(partRef, parts,
          IfThenElse(Cmp(OpEq, or("o_orderkey"), partRef("l_orderkey")),
            Singleton(Tuple("p_name" -> partRef("p_name"), "l_qty" -> partRef("l_qty"))))))))
  val (orders, orderRef) = varset("orders", "order", oquery)

  val cquery = 
    ForeachUnion(cr, relC,
      Singleton(Tuple("c_nationkey" -> cr("c_nationkey"), "c_name" -> cr("c_name"), "c_orders" -> 
        ForeachUnion(orderRef, orders,
          IfThenElse(Cmp(OpEq, cr("c_custkey"), orderRef("o_custkey")),
            Singleton(Tuple("o_orderdate" -> orderRef("o_orderdate"), 
              "o_parts" -> orderRef("o_parts"))))))))
  val (customers, customerRef) = varset("customers", "customer", cquery)
  val query = 
    ForeachUnion(nr, relN, 
      Singleton(Tuple("n_name" -> nr("n_name"), "n_custs" -> 
        ForeachUnion(customerRef, customers,
          IfThenElse(Cmp(OpEq, nr("n_nationkey"), customerRef("c_nationkey")),
            Singleton(Tuple("c_name" -> customerRef("c_name"), "c_orders" -> customerRef("c_orders"))))))))

  val program = Program(Assignment(parts.name, pquery), 
    Assignment(orders.name, oquery), Assignment(customers.name, cquery), 
      Assignment(name, query))
    
}

object Test4Join extends TPCHBase {

  val name = "Test4"
  val tbls: Set[String] = Set("Customer", "Order", "Lineitem", "Nation", "Region", "Part")

  val query = 
  ForeachUnion(rr, relR,
    Singleton(Tuple("r_name" -> rr("r_name"), "r_nations" ->
      ForeachUnion(nr, relN,
        IfThenElse(Cmp(OpEq, nr("n_regionkey"), rr("r_regionkey")),
          Singleton(Tuple("n_name" -> nr("n_name"), "n_custs" ->
            ForeachUnion(cr, relC,
              IfThenElse(Cmp(OpEq, nr("n_nationkey"), cr("c_nationkey")),
                Singleton(Tuple("c_name" -> cr("c_name"), "c_orders" -> 
                  ForeachUnion(or, relO,
                    IfThenElse(Cmp(OpEq, cr("c_custkey"), or("o_custkey")),
                      Singleton(Tuple("o_orderdate" -> or("o_orderdate"), "o_parts" ->
                        ForeachUnion(lr, relL,
                          IfThenElse(Cmp(OpEq, or("o_orderkey"), lr("l_orderkey")),
                            Singleton(Tuple("p_name" -> lr("l_partkey"), "l_qty" -> lr("l_quantity")))))))
                      )))))))))))))

  val program = Program(Assignment(name, query))
}

object Test4JoinFlat extends TPCHBase {

  val name = "Test4"
  val tbls: Set[String] = Set("Customer", "Order", "Lineitem", "Nation", "Region", "Part")

  val pquery = 
    ForeachUnion(lr, relL,
      ForeachUnion(pr, relP,
        IfThenElse(Cmp(OpEq, lr("l_partkey"), pr("p_partkey")),
          Singleton(Tuple("l_orderkey" -> lr("l_orderkey"),
            "p_name" -> pr("p_name"), "l_qty" -> lr("l_quantity"))))))
  val (parts, partRef) = varset("parts", "part", pquery)

  val oquery = 
    ForeachUnion(or, relO,
      Singleton(Tuple("o_custkey" -> or("o_custkey"), "o_orderdate" -> or("o_orderdate"), "o_parts" -> 
        ForeachUnion(partRef, parts,
          IfThenElse(Cmp(OpEq, or("o_orderkey"), partRef("l_orderkey")),
            Singleton(Tuple("p_name" -> partRef("p_name"), "l_qty" -> partRef("l_qty"))))))))
  val (orders, orderRef) = varset("orders", "order", oquery)

  val cquery = 
    ForeachUnion(cr, relC,
      Singleton(Tuple("c_nationkey" -> cr("c_nationkey"), "c_name" -> cr("c_name"), "c_orders" -> 
        ForeachUnion(orderRef, orders,
          IfThenElse(Cmp(OpEq, cr("c_custkey"), orderRef("o_custkey")),
            Singleton(Tuple("o_orderdate" -> orderRef("o_orderdate"), 
              "o_parts" -> orderRef("o_parts"))))))))
  val (customers, customerRef) = varset("customers", "customer", cquery)

  val nquery = ForeachUnion(nr, relN, 
    Singleton(Tuple("n_regionkey" -> nr("n_regionkey"), "n_name" -> nr("n_name"), "n_custs" -> 
      ForeachUnion(customerRef, customers, 
        IfThenElse(Cmp(OpEq, nr("n_nationkey"), customerRef("c_nationkey")), 
          Singleton(Tuple("c_name" -> customerRef("c_name"), "c_orders" -> customerRef("c_orders"))))))))
  val (nations, nationRef) = varset("nations", "nation", nquery)

  val query = 
    ForeachUnion(rr, relR,
      Singleton(Tuple("r_name" -> rr("r_name"), "r_nations" -> 
        ForeachUnion(nationRef, nations,
          IfThenElse(Cmp(OpEq, rr("r_regionkey"), nationRef("n_regionkey")),
            Singleton(Tuple("n_name" -> nationRef("n_name"), "n_custs" -> nationRef("n_custs"))))))))

  val program = Program(Assignment(parts.name, pquery), Assignment(orders.name, oquery), 
    Assignment(customers.name, cquery), Assignment(nations.name, nquery),
    Assignment(name, query))
}


/** Flat to nested queries:
  * Suppliers - ( Customers JOIN Orders JOIN Lineitem )
  * Useful for skew
  */

object TestFN0 extends TPCHBase {
  val name = "TestFN0"
  val tbls: Set[String] = Set("Customer", "Order", "Lineitem")

  val custs = 
      ForeachUnion(or, relO,
          ForeachUnion(cr, relC,
            IfThenElse(Cmp(OpEq, cr("c_custkey"), or("o_custkey")),
              Singleton(Tuple("c_orderkey" -> or("o_orderkey"),
                 "c_name" -> cr("c_name"), "c_nationkey" -> cr("c_nationkey"), "o_orderdate" -> or("o_orderdate"))))))
  val (customers, customersRef) = varset("customers", "co", custs)

  val query5 = ForeachUnion(customersRef, customers, 
    ForeachUnion(lr, relL,
      IfThenElse(Cmp(OpEq, lr("l_orderkey"), customersRef("c_orderkey")),
        projectTuple(customersRef, Map("c_suppkey" -> lr("l_suppkey"), "l_partkey" -> lr("l_partkey"), 
          "l_quantity" -> lr("l_quantity")), List("c_orderkey")))))
                      
  val program = Program(Assignment(customers.name, custs), Assignment(name, query5))
}

object TestFN1 extends TPCHBase {
  val name = "TestFN1"
  val tbls: Set[String] = Set("Customer", "Order", "Linetime", "Supplier")

  val custs = 
      ForeachUnion(or, relO,
          ForeachUnion(cr, relC,
            IfThenElse(Cmp(OpEq, cr("c_custkey"), or("o_custkey")),
              Singleton(Tuple("c_orderkey" -> or("o_orderkey"),
                 "c_name" -> cr("c_name"), "c_nationkey" -> cr("c_nationkey"), "o_orderdate" -> or("o_orderdate"))))))
  val (customers, customersRef) = varset("customers", "co", custs)

  val custsKeyed = ForeachUnion(customersRef, customers, 
    ForeachUnion(lr, relL,
      IfThenElse(Cmp(OpEq, lr("l_orderkey"), customersRef("c_orderkey")),
        projectTuple(customersRef, Map("c_suppkey" -> lr("l_suppkey"), "l_partkey" -> lr("l_partkey"), 
          "l_quantity" -> lr("l_quantity")), List("c_orderkey")))))
  val (csuppkeys, csuppRef) = varset("csupps", "cs", custsKeyed)
                       
  val query5 = ForeachUnion(sr, relS,
                Singleton(Tuple("s_name" -> sr("s_name"), "s_nationkey" -> sr("s_nationkey"), "customers2" -> 
                  ForeachUnion(csuppRef, csuppkeys,
                    IfThenElse(Cmp(OpEq, csuppRef("c_suppkey"), sr("s_suppkey")),
                      projectBaseTuple(csuppRef, List("c_suppkey"))))))) 

  val program = Program(Assignment(customers.name, custs), 
    Assignment(csuppkeys.name, custsKeyed), Assignment(name, query5))
}

object TestFN2 extends TPCHBase {
  val name = "TestFN2"
  val tbls: Set[String] = Set("Customer", "Order", "Lineitem", "Supplier", "Nation")

  val custs = 
      ForeachUnion(or, relO,
          ForeachUnion(cr, relC,
            IfThenElse(Cmp(OpEq, cr("c_custkey"), or("o_custkey")),
              Singleton(Tuple("c_orderkey" -> or("o_orderkey"),
                 "c_name" -> cr("c_name"), "c_nationkey" -> cr("c_nationkey"), "o_orderdate" -> or("o_orderdate"))))))
  val (customers, customersRef) = varset("customers", "co", custs)

  val custsKeyed = ForeachUnion(customersRef, customers, 
    ForeachUnion(lr, relL,
      IfThenElse(Cmp(OpEq, lr("l_orderkey"), customersRef("c_orderkey")),
        projectTuple(customersRef, Map("c_suppkey" -> lr("l_suppkey"), "l_partkey" -> lr("l_partkey"), 
          "l_quantity" -> lr("l_quantity")), List("c_orderkey")))))
  val (csuppkeys, csuppRef) = varset("csupps", "cs", custsKeyed)

  val query5 = ForeachUnion(nr, relN, 
    Singleton(Tuple("n_name" -> nr("n_name"), "n_nationkey" -> nr("n_nationkey"), "n_suppliers" -> 
      ForeachUnion(sr, relS,
        Singleton(Tuple("s_name" -> sr("s_name"), "s_nationkey" -> sr("s_nationkey"), "customers2" -> 
          ForeachUnion(csuppRef, csuppkeys,
            IfThenElse(Cmp(OpEq, csuppRef("c_suppkey"), sr("s_suppkey")),
              projectBaseTuple(csuppRef, List("c_suppkey"))))))))))

  val program = Program(Assignment(customers.name, custs), 
    Assignment(csuppkeys.name, custsKeyed), Assignment(name, query5))
}

object TestFN2Full extends TPCHBase {
  val name = "TestFN2"
  val tbls: Set[String] = Set("Customer", "Order", "Lineitem", "Supplier", "Nation")

  val custs = 
      ForeachUnion(or, relO,
          ForeachUnion(cr, relC,
            IfThenElse(Cmp(OpEq, cr("c_custkey"), or("o_custkey")),
              Singleton(Tuple("c_orderkey" -> or("o_orderkey"),
                 "c_name" -> cr("c_name"), "c_nationkey" -> cr("c_nationkey"), "o_orderdate" -> or("o_orderdate"))))))
  val (customers, customersRef) = varset("customers", "co", custs)

  val custsKeyed = ForeachUnion(customersRef, customers, 
    ForeachUnion(lr, relL,
      IfThenElse(Cmp(OpEq, lr("l_orderkey"), customersRef("c_orderkey")),
        projectTuple(customersRef, Map("c_suppkey" -> lr("l_suppkey"), "l_partkey" -> lr("l_partkey"), 
          "l_quantity" -> lr("l_quantity")), List("c_orderkey")))))
  val (csuppkeys, csuppRef) = varset("csupps", "cs", custsKeyed)
                       
  val suppsKeyed = ForeachUnion(sr, relS,
                Singleton(Tuple("s_name" -> sr("s_name"), "s_nationkey" -> sr("s_nationkey"), "customers2" -> 
                  ForeachUnion(csuppRef, csuppkeys,
                    IfThenElse(Cmp(OpEq, csuppRef("c_suppkey"), sr("s_suppkey")),
                      projectBaseTuple(csuppRef, List("c_suppkey")))))))
  val (suppliers, suppliersRef) = varset("suppliers", "supplier", suppsKeyed) 

  val query5 = ForeachUnion(nr, relN, 
    Singleton(Tuple("n_name" -> nr("n_name"), "n_nationkey" -> nr("n_nationkey"), "n_suppliers" -> 
      ForeachUnion(suppliersRef, suppliers, 
        IfThenElse(Cmp(OpEq, suppliersRef("s_nationkey"), nr("n_nationkey")),
          projectBaseTuple(suppliersRef, List("s_nationkey")))))))

  val program = Program(Assignment(customers.name, custs), 
    Assignment(csuppkeys.name, custsKeyed), Assignment(suppliers.name, suppsKeyed), Assignment(name, query5))
}
