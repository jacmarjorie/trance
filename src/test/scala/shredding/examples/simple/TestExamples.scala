package shredding.examples.simple

import org.scalatest.FunSuite
import shredding.core._
import shredding.nrc.{NRC, Printer, ShredNRC}
import shredding.wmcc._


//object TestExamples extends App
class TestExamples extends FunSuite
  with NRC
  with ShredNRC
  with Printer {
  /*Examples
  June 6, 2019
  */
  def printQuery(test: String, query: Expr): Unit = {
    val printer = new Printer {}
    println("[" + test + "] print: \n" + printer.quote(query.asInstanceOf[printer.Expr]))
    val translator = new NRCTranslator {}
    val normalizer = new Finalizer(new BaseNormalizer {})
    val qTranslated = translator.translate(query.asInstanceOf[translator.Expr])
    val normq1 = normalizer.finalize(qTranslated)
    println("[" + test + "] translated: \n" + Printer.quote(normq1.asInstanceOf[CExpr]))

  }

  /*1. A query doing a simple projection on a tuple variable.
    Suppose x is a variable of type <. . . a : Bag(C). . .>
    E = x.a
    */
  test("test1") {
    println("")
    println("[TEST_1] A query doing a simple projection on a tuple variable.\n    Suppose x is a variable of type <. . . a : Bag(C). . .>\n    E = x.a")
    val tuple = TupleType("b" -> StringType, "a" -> BagType(TupleType("c" -> IntType)), "d" -> StringType)
    val relationR = BagVarRef(VarDef("R", BagType(tuple)))

    val xdef = VarDef("x", tuple)
    val xref = TupleVarRef(xdef)

    val q1 = ForeachUnion(xdef, relationR, Singleton(Tuple("E" -> xref("a"))))
    printQuery("TEST_1", q1)

    val translator = new NRCTranslator {}
    val normalizer = new Finalizer(new BaseNormalizer {})
    val q2 = translator.translate(q1.asInstanceOf[translator.Expr])
    val normq1 = normalizer.finalize(q2)
    println(Printer.quote(normq1.asInstanceOf[CExpr]))
    val eval = new BaseScalaInterp {}
    val evaluator = new Finalizer(eval)

    val data = List(
      RecordValue("b" -> "b1", "a" -> List(1,2,3),"d" -> "d1"),
      RecordValue("b" -> "b2", "a" -> List(11,22,33),"d" -> "d2"),
      RecordValue("b" -> "b3", "a" -> List(111,222,333),"d" -> "d3"),
      RecordValue("b" -> "b4", "a" -> List(1111,2222,3333),"d" -> "d4"),
    )

    eval.ctx("R") = data
    println("[TEST_1] input data :" + data)
    val evaluated = evaluator.finalize(normq1.asInstanceOf[CExpr]);
    println("[TEST_1] EVAL:" + evaluated)

    val expected = List(RecordValue("E"->List(1, 2, 3)), RecordValue("E"->List(11, 22, 33)),
      RecordValue("E"->List(111, 222, 333)), RecordValue("E"->List(1111, 2222, 3333)))
    assert(evaluated==expected)

  }

  /*
  2. Projecting every tuple in a bag onto a bag attribute. Input: R of type BagType(h. . . a : BagType(C). . .i.
    E = For x In R Union x.a
    */
  test("test2") {
    println()
    println("[TEST_2] Projecting every tuple in a bag onto a bag attribute. Input: R of type BagType(h. . . a : BagType(C). . .i.\n    E = For x In R Union x.a")

    val tuple = TupleType("b" -> StringType, "a" -> BagType(TupleType("c" -> IntType)), "d" -> StringType)
    val relationR = BagVarRef(VarDef("R", BagType(tuple)))

    val xdef = VarDef("x", tuple)
    val xref = TupleVarRef(xdef)

    val q1 = ForeachUnion(xdef, relationR, Singleton(Tuple("E" -> xref("a"))))

    printQuery("TEST_2", q1)


    val translator = new NRCTranslator {}
    val normalizer = new Finalizer(new BaseNormalizer {})
    val q2 = translator.translate(q1.asInstanceOf[translator.Expr])
    val normq1 = normalizer.finalize(q2)
    println(Printer.quote(normq1.asInstanceOf[CExpr]))
    val eval = new BaseScalaInterp {}
    val evaluator = new Finalizer(eval)

    val data = List(
      RecordValue("b" -> "b1", "a" -> List(1,2,3),"d" -> "d1"),
      RecordValue("b" -> "b2", "a" -> List(11,22,33),"d" -> "d2"),
      RecordValue("b" -> "b3", "a" -> List(111,222,333),"d" -> "d3"),
      RecordValue("b" -> "b4", "a" -> List(1111,2222,3333),"d" -> "d4"),
    )

    eval.ctx("R") = data
    println("[TEST_2] input data :" + data)
    val evaluated = evaluator.finalize(normq1.asInstanceOf[CExpr]);
    println("[TEST_2] EVAL:" + evaluated)

    val expected = List(RecordValue("E"->List(1, 2, 3)), RecordValue("E"->List(11, 22, 33)),
      RecordValue("E"->List(111, 222, 333)), RecordValue("E"->List(1111, 2222, 3333)))
    assert(evaluated==expected)


  }

  /*


  3. Taking a projection of a variable onto a bag attribute (as in the first example) and wrapping it in
  a singleton tuple.
    E = {a' = x.ai}
    */
  test("test3") {
    println()
    println("[TEST_3] Taking a projection of a variable onto a bag attribute (as in the first example) and wrapping it in\n  a singleton tuple.\n E = {a' = x.ai}")

    val tuple = TupleType("b" -> StringType, "a" -> BagType(TupleType("c" -> IntType)), "d" -> StringType)
    val relationR = BagVarRef(VarDef("R", BagType(tuple)))
    val xdef = VarDef("x", tuple)
    val xref = TupleVarRef(xdef)
    val results = Singleton(Tuple("a" -> ForeachUnion(xdef, relationR, Singleton(Tuple("E" -> xref("a"))))))

    printQuery("TEST_3", results)
    val translator = new NRCTranslator {}
    val normalizer = new Finalizer(new BaseNormalizer {})
    val q2 = translator.translate(results.asInstanceOf[translator.Expr])
    val normq1 = normalizer.finalize(q2)
    println(Printer.quote(normq1.asInstanceOf[CExpr]))
    val eval = new BaseScalaInterp {}
    val evaluator = new Finalizer(eval)

    val data = List(
      RecordValue("b" -> "b1", "a" -> List(1,2,3),"d" -> "d1"),
      RecordValue("b" -> "b2", "a" -> List(11,22,33),"d" -> "d2"),
      RecordValue("b" -> "b3", "a" -> List(111,222,333),"d" -> "d3"),
      RecordValue("b" -> "b4", "a" -> List(1111,2222,3333),"d" -> "d4"),
    )

    eval.ctx("R") = data
    println("[TEST_3] input data :" + data)
    val evaluated = evaluator.finalize(normq1.asInstanceOf[CExpr]);
    println("[TEST_3] EVAL:" + evaluated)

    val expected = List(RecordValue("a" ->List(RecordValue("E"->List(1, 2, 3)), RecordValue("E"->List(11, 22, 33)),
        RecordValue("E"->List(111, 222, 333)), RecordValue("E"->List(1111, 2222, 3333)))))
    assert(evaluated==expected)
  }

  /*
4. Output a tuple where one attribute is a value and another is a complex bag that groups on the
  value.
    E = <a' = x.c, b' = For y In R Union (If y.c == x.c Then y.a)>
*/
  test("test4") {
    println()
    println("[TEST_4] Output a tuple where one attribute is a value and another is a complex bag that groups on the\n  value.\n    E = <a' = x.c, b' = For y In R Union (If y.c == x.c Then y.a)>")
    val tuple_x = TupleType("a" -> IntType, "c" -> IntType)
    val tuple_y = TupleType("a" -> IntType, "c" -> IntType)
    val relationR = BagVarRef(VarDef("R", BagType(tuple_x)))
    val xdef = VarDef("x", tuple_x)
    val ydef = VarDef("y", tuple_y)
    val xref = TupleVarRef(xdef)

    val yref = TupleVarRef(ydef)
    val results = Tuple("a" -> xref("c"), "b" -> ForeachUnion(ydef, relationR,
      IfThenElse(
        Cmp(OpEq, yref("c"), xref("c")),
        Singleton(Tuple("b'" -> yref("a")))
      ).asInstanceOf[BagExpr]  ))

    printQuery("TEST_4", results)
    val translator = new NRCTranslator {}
    val normalizer = new Finalizer(new BaseNormalizer {})
    val q2 = translator.translate(results.asInstanceOf[translator.Expr])
    val normq1 = normalizer.finalize(q2)
    println(Printer.quote(normq1.asInstanceOf[CExpr]))
    val eval = new BaseScalaInterp {}
    val evaluator = new Finalizer(eval)

    val data = List(
      RecordValue("a" -> 1, "c" -> 11),
      RecordValue("a" -> 2, "c" -> 22),
      RecordValue("a" -> 3, "c" -> 33),
      RecordValue("a" -> 4, "c" -> 44)
    )

    eval.ctx("R") = data
    eval.ctx("x") = RecordValue("a" -> 4, "c" -> 44)
    println("[TEST_4] input data :" + data)
    val evaluated = evaluator.finalize(normq1.asInstanceOf[CExpr]);
    println("[TEST_4] EVAL:" + evaluated)

    val expected = RecordValue("a"->44,"b"->List(RecordValue("b'"->4)))
    assert(evaluated==expected)
  }

  /*

  5. Take the previous example and enclose it in an iterator so that it contains a bag of complex tuples;
  this will end up performing a group-by.
    E = For x In S Union {a' = x.c, b0 = For y In R Union (If y.c == x.c Then y.a)}

  */
  test("test5") {
    println()
    println("[TEST_5] Take the previous example and enclose it in an iterator so that it contains a bag of complex tuples;\n  this will end up performing a group-by.\n    E = For x In S Union {a' = x.c, b0 = For y In R Union (If y.c == x.c Then y.a)}")

    val tuple_x = TupleType("a" -> IntType, "c" -> IntType)
    val tuple_y = TupleType("a" -> IntType, "c" -> IntType)
    val relationR = BagVarRef(VarDef("R", BagType(tuple_x)))
    val relationS = BagVarRef(VarDef("S", BagType(tuple_x)))
    val xdef = VarDef("x", tuple_x)
    val ydef = VarDef("y", tuple_y)
    val xref = TupleVarRef(xdef)

    val yref = TupleVarRef(ydef)
    val q4 = Singleton(Tuple("a" -> xref("c"), "b" -> ForeachUnion(ydef, relationR,
      IfThenElse(
        Cmp(
          OpEq, yref("c"), xref("c")),
        Singleton(Tuple("asd" -> yref("a")))))))

    val results = ForeachUnion(xdef, relationS, q4.asInstanceOf[BagExpr]);
    printQuery("TEST_5", results)

  }

  /*

  6. Double grouping. Take as input a relation R of type BagType(a : int, b : int, c : int) and group it
    first by attribute a, then by attribute b.
  1
  E =
    For x In R Union
    {a' = x.a, s1 =
      For y In R Union If y.a == x.a Then
        {b' = y.b, s2 =
          For z In R Union If z.a == x.a ∧ z.b == y.b Then
            {c' = z.ci}
        }
    }
*/
  test("test6") {
    println()
    println("[TEST_6]   E =\n    For x In R Union\n    {a' = x.a, s1 =\n      " +
      "For y In R Union If y.a == x.a Then\n        " +
      "{b' = y.b, s2 =\n          " +
      "For z In R Union If z.a == x.a ∧ z.b == y.b Then\n            " +
      "{c' = z.ci}")
    val tuple_x = TupleType("a" -> IntType, "b" -> IntType, "c" -> IntType)
    val relationR = BagVarRef(VarDef("R", BagType(tuple_x)))
    val xdef = VarDef("x", tuple_x)
    val zdef = VarDef("z", tuple_x)
    val ydef = VarDef("y", tuple_x)
    val xref = TupleVarRef(xdef)
    val yref = TupleVarRef(ydef)
    val zref = TupleVarRef(zdef)
    val results = ForeachUnion(xdef, relationR,
      ForeachUnion(ydef, relationR, IfThenElse(Cmp(OpEq, xref("a"), yref("a")),
        //{b' = y.b, s2 =
        //  For z In R Union If z.a == x.a ∧ z.b == y.b Then
        //    {c' = z.ci}
        ForeachUnion(zdef, relationR,
          IfThenElse(
            Or(Cmp(OpEq, zref("a"), xref("a")), Cmp(OpEq, zref("b"), yref("b"))),
            Singleton(Tuple("c'" -> zref("c"))))
        ),
      )
      ))
    printQuery("TEST_6", results)




    val translator = new NRCTranslator {}
    val normalizer = new Finalizer(new BaseNormalizer {})
    val q2 = translator.translate(results.asInstanceOf[translator.Expr])
    val normq1 = normalizer.finalize(q2)
    println(Printer.quote(normq1.asInstanceOf[CExpr]))
    val eval = new BaseScalaInterp {}
    val evaluator = new Finalizer(eval)

    val data = List(
      RecordValue("a" -> 101, "b" -> 102,"c" -> 103),
      RecordValue("a" -> 201, "b" -> 102,"c" -> 203),
      RecordValue("a" -> 301, "b" -> 102,"c" -> 303),
      RecordValue("a" -> 401, "b" -> 102,"c" -> 403)
    )

    eval.ctx("R") = data
    println("[TEST_2] input data :" + data)
    val evaluated = evaluator.finalize(normq1.asInstanceOf[CExpr]);
    println("[TEST_2] EVAL:" + evaluated)
    val expected = List(
      RecordValue("c'" -> 103), RecordValue("c'" -> 203), RecordValue("c'" -> 303), RecordValue("c'" -> 403),
      RecordValue("c'" -> 103), RecordValue("c'" -> 203), RecordValue("c'" -> 303), RecordValue("c'" -> 403),
      RecordValue("c'" -> 103), RecordValue("c'" -> 203), RecordValue("c'" -> 303), RecordValue("c'" -> 403),
      RecordValue("c'" -> 103), RecordValue("c'" -> 203), RecordValue("c'" -> 303), RecordValue("c'" -> 403))
    assert(evaluated==expected)
  }

  /*

    7. A more complex example.
      //iterate over x’s
      For x In R Union
    //Form a new object z that depends on x
    Let Z :=
    For y In x.a Union
      sng(o1 = y.b, o2 = For q In x.c Union If (q.e = y.b) Then (o3 = q.h, o4 = q.j) )
    //use Z to return a bag of tuples, with the first component
    //depending on x, the second being computed from Z
    In
    sng (o5 = x.h, o6 = For w In Z Union sng(o7 = w.o1, o8 = Mult(w.o1, Z)))

*/
  test("test7") {

    println()
    println("[TEST_7]     For x In R Union\n    " +
      "Let Z :=\n    For y In x.a Union\n      " +
      "sng(o1 = y.b, o2 = For q In x.c Union If (q.e = y.b) Then (o3 = q.h, o4 = q.j) )\n    " +
      "In\n    " +
      "sng (o5 = x.h, o6 = For w In Z Union sng(o7 = w.o1, o8 = Mult(w.o1, Z)))")

    // printQuery("TEST_7", results)

  }

  /*

      8. A portion of the alele count example
        Inputs:
        V : Bag(contig: String, start: Int, genotypes: Bag(sample: String, call: Int))
      C: Bag(sample: String, iscase: Double)
      Query:
        For v in V Union
      sng(contig = v.contig, start = v.start, alleleCnts =
        Let G =
          For c in C Union
            sng(iscase = c.iscase, genos = For g in v.genotypes Union
            For c2 in C Union
      if c2.sample = g.sample
      then if c2.iscase = c.iscase
      then sng(call = g.call)) In
      2
      For grp in G Union
      sng(iscase = grp.iscase, cnts = For g in grp.genos Union
      if g = 0 then sng(ref = 2, alt = 0)
      if g = 1 then sng(ref = 1, alt = 1)
      if g = 2 then sng(ref = 0, alt = 2)
      if g = 3 then sng(ref = 0, alt = 0)))
      Output :
        Bag(contig: String, start: Int, alleleCnts: Bag(iscase: Int, cnts: Bag(ref: Int, alt: Int)))
      */
  test("test8") {
    println()
    println("[TEST_8] A portion of the alele count example")
    val results = null
    //printQuery("TEST_8", results)

  }

}