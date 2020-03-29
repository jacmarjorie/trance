package shredding.generator

import java.io._
import shredding.core._
import shredding.wmcc._
import shredding.examples.Query
import shredding.examples.tpch._

object Utils {

  val normalizer = new Finalizer(new BaseNormalizer{})
  def pathfun(outf: String, sub: String = ""): String = s"experiments/tpch/$sub/test/$outf.scala"
  //val pathout = (outf: String) => s"src/test/scala/shredding/examples/simple/$outf.scala"

  /**
    * Produces an output file for a query pipeline
    * (either shredded or not shredded) the does not do unnesting
    */

  def runCalc(qInfo: (CExpr, String, String), inputM: Map[Type, String], 
              q2Info: (CExpr, String, String) = (EmptySng, "", "")): Unit = {
    val anfBase = new BaseANF {}
    val anfer = new Finalizer(anfBase)

    val (q1, qname, qdata) = qInfo
    
    val normq1 = normalizer.finalize(q1).asInstanceOf[CExpr]
    val inputs = normq1 match {
                   case l @ LinearCSet(_) => inputM ++ l.getTypeMap
                   case _ => inputM ++ Map(normq1.tp.asInstanceOf[BagCType].tp -> s"${qname}Out")
                 }
    val ng = inputM.toList.map(f => f._2)
    val codegen = new ScalaNamedGenerator(inputs)
    
    val anfedq1 = new Finalizer(anfBase).finalize(normq1.asInstanceOf[CExpr])
    val anfExp1 = anfBase.anf(anfedq1.asInstanceOf[anfBase.Rep])
    val gcode = codegen.generate(anfExp1)
    val header = codegen.generateHeader(ng)

    val printer = new PrintWriter(new FileOutputStream(new File(pathfun(qname+"Calc")), false))
    val finalc = write(qname+"Calc", qdata, header, gcode)
    printer.println(finalc)
    printer.close 

    // generate the down stream query
    if (q2Info != (EmptySng, "", "")){
      
      val (q2, q2name, q2data) = q2Info

      val normq2 = normalizer.finalize(q2).asInstanceOf[CExpr]
      anfBase.reset
      val anfedq2 = anfer.finalize(normq2)
      val anfExp2 = anfBase.anf(anfedq2.asInstanceOf[anfBase.Rep])

      val gcode2 = codegen.generate(anfExp2)
      val header2 = codegen.generateHeader(ng)

      val printer2 = new PrintWriter(new FileOutputStream(new File(pathfun(q2name+"Calc")), false))
      val finalc2 = write2(q2name+"Calc", qdata, header2, gcode, q2name, gcode2, q2data)
      printer2.println(finalc2)
      printer2.close 

    }

  }

  /**
    * Produces an output file for a query pipeline 
    * (either shredded or not shredded) that does unnesting
    */
 
  def run(qInfo: (CExpr, String, String), inputM: Map[Type, String], 
          q2Info: (CExpr, String, String) = (EmptySng, "", "")): Unit = {
    val anfBase = new BaseANF {}
    val anfer = new Finalizer(anfBase)

    val (q1, qname, qdata) = qInfo
    
    val normq1 = normalizer.finalize(q1).asInstanceOf[CExpr]
    val inputs = normq1 match {
                  case l @ LinearCSet(_) => inputM ++ l.getTypeMap
                  case _ => inputM ++ Map(normq1.tp.asInstanceOf[BagCType].tp -> s"${qname}Out")
                 }
    val ng = inputM.toList.map(f => f._2)
    val codegen = new ScalaNamedGenerator(inputs)
    
    val plan1 = Unnester.unnest(normq1)(Nil, Nil, None).asInstanceOf[CExpr]
    println(Printer.quote(plan1))
    val anfedq1 = anfer.finalize(plan1)
    val anfExp1 = anfBase.anf(anfedq1.asInstanceOf[anfBase.Rep])
    println(Printer.quote(anfExp1))
    val gcode = codegen.generate(anfExp1)
    val header = codegen.generateHeader(ng)

    val printer = new PrintWriter(new FileOutputStream(new File(pathfun(qname)), false))
    val finalc = write(qname, qdata, header, gcode)
    printer.println(finalc)
    printer.close 

    // generate the down stream query
    if (q2Info != (EmptySng, "", "")){
      val (q2, q2name, q2data) = q2Info

      val normq2 = normalizer.finalize(q2).asInstanceOf[CExpr]
      println(Printer.quote(normq2))
      val plan2 = Unnester.unnest(normq2)(Nil, Nil, None)
      println(Printer.quote(plan2))
      anfBase.reset
      val anfedq2 = anfer.finalize(plan2)
      val anfExp2 = anfBase.anf(anfedq2.asInstanceOf[anfBase.Rep])

      val gcode2 = codegen.generate(anfExp2)
      val header2 = codegen.generateHeader(ng)

      val printer2 = new PrintWriter(new FileOutputStream(new File(pathfun(q2name)), false))
      val finalc2 = write2(q2name, qdata, header2, gcode, q2name, gcode2, q2data)
      printer2.println(finalc2)
      printer2.close 
    }

  }
 
   /**
    * Produces an ouptut spark application 
    * (either shredded or not shredded) that does unnesting
    */
  def timeOp(appname: String, e: String, i: Int = 0): String = {
    val query = if (i > 0) "unshredding" else "query"
    s"""
      |var start$i = System.currentTimeMillis()
      |$e
      |var end$i = System.currentTimeMillis() - start$i
      |println("$appname,"+sf+","+Config.datapath+","+end$i+",$query,"+spark.sparkContext.applicationId)
    """.stripMargin
  }

  def timed(appname: String, e: List[String]): String =
    s"""| def f = {
        | ${e.zipWithIndex.map{ case (e1,i) => timeOp(appname, e1, i) }.mkString("\n")}
        |}
        |var start = System.currentTimeMillis()
        |f
        |var end = System.currentTimeMillis() - start
    """.stripMargin
   
  def timed(e: String): String = 
    s"""|def f = { 
        | $e
        |}
        |var start = System.currentTimeMillis()
        |f
        |var end = System.currentTimeMillis() - start """.stripMargin

  
  def flat(query: Query, path: String, label: String): Unit =
    runSpark(query, path, label, 0, false)

  def flatInput(input: Query, query: Query, path: String, label: String): Unit =
    runSparkInput(input, query, path, label, 0, false)

  def flatProj(query: Query, path: String, label: String): Unit =
    runSpark(query, path, label, 1, false)

  def flatProjInput(input: Query, query: Query, path: String, label: String): Unit =
    runSparkInput(input, query, path, label, 1, false)

  def flatOpt(query: Query, path: String, label: String, skew: Boolean = false): Unit =
    runSpark(query, path, label, 2, skew)

  def flatOptInput(input: Query, query: Query, path: String, label: String, skew: Boolean = false): Unit =
    runSparkInput(input, query, path, label, 2, skew)

  def shred(query: Query, path: String, label: String, eliminateDomains: Boolean = true, 
    unshred: Boolean = false, skew: Boolean = false): Unit =
      runSparkShred(query, path, label, eliminateDomains, unshred, skew)

  def shredInput(input: Query, query: Query, path: String, label: String, 
    eliminateDomains: Boolean = true, unshred: Boolean = false, skew: Boolean = false): Unit =
    runSparkInputShred(input, query, path, label, eliminateDomains, unshred, skew)

  def runSpark(query: Query, pathout: String, label: String, 
    optLevel: Int = 2, skew: Boolean = false): Unit = {
    
    val codegen = new SparkNamedGenerator(false, true, flatDict = true)//query.inputTypes(shred))
    val gcode = codegen.generate(query.anf(optLevel))
    val header = if (skew) {
        s"""|import sprkloader.SkewPairRDD._
            |import sprkloader.SkewTopRDD._
            |${codegen.generateHeader(query.headerTypes(false))}""".stripMargin
      } else {
        s"""|import sprkloader.PairRDDOperations._
            |import sprkloader.TopRDD._
            |${codegen.generateHeader(query.headerTypes(false))}""".stripMargin
      }
   
    val flatTag = optLevel match {
      case 0 => "None"
      case 1 => "Proj"
      case _ => ""
    }
    val qname = if (skew) s"${query.name}${flatTag}SkewSpark" else s"${query.name}${flatTag}Spark"
    val fname = s"$pathout/$qname.scala" 
    println(s"Writing out $qname to $fname")
    val printer = new PrintWriter(new FileOutputStream(new File(fname), false))
    val inputs = if (skew) query.inputs(TPCHSchema.skewcmds) else query.inputs(TPCHSchema.tblcmds)
    val finalc = writeSpark(qname, inputs, header, timed(gcode), label)
    printer.println(finalc)
    printer.close 
  
  }

  def runSparkInput(inputQuery: Query, query: Query, pathout: String, label: String, 
    optLevel: Int = 2, skew: Boolean = false): Unit = {
    
    val codegenInput = new SparkNamedGenerator(true, true, flatDict = true)
    val inputCode = codegenInput.generate(inputQuery.anf()) 
    val codegen = new SparkNamedGenerator(false, true, flatDict = true, inputs = codegenInput.types) 
    val gcode = codegen.generate(query.anf(optLevel))
    val header = if (skew) {
        s"""|import sprkloader.SkewPairRDD._
            |import sprkloader.SkewTopRDD._
            |${codegen.generateHeader(query.headerTypes(false))}""".stripMargin
      } else {
        s"""|import sprkloader.PairRDDOperations._
            |import sprkloader.TopRDD._
            |${codegen.generateHeader(query.headerTypes(false))}""".stripMargin
      }
    val flatTag = optLevel match {
      case 0 => "None"
      case 1 => "Proj"
      case _ => ""
    }
    val qname = if (skew) s"${query.name}${flatTag}SkewSpark" else s"${query.name}${flatTag}Spark"
    val fname = s"$pathout/$qname.scala"
    println(s"Writing out $qname to $fname")
    val printer = new PrintWriter(new FileOutputStream(new File(fname), false))
    val finalc = writeSpark(qname, query.inputs(TPCHSchema.tblcmds), 
        header, s"$inputCode\n${timed(gcode)}", label)
    printer.println(finalc)
    printer.close 
  
  }

  def runSparkShred(query: Query, pathout: String, label: String, eliminateDomains: Boolean = true, 
    unshred: Boolean = false, skew: Boolean = false): Unit = {
    
    val codegen = new SparkNamedGenerator(unshred, eliminateDomains, flatDict = true)
    val (gcodeShred, gcodeUnshred) = query.shredPlan(unshred, eliminateDomains = eliminateDomains)
    val gcode1 = codegen.generate(gcodeShred)
    val gcodeSet = if (unshred) List(gcode1, codegen.generate(gcodeUnshred)) else List(gcode1)
    val header = if (skew) {
        s"""|import sprkloader.SkewPairRDD._
            |import sprkloader.SkewDictRDD._
            |import sprkloader.SkewTopRDD._
            |${codegen.generateHeader(query.headerTypes(true))}""".stripMargin
      } else {
        s"""|import sprkloader.PairRDDOperations._
            |import sprkloader.DictRDDOperations._
            |import sprkloader.TopRDD._
            |${codegen.generateHeader(query.headerTypes(true))}""".stripMargin
      }
   
    val qname = if (skew) s"Shred${query.name}SparkSkew" else s"Shred${query.name}Spark"
    val fname = if (unshred) s"$pathout/unshred/$qname.scala" else s"$pathout/$qname.scala"
    println(s"Writing out $qname to $fname")
    val printer = new PrintWriter(new FileOutputStream(new File(fname), false))
    val inputs = if (skew) query.inputs(TPCHSchema.sskewcmds) else query.inputs(TPCHSchema.stblcmds)
    val finalc = writeSpark(qname, inputs, header, timed(label, gcodeSet), label)
    printer.println(finalc)
    printer.close 
  
  }

  def runSparkInputShred(inputQuery: Query, query: Query, pathout: String, label: String, 
    eliminateDomains: Boolean = true, unshred: Boolean = false, skew: Boolean = false): Unit = {
    
    val codegenInput = new SparkNamedGenerator(false, false, flatDict = true)
    val (inputShred, queryShred, queryUnshred) = query.shredWithInput(inputQuery, unshredRun = unshred, eliminateDomains = eliminateDomains)
    val inputCode = codegenInput.generate(inputShred)
    val codegen = new SparkNamedGenerator(false, eliminateDomains, flatDict = true, inputs = codegenInput.types)
    val gcode1 = codegen.generate(queryShred)
    val gcodeSet = if (unshred) List(gcode1, codegen.generate(queryUnshred)) else List(gcode1)
    val header = if (skew) {
        s"""|import sprkloader.SkewPairRDD._
            |import sprkloader.SkewDictRDD._
            |import sprkloader.SkewTopRDD._
            |${codegen.generateHeader(query.headerTypes(true))}""".stripMargin
      } else {
        s"""|import sprkloader.PairRDDOperations._
            |import sprkloader.DictRDDOperations._
            |import sprkloader.TopRDD._
            |${codegen.generateHeader(query.headerTypes(true))}""".stripMargin
      }
   
    val domains = if (eliminateDomains) "" else "Domains"
    val qname = if (skew) s"Shred${query.name}${domains}SparkSkew" else s"Shred${query.name}${domains}Spark"
    val fname = if (unshred) s"$pathout/unshred/$qname.scala" else s"$pathout/$qname.scala"
    println(s"Writing out $qname to $fname")
    val printer = new PrintWriter(new FileOutputStream(new File(fname), false))
    val inputSection = s"${inputCode}\n${shredInputs(inputQuery.indexedDict)}"
    val inputs = if (skew) query.inputs(TPCHSchema.sskewcmds) else query.inputs(TPCHSchema.stblcmds)
    val finalc = writeSpark(qname, inputs, header, s"$inputSection\n${timed(label, gcodeSet)}", label)
    printer.println(finalc)
    printer.close 
  
  }



  def inputs(n: String, e: String): String = {
    s"""|val $n = {
        | $e
        |}
        |$n.cache
        |$n.evaluate""".stripMargin
  }
 
  def shredInputs(ns: List[String]): String = { 
    var cnt = 0
    ns.map{ n => 
      val (inputTag, outputTag) = if (cnt == 0) ("MBag", "IBag") else ("MDict", "IDict")
      val iname = n.replace("__D", "")
      val oname = n.replace("_1", "")
      cnt += 1
      s"""|val ${outputTag}_$oname = ${inputTag}_$iname
          |${outputTag}_$oname.cache
          |${outputTag}_$oname.evaluate"""
    }.mkString("\n").stripMargin
  }

  /**
    * Writes out a query for a Spark application
    **/

  def writeSpark(appname: String, data: String, header: String, gcode: String, label:String): String  = {
    s"""
      |package experiments
      |/** Generated **/
      |import org.apache.spark.SparkConf
      |import org.apache.spark.sql.SparkSession
      |import scala.collection.mutable.HashMap
      |import sprkloader._
      |$header
      |object $appname {
      | def main(args: Array[String]){
      |   val sf = Config.datapath.split("/").last
      |   val conf = new SparkConf().setMaster(Config.master).setAppName(\"$appname\"+sf)
      |   val spark = SparkSession.builder().config(conf).getOrCreate()
      |   $data
      |   $gcode
      |   println("$label,"+sf+","+Config.datapath+","+end+",total,"+spark.sparkContext.applicationId)
      | }
      |}""".stripMargin
  }

  /**
    * Writes out a query that has inputs provided from the context (h)
    */

  def write(n: String, i: String, h: String, q: String): String = {
    s"""
      |package experiments
      |/** Generated code **/
      |import shredding.core.CaseClassRecord
      |import shredding.examples.tpch._
      |    $h
      |object $n {
      | def main(args: Array[String]){
      |    var start0 = System.currentTimeMillis()
      |    var id = 0L
      |    def newId: Long = {
      |      val prevId = id
      |      id += 1
      |      prevId
      |    }
      |    $i
      |    var end0 = System.currentTimeMillis() - start0
      |    def f(){
      |      $q
      |    }
      |    var time = List[Long]()
      |    for (i <- 1 to 5) {
      |      var start = System.currentTimeMillis()
      |      f
      |      var end = System.currentTimeMillis() - start
      |      time = time :+ end
      |    }
      |    val avg = (time.sum/5)
      |    println(end0+","+avg)
      | }
      |}""".stripMargin
  }

  /**
    * Writes out a query that takes another query as input
    */
  
  def write2(n: String, i1: String, h: String, q1: String, i2: String, q2: String, ef: String = ""): String = {
    s"""
      |package experiments
      |/** Generated code **/
      |import shredding.core.CaseClassRecord
      |import shredding.examples.tpch._
      |$h
      |object $n {
      | def main(args: Array[String]){
      |    var start0 = System.currentTimeMillis()
      |    var id = 0L
      |    def newId: Long = {
      |      val prevId = id
      |      id += 1
      |      prevId
      |    }
      |    $i1
      |    val $i2 = { $q1 }
      |    var end0 = System.currentTimeMillis() - start0
      |    $ef
      |    def f(){
      |      $q2
      |    }
      |    var time = List[Long]()
      |    for (i <- 1 to 5) {
      |     var start = System.currentTimeMillis()
      |      f
      |      var end = System.currentTimeMillis() - start
      |      time = time :+ end
      |    }
      |    val avg = (time.sum/5)
      |    println(end0+","+avg)
      | }
      |}""".stripMargin
  }

}
