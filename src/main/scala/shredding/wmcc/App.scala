package shredding.wmcc

import shredding.examples.tpch.{TPCHQueries, TPCHSchema, TPCHLoader}
import shredding.examples.simple.{FlatTests, FlatRelations}
import shredding.examples.simple.NestedTests

object App {
    
  val translator = new NRCTranslator{}
  val normalizer = new Finalizer(new BaseNormalizer{})

  def main(args: Array[String]){
    val q1 = translator.translate(FlatTests.q1.asInstanceOf[translator.Expr])
    val normq1 = normalizer.finalize(q1)
    println(Printer.quote(normq1.asInstanceOf[CExpr]))
    val eval = new BaseScalaInterp{}
    val evaluator = new Finalizer(eval)
    eval.ctx("R") = FlatRelations.format1a
    println(evaluator.finalize(normq1.asInstanceOf[CExpr]))

    val q2 = translator.translate(TPCHQueries.query1.asInstanceOf[translator.Expr])
    val normq2 = normalizer.finalize(q2)
    println(Printer.quote(normq2.asInstanceOf[CExpr]))
    eval.ctx("C") = TPCHLoader.loadCustomer.toList 
    eval.ctx("O") = TPCHLoader.loadOrders.toList 
    eval.ctx("L") = TPCHLoader.loadLineitem.toList 
    eval.ctx("P") = TPCHLoader.loadPart.toList 
    println(evaluator.finalize(normq2.asInstanceOf[CExpr]))

  }


}