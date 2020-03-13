package shredding.nrc

import shredding.core.BagType

trait MaterializeNRC extends ShredNRC {

  val KEY_ATTR_NAME: String = "_1"

  val VALUE_ATTR_NAME: String = "_2"

  val LABEL_ATTR_NAME: String = "lbl"

  final case class MatDictLookup(lbl: LabelExpr, bag: BagExpr) extends BagExpr {
    assert(bag.tp.tp(KEY_ATTR_NAME) == lbl.tp && bag.tp.tp(VALUE_ATTR_NAME).isInstanceOf[BagType])

    def tp: BagType = bag.tp.tp(VALUE_ATTR_NAME).asInstanceOf[BagType]
  }

}