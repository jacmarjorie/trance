package shredding.nrc

import shredding.core.VarDef
import shredding.runtime.{Evaluator, RuntimeContext, ScalaPrinter, ScalaShredding}
import shredding.examples.tpch
import shredding.examples.tpch.TPCHSchema

object TestMaterialization extends App
  with MaterializeNRC
  with Shredding
  with ScalaShredding
  with ScalaPrinter
  with Materialization
  with Printer
  with Evaluator
  with Optimizer {

  def run(program: Program): Unit = {
    println("Program: \n" + quote(program) + "\n")

    val shredded = shred(program)
    println("Shredded program: \n" + quote(shredded) + "\n")

    val optShredded = optimize(shredded)
    println("Shredded program optimized: \n" + quote(optShredded) + "\n")

    val materializedProgram = materialize(optShredded, eliminateDomains = true)
    println("Materialized program: \n" + quote(materializedProgram.program) + "\n")

    val unshredded = unshred(optShredded, materializedProgram.ctx)
    println("Unshredded program: \n" + quote(unshredded) + "\n")

    val lDict = List[Map[String, Any]](
      Map("l_orderkey" -> 1, "l_partkey" -> 42, "l_suppkey" -> 789, "l_quantity" -> 7.0)
    )
    val pDict = List[Map[String, Any]](
      Map("p_partkey" -> 42, "p_name" -> "Kettle", "p_retailprice" -> 12.45)
    )
    val cDict = List[Map[String, Any]](
      Map("c_custkey" -> 10, "c_name" -> "Alice")
    )
    val oDict = List[Map[String, Any]](
      Map("o_orderkey" -> 1, "o_custkey" -> 10, "o_orderdate" -> 20200317)
    )
    val sDict = List[Map[String, Any]](
      Map("s_suppkey" -> 789, "s_name" -> "Supplier#1")
    )

    val ctx = new RuntimeContext
    ctx.add(VarDef(inputBagName("L__D"), TPCHSchema.lineittype), lDict)
    ctx.add(VarDef(inputBagName("P__D"), TPCHSchema.parttype), pDict)
    ctx.add(VarDef(inputBagName("C__D"), TPCHSchema.customertype), cDict)
    ctx.add(VarDef(inputBagName("O__D"), TPCHSchema.orderstype), oDict)
    ctx.add(VarDef(inputBagName("S__D"), TPCHSchema.suppliertype), sDict)

    println("Program eval: ")
    eval(materializedProgram.program, ctx)
    materializedProgram.program.statements.foreach { s =>
      println("  " + s.name + " = " + ctx(VarDef(s.name, s.rhs.tp)))
    }

    println("Unshredded program eval: ")
    eval(unshredded, ctx)
    program.statements.foreach { s =>
      println("  " + s.name + " = " + ctx(VarDef(s.name, s.rhs.tp)))
    }

  }

//  run(tpch.Query1.program.asInstanceOf[Program])
//  run(tpch.Query2.program.asInstanceOf[Program])
//  run(tpch.Query3.program.asInstanceOf[Program])
  run(tpch.Query4.program.asInstanceOf[Program])
//  run(tpch.Query5.program.asInstanceOf[Program])
//  run(tpch.Query6.program.asInstanceOf[Program])
//  run(tpch.Query7.program.asInstanceOf[Program])
}