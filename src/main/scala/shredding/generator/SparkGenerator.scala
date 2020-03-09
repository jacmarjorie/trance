package shredding.generator

import shredding.core._
import shredding.wmcc.{Multiply => CMultiply}
import shredding.wmcc._
import shredding.utils.Utils.ind

/**
  * Generates Scala code specific to Spark applications
  */

class SparkNamedGenerator(inputs: Map[Type, String] = Map()) extends SparkTypeHandler with SparkUtils {

  implicit def expToString(e: CExpr): String = generate(e)


  var types: Map[Type, String] = inputs

  def generateHeader(names: List[String] = List()): String = {
    val h1 = typelst.map(x => generateTypeDef(x)).mkString("\n")
    val h2 = inputs.withFilter(x => !names.contains(x._2)).map( x => generateTypeDef(x._1)).toList
    if (h2.nonEmpty) { s"$h1\n${h2.mkString("\n")}" } else { h1 }
  }

  def conditional(p: CExpr, thenp: String, elsep: String): String = p match {
    case Constant(true) => s"${ind(thenp)}"
    case _ => s"if({${generate(p)}}) {${ind(thenp)}} else {${ind(elsep)}}"
  }

  def nullProject(e: CExpr): String = e match {
    case Project(e1, field) => 
      s"${generate(e1)} match { case null => ${castNull(e)}; case pv => pv.${kvName(field)} }"
    case Bind(bv, p1 @ Project(v, field), Bind(bv2, p2 @ Record(_), p3)) =>
      s"""|{val ${generate(bv2)} = ${generate(v)} match {
          |   case null => null; case _ => {
          |     val ${generate(bv)} = ${generate(v)}.$field
          |     ${generate(p2)} }}
          |${generate(p3)}}""".stripMargin
    case _ => generate(e)
  }

  def generate(e: CExpr): String = e match {

    /** ZEROS **/
    case Null => "null"
    case CUnit => "()"
    case EmptySng => "Iterable()"
    case EmptyCDict => s"()"
    
    /** BASIC CONSTRUCTS **/
    case Variable(name, _) => name
    case InputRef(name, tp) => name
    case Constant(s:String) => "\"" + s + "\""
    case Constant(x) => x.toString
    case Sng(e) => s"Iterable(${generate(e)})"
    case Record(fs) if isDictRecord(e) => 
      s"(${fs.map(f => { handleType(f._2.tp); generate(f._2) } ).mkString(", ") })"
    case Record(fs) => {
      val tp = e.tp
      handleType(tp)
      s"${generateType(tp)}(${fs.map(f => generate(f._2)).mkString(", ")})"
    }
    case Tuple(fs) => s"(${fs.map(f => generate(f)).mkString(",")})"
    case Project(e1, "lbl") => generate(e1)
    case Project(e2 @ Record(fs), field) => 
      s"${generate(e2)}.${kvName(field)(fs.size)}"
    case Project(e2, field) => s"${generate(e2)}.${kvName(field)}"
    /** MATH OPS **/
    case CMultiply(e1, e2) => s"${generate(e1)} * ${generate(e2)}"

    /** BOOL OPS **/
    case Equals(e1, e2) => s"${generate(e1)} == ${generate(e2)}"
    case Lt(e1, e2) => s"${generate(e1)} < ${generate(e2)}"
    case Gt(e1, e2) => s"${generate(e1)} > ${generate(e2)}"
    case Lte(e1, e2) => s"${generate(e1)} <= ${generate(e2)}"
    case Gte(e1, e2) => s"${generate(e1)} >= ${generate(e2)}"
    case And(e1, e2) => s"${generate(e1)} && ${generate(e2)}"
    case Or(e1, e2) => s"${generate(e1)} || ${generate(e2)}"
    case Not(e1) => s"!(${generate(e1)})"
    case If(cond, e1, Some(e2)) => conditional(cond, e1, e2)
    case If(cond, e1, None) => conditional(cond, e1, zero(e1))

    /** BAG UNION **/
    case Merge(e1, e2) => s"${generate(e1) ++ generate(e2)}"

    /** DEDUPLICATION **/
    case CDeDup(e1) => s"${generate(e1)}.distinct"
   
    /** DOMAIN CREATION **/
    case Reduce(e1, v, f @ Bind(_, Project(_, labelField), _), Constant(true)) if isDomain(e) =>  
      s"${generate(e1)}.createDomain(l => l.$labelField)"

    /** IDENTITY **/
    case Reduce(InputRef(n, _), v, Variable(_,_), Constant(true)) => n
    case Reduce(Variable(n, _), v, Variable(_,_), Constant(true)) => n

    /** PROJECT **/
    case Reduce(e1, v, f, Constant(true)) => 
      val vars = generateVars(v, e1.tp)
      s"""|${generate(e1)}.map{ case $vars => 
          |   ${generate(f)}
          |}""".stripMargin

    case Reduce(e1, v, f, Constant("null")) => 
      val vars = generateVars(v, e1.tp)
      s"""|${generate(e1)}.map{ case $vars => 
          |   ${nullProject(f)}
          |}""".stripMargin

    /** SELECT **/
    case Reduce(e1, v, f, p) => 
      val vars = generateVars(v, e1.tp)
      s"""|${generate(e1)}.map{ case $vars => 
          |   ${generate(f)}
          |}.filter($vars => {${generate(p)}})""".stripMargin
    // catch all
    case Select(x, v, p, e2) => generate(Reduce(x, List(v), e2, p))
    
    /** UNNEST **/
    case Unnest(e1, v1, f, v2, p, value) => 
      val vars = e1.tp match {
        case _:BagDictCType => generateVars(v1, e1.tp)+"._1"
        case _ => generateVars(v1, e1.tp)
      }
      val gv2 = generate(v2)
      val (v, attr, vs1, vs2) = projectBag(f, v1)
      val nvars = generateVars(vs1, e1.tp)
      p match {
        case Constant(true) =>
          s"""|${generate(e1)}.flatMap{ case $vars => 
              | $v.$attr.map{ $gv2 => ($nvars, ${generate(value)})}}""".stripMargin
        case _ => 
          s"""|${generate(e1)}.flatMap{ case $vars => 
              | $v.$attr.map{ $gv2 => ($nvars, ${generate(value)})}
              |}.filter{ case ($vars, $gv2) => {${generate(p)}} }""".stripMargin
      }
    // TODO
    case OuterUnnest(e1, v1, f, v2, p, value) => 
      val vars = generateVars(v1, e1.tp)
      val gv2 = generate(v2)
      val (v, attr, vs1, vs2) = projectBag(f, v1)
      val nvars = generateVars(vs1, e1.tp)
      val nullvars = if (v1.size == 1) "" 
        else s"case null => Iterable((${generateVars(vs2, e1.tp)}, null))"
      p match {
        case Constant(true) =>
          s"""|${generate(e1)}.flatMap{ case $vars => 
              |  $v match { $nullvars
              |    case _ => if ($v.$attr.isEmpty) Iterable(($nvars, null))
              |     else $v.$attr.map{ $gv2 => ($nvars, ${generate(value)})}
              |}}""".stripMargin
        case _ => 
          s"""|${generate(e1)}.flatMap{ case $vars => 
              | {${generate(f)}}.map{ $gv2 => ($nvars, ${generate(value)})}
              |}.filter{ case ($vars, $gv2) => {${generate(p)}} }""".stripMargin
      }

    /** JOIN **/
    case Bind(jv, Join(e1, e2, v1, p1, v2, p2), e3) => 
      val vars = generateVars(v1, e1.tp)
      val gv2 = generate(v2)
      val ve1 = "x" + Variable.newId()
      val ve2 = "x" + Variable.newId()
      (p1, p2) match {
        case (Constant(true), Constant(true)) =>
          s"val ${generate(jv)} = ${generate(e1)}.cartesian(${generate(e2)})\n${generate(e3)}"
        case _ => 
       s"""|val $ve1 = ${generate(e1)}.map{ case $vars => ({${generate(p1)}}, $vars) }
           |val $ve2 = ${generate(e2)}.map{ case $gv2 => ({${generate(p2)}}, $gv2) }
           |val ${generate(jv)} = $ve1.joinDropKey($ve2)
           |${generate(e3)}
           |""".stripMargin
      }

    /** LEFT OUTER JOIN **/
    case Bind(jv, OuterJoin(e1, e2, v1, p1, v2, p2), e3) => 
      val vars = generateVars(v1, e1.tp)
      val gv2 = generate(v2)
      val ve1 = "x" + Variable.newId()
      val ve2 = "x" + Variable.newId()
      // avoid null pointer exceptions
      val gp1 = p1 match {
        case Bind(_, proj @ Project(v, field), _) => 
          if (v1.size > 1) nullProject(proj)
          else generate(p1)
        case _ => sys.error(s"unsupported $p1")
      }
      s"""|val $ve1 = ${generate(e1)}.map{ case $vars => ({$gp1}, $vars) }
          |val $ve2 = ${generate(e2)}.map{ case $gv2 => ({${generate(p2)}}, $gv2) }
          |val ${generate(jv)} = $ve1.leftOuterJoin($ve2).map{ case (k, (a, Some(v))) => 
          |    (a, v); case (k, (a, None)) => (a, null) }
          |${generate(e3)}
          |""".stripMargin

    /** NEST - Dictionary Specific **/
    // Nest for dictionaries, use groupByKey to remove lbl -> {} pairs
    case Nest(e1, v1, f, e2, v2, p, g) if hasLabel(f.tp) =>
      val vars = generateVars(v1, e1.tp)
      val agg = e2.tp match {
        case IntType => "reduceByKey(_+_)"
        case DoubleType => "reduceByKey(_+_)"
        case _ => "groupByKey()"
      } 
      val acc = "acc"+Variable.newId
      s"""|${generate(e1)}.map{ case $vars => 
          |   ({${generate(f)}}, {${generate(e2)}})
          | }.$agg""".stripMargin

    /** NEST **/
    // TODO add filter
    case Nest(e1, v1, f, e2, v2, Constant(true), g) =>
      val vars = generateVars(v1, e1.tp)
      val acc = "acc"+Variable.newId
      val emptyType = empty(e2)
      val aggType = agg(e2)
      val gv2 = generate(v2)
      val key = f match {
        case Bind(bv, Project(v, field), _) => nullProject(f)
        case _ => s"{${generate(f)}}"
      }
      //s"{${generate(f)}}"
      val value = if (!emptyType.contains("0")) s"Iterable({${generate(e2)}})" else s"{${generate(e2)}}"
      val nonet = g match {
        case Bind(_, Tuple(fs), _) if fs.size > 1 => 
          ((2 to fs.size).map(i => 
            if (i != fs.size) {
              s"case (${fs.slice(1, i).map(e => "_").mkString(",")},${zero(fs(i-1))},${fs.slice(i, fs.size).map(e => "_").mkString(",")}) => ($key, $emptyType)" //({${generate(f)}}, ${zero(e2)})"
            } else { 
              s"case (${fs.slice(1, i).map(e => "_").mkString(",")},${zero(fs.last)}) => ($key, $emptyType)" //({${generate(f)}}, ${zero(e2)})" 
            }
          ) :+ s"case (null, ${(2 to fs.size).map(i => "_").mkString(",")}) => ($key, $emptyType)").mkString("\n")
        case _ => s"case (null) => ($key, $emptyType)" //({${generate(f)}}, ${zero(e2)})"
      }
      s"""|${generate(e1)}.map{ case $vars => {${generate(g)}} match {
          |   $nonet
          |   case $gv2 => ({${generate(f)}}, $value)
          | }
          |}.reduceByKey(_ $aggType _)""".stripMargin
    
    /** LOOKUPS **/

    // LOOKUP ITERATOR (unshredding)
    case Bind(unv, Unnest(dict1, v1, bag, v2, filt, value), 
      Bind(luv, Lookup(_, dict2, _, Bind(_, Project(_, key1), _), v3, key2, key3), e2)) =>
      val vars = generateVars(v1, dict1.tp)
      val undict1 = generate(unv)
      val gv2 = generate(v2)
      val gluv = generate(luv)
      val nv2 = v2.tp match {
        case RecordCType(ms) => generate(Record((ms - key1).map(f => f._1 -> Project(v2, f._1))))
        case _ => ???
      }
      val flatten = dict2 match { case InputRef(n, _) if !n.contains("new") => ".flatten"; case _ => "" }
      s"""|val $undict1 = ${generate(dict1)}.flatMap{
          | case $vars => {${generate(bag)}}.map{case $gv2 => 
          |   ($gv2.$key1, ($vars._1, $nv2))}
          |}
          |val $gluv = $undict1.cogroup(${generate(dict2)}).flatMap{
          | case (_, (dict1, dict2)) => dict1.map{ case (lbl, d) => lbl -> (d, dict2$flatten) }
          |}
          |${generate(e2)}
          |""".stripMargin
    
    // DOMAIN LOOKUP ITERATOR 
    case Bind(luv, Lookup(e1, e2, vs, k1, v2, k2, Constant(true)), e3) if isDomain(e1) =>
      val ve1 = "x" + Variable.newId()
      val ve2 = "x" + Variable.newId()
      val vars = generateVars(vs, e1.tp.asInstanceOf[BagCType].tp)
      val domain = e1.tp match {
        case BagCType(RecordCType(ms)) if ms.size == 1 => generate(e1)
        case _ => s"${generate(e1)}.map{ case $vars => ({${generate(k1)}}, $vars)}"
      }
      val tp = e1.tp.asInstanceOf[BagCType].tp.asInstanceOf[RecordCType].attrTps("lbl")
      handleType(tp)
      val label = generateType(tp)
      val e1key = k2 match {
        case Constant(true) => s"$label(lbl)"
        case _ => s"$label({${generate(k2)}})"
      }
      val gv2 = e2.tp match {
        case BagCType(RecordCType(_)) => s"${generate(v2)} => ($e1key, ${generate(v2)})"
        case _ => s"(lbl, bag) => ($e1key, bag)"
      }
      s"""| val $ve1 = $domain
          | val $ve2 = ${generate(e2)}.map{ case $gv2 }
          | val ${generate(luv)} = $ve2.lookupIteratorDomain($ve1)
          | ${generate(e3)}
        """.stripMargin

    // STANDARD LOOKUP
    case Bind(luv, Lookup(e1, e2, v1, p1 @ Bind(_, Project(v3, key1), _), v2, p2, Variable(_,_)), e3) =>
      val vars = generateVars(v1, e1.tp.asInstanceOf[BagCType].tp)
      val gv2 = generate(v2)
      val gluv = generate(luv)
      val ve1 = "x" + Variable.newId()
      val flatten = e2 match { case InputRef(n, _) if !n.contains("new") => ".flatten"; case _ => "" }
      // move this to the implementation of lookup
      val nv2 = v3.tp match {
        case RecordCType(ms) => generate(Record((ms - key1).map(f => f._1 -> Project(v3, f._1))))
        case _ => ???
      }
      s"""|val $ve1 = ${generate(e1)}.map{ case $vars => (${generate(v3)}.$key1, $nv2)}
          |val $gluv = $ve1.cogroup(${generate(e2)}).flatMap{
          | case (_, (dict1, dict2)) => dict1.map{ case $vars => ($vars, dict2$flatten) }}
          |${generate(e3)}
          |""".stripMargin 

    /** COMBINE JOIN / NEST OPERATORS **/

    // MERGE JOIN DOMAIN + GROUP BY KEY; LOOKUP DOMAIN
    case Bind(cv, CoGroup(e1, e2, vs, v2, k1, k2, value), e3) if isDomain(e1) =>
      val ve1 = "x" + Variable.newId()
      val ve2 = "x" + Variable.newId()
      val vars = generateVars(vs, e1.tp.asInstanceOf[BagCType].tp)
      val domain = e1.tp match {
        case BagCType(RecordCType(ms)) if ms.size == 1 => generate(e1)
        case _ => s"${generate(e1)}.map{ case $vars => ({${generate(k1)}}, $vars)}"
      }
      val tp = e1.tp.asInstanceOf[BagCType].tp.asInstanceOf[RecordCType].attrTps("lbl")
      handleType(tp)
      val label = generateType(tp)
      val e1key = k2 match {
        case Constant(true) => s"$label(lbl)"
        case _ => s"$label({${generate(k2)}})"
      }
      val (gv2, funct) = e2.tp match {
        case BagCType(RecordCType(_)) => (s"${generate(v2)} => ($e1key, {${generate(value)}})", "cogroupDomain")
        case _ => (s"(lbl, bag) => ($e1key, bag.map(${generate(v2)} => {${generate(value)}}))", "lookup")
      }
      s"""| val $ve1 = $domain
          | val $ve2 = ${generate(e2)}.map{ case $gv2 }
          | val ${generate(cv)} = $ve2.$funct($ve1)
          | ${generate(e3)}
        """.stripMargin

    // MERGE JOIN + GROUP BY
    case Bind(cv, CoGroup(e1, e2, vs, v2, k1, k2, value), e3) =>
      val ve1 = "x" + Variable.newId()
      val ve2 = "x" + Variable.newId()
      val vars = generateVars(vs, e1.tp.asInstanceOf[BagCType].tp)
      val gv2 = generate(v2)
      s"""| val $ve1 = ${generate(e1)}.map{ case $vars => ({${generate(k1)}}, $vars)}
          | val $ve2 = ${generate(e2)}.map{ case $gv2 => ({${generate(k2)}}, {${generate(value)}})}
          | val ${generate(cv)} = $ve1.cogroup($ve2).flatMap{
          |  case (_, (e1, e2)) => e1.map(v => v -> e2)
          |}
          | ${generate(e3)}
        """.stripMargin

    /** LOCAL COMPREHENSION **/

    case Comprehension(e1, v, p, e) =>
      val acc = Variable.fresh("acc")
      val cur = generate(v)
      e.tp match {
        case IntType =>
          s"${generate(e1)}.foldLeft(0)(($acc, $cur) => \n${ind(conditional(p, s"$acc + {${generate(e)}}", s"$acc"))})"
        case DoubleType =>
          s"${generate(e1)}.foldLeft(0.0)(($acc, $cur) => \n${ind(conditional(p, s"$acc + {${generate(e)}}", s"$acc"))})"
        case _ =>
          s"${generate(e1)}.flatMap($cur => { \n${ind(conditional(p, s"${generate(e)}", "Nil"))}})"
      }

    /** SEQUENCE OF PLAN HANDLING **/

    case Bind(x, CNamed(n, e1), Bind(_, LinearCSet(_), _)) => 
      s"""|val $n = ${generate(e1)}
          |val ${generate(x)} = $n
          |${runJob(n, true)}""".stripMargin
    case Bind(x, CNamed(n, e1), e2) =>
    	s"""|val $n = ${generate(e1)}
          |val ${generate(x)} = $n
          |${runJob(n)}
          |${generate(e2)}""".stripMargin

    /** ANF BASE CASE **/
    case Bind(v, e1, e2) => 
      s"val ${generate(v)} = ${generate(e1)} \n${generate(e2)}"

    case _ => sys.error(s"not supported $e \n ${Printer.quote(e)}")
  }

  /* Tuple vars based on type, for example (a,b,c) -> ((a,b),c) */
  def generateVars(e: List[CExpr], tp: Type): String = tp match {
    case BagDictCType(BagCType(t), d) => generateVars(e, t)
    case BagCType(t) => generateVars(e, t)
    case TTupleType(seq) if (seq.size == 2 && validLabel(seq.head)) => s"${generate(e.head)}"
    case TTupleType(seq) if e.size == seq.size => e.map(generate).mkString("(", ", ", ")")
    case TTupleType(seq) if e.size > seq.size => {
      val en = e.dropRight(seq.size - 1)
      val rest = e.takeRight(seq.size - 1).map(generate).mkString(", ")
      s"(${generateVars(en, seq.head)}, $rest)"
    }
    case RecordCType(_) => s"${generate(e.head)}"
    case y if e.size == 1 => s"${generate(e.head)}" 
    //TTupleType(seq) if seq.size == 2 && e.size == 1 => e.map(generate).mkString("(", ",", ")")
    case TTupleType(seq) => sys.error(s"not supported ${e.size} ${seq.size} --> $e:\n ")//${generateType(tp)}")
    //case _ if e.size == 1 => s"${generate(e.head)}"
  }

  def e1Key(p1: CExpr, p3: CExpr) =  p3 match {
    case Constant(true) => s"{${generate(p1)}}"
    case _ => s"({${generate(p1)}}, {${generate(p3)}})"
  }

  def e2Key(v2: CExpr, p2: CExpr) = {
    val gv2 = generate(v2)
    p2 match {
      case Constant(true) => s".flatMapValues(identity)"
      case _ => ???
      // s"""/** WHEN DOES THIS CASE HAPPEN **/
      //   .flatMap(v2 => v2._2.map{case $gv2 => ((v2._1, {${generate(p2)}}), $gv2)})"""
    }
  }


}
