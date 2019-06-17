package shredding.nrc

import shredding.core.VarDef

/**
  * Extension methods for NRC expressions
  */
trait Extensions extends LinearizedNRC {

  def collect[A](e: Expr, f: PartialFunction[Expr, List[A]]): List[A] =
    f.applyOrElse(e, (ex: Expr) => ex match {
      case p: Project => collect(p.tuple, f)
      case ForeachUnion(_, e1, e2) => collect(e1, f) ++ collect(e2, f)
      case Union(e1, e2) => collect(e1, f) ++ collect(e2, f)
      case Singleton(e1) => collect(e1, f)
      case WeightedSingleton(e1, w1) => collect(e1, f) ++ collect(w1, f)
      case Tuple(fs) => fs.flatMap(x => collect(x._2, f)).toList
      case l: Let => collect(l.e1, f) ++ collect(l.e2, f)
      case Total(e1) => collect(e1, f)
      case DeDup(e1) => collect(e1, f)
      case c: Cond => c match {
        case Cmp(_, e1, e2) => collect(e1, f) ++ collect(e2, f)
        case And(e1, e2) => collect(e1, f) ++ collect(e2, f)
        case Or(e1, e2) => collect(e1, f) ++ collect(e2, f)
        case Not(e1) => collect(e1, f)
      }
      case i: IfThenElse =>
        collect(i.cond, f) ++ collect(i.e1, f) ++ i.e2.map(collect(_, f)).getOrElse(Nil)
      case BagExtractLabel(l, e1) =>
        collect(l, f) ++ collect(e1, f)
      case Lookup(l, d) => collect(l, f) ++ collect(d, f)
      case BagDict(l, b, d) => collect(l, f) ++ collect(b, f) ++ collect(d, f)
      case TupleDict(fs) => fs.flatMap(x => collect(x._2, f)).toList
      case BagDictProject(v, _) => collect(v, f)
      case TupleDictProject(d) => collect(d, f)
      case DictUnion(d1, d2) => collect(d1, f) ++ collect(d2, f)
      case Named(_, e1) => collect(e1, f)
      case Sequence(ee) => ee.flatMap(collect(_, f))
      case _ => List()
    })

  def replace(e: Expr, f: PartialFunction[Expr, Expr]): Expr =
    f.applyOrElse(e, (ex: Expr) => ex match {
      case p: Project =>
        val r = replace(p.tuple, f).asInstanceOf[TupleExpr]
        ShredProject(r, p.field)
      case ForeachUnion(x, e1, e2) =>
        val r1 = replace(e1, f).asInstanceOf[BagExpr]
        val xd = VarDef(x.name, r1.tp.tp)
        val r2 = replace(e2, f).asInstanceOf[BagExpr]
        ForeachUnion(xd, r1, r2)
      case Union(e1, e2) =>
        val r1 = replace(e1, f).asInstanceOf[BagExpr]
        val r2 = replace(e2, f).asInstanceOf[BagExpr]
        Union(r1, r2)
      case Singleton(e1) =>
        Singleton(replace(e1, f).asInstanceOf[TupleExpr])
      case WeightedSingleton(e1, w1) =>
        WeightedSingleton(
          replace(e1, f).asInstanceOf[TupleExpr],
          replace(w1, f).asInstanceOf[PrimitiveExpr])
      case Tuple(fs) =>
        Tuple(fs.map(x => x._1 -> replace(x._2, f).asInstanceOf[TupleAttributeExpr]))
      case l: Let =>
        val r1 = replace(l.e1, f)
        val xd = VarDef(l.x.name, r1.tp)
        val r2 = replace(l.e2, f)
        ShredLet(xd, r1, r2)
      case Total(e1) =>
        Total(replace(e1, f).asInstanceOf[BagExpr])
      case DeDup(e1) =>
        DeDup(replace(e1, f).asInstanceOf[BagExpr])
      case c: Cond => c match {
        case Cmp(op, e1, e2) =>
          val c1 = replace(e1, f).asInstanceOf[TupleAttributeExpr]
          val c2 = replace(e2, f).asInstanceOf[TupleAttributeExpr]
          Cmp(op, c1, c2)
        case And(e1, e2) =>
          val c1 = replace(e1, f).asInstanceOf[Cond]
          val c2 = replace(e2, f).asInstanceOf[Cond]
          And(c1, c2)
        case Or(e1, e2) =>
          val c1 = replace(e1, f).asInstanceOf[Cond]
          val c2 = replace(e2, f).asInstanceOf[Cond]
          Or(c1, c2)
        case Not(e1) =>
          Not(replace(e1, f).asInstanceOf[Cond])
      }
      case i: IfThenElse =>
        val c = replace(i.cond, f).asInstanceOf[Cond]
        val r1 = replace(i.e1, f)
        if (i.e2.isDefined)
          ShredIfThenElse(c, r1, replace(i.e2.get, f))
        else
          ShredIfThenElse(c, r1)

      case x: ExtractLabel =>
        val rl = replace(x.lbl, f).asInstanceOf[LabelExpr]
        val re = replace(x.e, f)
        ExtractLabel(rl, re)
      case Lookup(l, d) =>
        val rl = replace(l, f).asInstanceOf[LabelExpr]
        val rd = replace(d, f).asInstanceOf[BagDictExpr]
        Lookup(rl, rd)
      case BagDict(l, b, d) =>
        val rl = replace(l, f).asInstanceOf[LabelExpr]
        val rb = replace(b, f).asInstanceOf[BagExpr]
        val rd = replace(d, f).asInstanceOf[TupleDictExpr]
        BagDict(rl, rb, rd)
      case TupleDict(fs) =>
        TupleDict(fs.map(x => x._1 -> replace(x._2, f).asInstanceOf[TupleDictAttributeExpr]))
      case BagDictProject(v, n) =>
        BagDictProject(replace(v, f).asInstanceOf[TupleDictExpr], n)
      case TupleDictProject(d) =>
        TupleDictProject(replace(d, f).asInstanceOf[BagDictExpr])
      case DictUnion(d1, d2) =>
        val r1 = replace(d1, f).asInstanceOf[DictExpr]
        val r2 = replace(d2, f).asInstanceOf[DictExpr]
        DictUnion(r1, r2)

      case Named(v, e1) => Named(v, replace(e1, f))
      case Sequence(ee) => Sequence(ee.map(replace(_, f)))

      case _ => ex
    })

  def inputVars(e: Expr): Set[VarRef] = inputVars(e, Map.empty).toSet

  protected def inputVars(e: Expr, scope: Map[String, VarDef]): List[VarRef] = collect(e, {
    case v: VarRef => inputVarRef(v, scope)
    case ForeachUnion(x, e1, e2) => inputVars(e1, scope) ++ inputVars(e2, scope + (x.name -> x))
    case l: Let => inputVars(l.e1, scope) ++ inputVars(l.e2, scope + (l.x.name -> l.x))
    case NewLabel(vs) => vs.flatMap(inputVarRef(_, scope)).toList
    case BagDict(l, f, d) =>
      val lblVars = inputVars(l, Map.empty)
      val lblScope = scope ++ lblVars.map(v => v.name -> v.varDef).toMap
      inputVars(f, lblScope) ++ inputVars(d, scope)
    case Named(v, e1) => inputVars(e1, scope + (v.name -> v))
  })

  protected def inputVarRef(v: VarRef, scope: Map[String, VarDef]): List[VarRef] =
    scope.get(v.name).map { v2 =>
      // Sanity check
      assert(v.tp == v2.tp, "Types differ: " + v.tp + " and " + v2.tp)
      Nil
    }.getOrElse(List(v))
}