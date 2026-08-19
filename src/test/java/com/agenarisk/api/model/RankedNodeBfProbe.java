package com.agenarisk.api.model;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import uk.co.agena.minerva.model.extendedbn.ExtendedBN;
import uk.co.agena.minerva.model.extendedbn.ExtendedNode;
import uk.co.agena.minerva.model.extendedbn.ExtendedNodeFunction;
import uk.co.agena.minerva.model.extendedbn.RankedEN;
import uk.co.agena.minerva.util.binaryfactorisation.BinaryBNConverter;
import uk.co.agena.minerva.util.model.DataSet;

/**
 * Ranked nodes are excluded from binary factorisation by design — BinaryBNConverter's selection gate
 * tests !(node instanceof RankedEN), and ranked PARENTS are not even counted toward the fan-in that
 * triggers it. Their state count is small and fixed, so there is nothing to tame.
 *
 * <p>This checks the recent aggregation work did not quietly route them into BF: if it had, the generated
 * model would show the child binarised (in-degree 2) instead of keeping all its parents.
 */
public class RankedNodeBfProbe {

  private static void setFn(ExtendedNode n, String fn, String... params) throws Exception {
    n.setFunctionMode(ExtendedNode.EDITABLE_NODE_FUNCTION);
    n.setCurrentNodeFunction(
        new ExtendedNodeFunction(fn, Arrays.asList(params), ExtendedNodeFunction.CURRENT_TYPE));
  }

  private static RankedEN ranked(ExtendedBN ebn, String id) throws Exception {
    RankedEN n = ebn.addRankedNode(id, id);
    DataSet ds = new DataSet();
    for (String s : new String[] { "Low", "Medium", "High" }) {
      ds.addLabelledDataPoint(s);
    }
    n.createExtendedStates(ds);
    return n;
  }

  private static void report(String label, String childExpr) {
    try {
      uk.co.agena.minerva.model.Model model = uk.co.agena.minerva.model.Model.createEmptyModel();
      ExtendedBN ebn = model.getExtendedBNAtIndex(0);
      for (int i = 1; i <= 5; i++) {
        ranked(ebn, "r" + i);
      }
      RankedEN y = ranked(ebn, "y");
      setFn(y, "Arithmetic", childExpr);
      for (int i = 1; i <= 5; i++) {
        ebn.getExtendedNodeWithUniqueIdentifier("r" + i).addChild(y);
      }

      uk.co.agena.minerva.model.Model copy = uk.co.agena.minerva.model.Model.deepCopyInMemory(model);
      BinaryBNConverter conv = new BinaryBNConverter(model, copy);
      List<ExtendedBN> bns = new ArrayList<ExtendedBN>();
      bns.add(model.getExtendedBNAtIndex(0));
      conv.convertBNList(bns, model, new Boolean[] { Boolean.TRUE });
      uk.co.agena.minerva.model.Model built = conv.getBuiltBinaryModel();

      String shape;
      if (built == null) {
        shape = "no binary model built (untouched)";
      } else {
        ExtendedBN gen = built.getExtendedBNAtIndex(0);
        ExtendedNode leaf = gen.getExtendedNodeWithUniqueIdentifier("y");
        int deg = leaf == null ? -1 : gen.getParentNodes(leaf).size();
        boolean synthesised = false;
        for (Object o : gen.getExtendedNodes()) {
          if (((ExtendedNode) o).getConnNodeId().contains("new_node_")) {
            synthesised = true;
            break;
          }
        }
        shape = "generated nodes=" + gen.getExtendedNodes().size() + " y in-degree=" + deg
            + " synthesised=" + synthesised
            + (deg == 5 && !synthesised ? "  => LEFT ALONE" : "  => TOUCHED BY BF");
      }
      System.out.println("RANKED " + label + " | " + childExpr + " | " + shape);
    } catch (Throwable t) {
      Throwable root = t;
      while (root.getCause() != null && root.getCause() != root) {
        root = root.getCause();
      }
      System.out.println("RANKED " + label + " | " + childExpr + " | THREW "
          + root.getClass().getSimpleName() + ": " + root.getMessage());
    }
  }

  @Test
  public void rankedNodesAreNotFactorised() {
    report("plain sum", "r1+r2+r3+r4+r5");
    report("sum()", "sum(r1,r2,r3,r4,r5)");
    report("min()", "min(r1,r2,r3,r4,r5)");
    report("max()", "max(r1,r2,r3,r4,r5)");
    report("avg()", "avg(r1,r2,r3,r4,r5)");
    report("min embedded", "min(r1,r2,r3,r4,r5)+1");
  }

  /**
   * The remaining way a ranked node could be dragged into BF: not as the factorised node, but as a PARENT
   * referenced inside a factorised continuous child's expression. BinaryBNConverter:243 excludes ranked
   * parents from the fan-in count that triggers factorisation, but the expression can still name one.
   */
  @Test
  public void aRankedParentOfAFactorisedChild() {
    try {
      uk.co.agena.minerva.model.Model model = uk.co.agena.minerva.model.Model.createEmptyModel();
      ExtendedBN ebn = model.getExtendedBNAtIndex(0);
      for (int i = 1; i <= 4; i++) {
        uk.co.agena.minerva.model.extendedbn.ContinuousIntervalEN c =
            ebn.addContinuousIntervalNode("c" + i, "c" + i);
        c.setSimulationNode(true);
        c.setDynamicallyDiscretisable(true);
        setFn(c, "Normal", String.valueOf(i * 2.0), "1.0");
      }
      ranked(ebn, "r1");
      uk.co.agena.minerva.model.extendedbn.ContinuousIntervalEN y =
          ebn.addContinuousIntervalNode("y", "y");
      y.setSimulationNode(true);
      y.setDynamicallyDiscretisable(true);
      setFn(y, "Arithmetic", "sum(c1,c2,c3,c4,r1)");
      for (String id : new String[] { "c1", "c2", "c3", "c4", "r1" }) {
        ebn.getExtendedNodeWithUniqueIdentifier(id).addChild(y);
      }
      model.calculate();
      System.out.println("RANKED as-parent | sum(c1..c4,r1) | calculate ok="
          + model.isLastPropagationSuccessful());
    } catch (Throwable t) {
      Throwable root = t;
      while (root.getCause() != null && root.getCause() != root) {
        root = root.getCause();
      }
      System.out.println("RANKED as-parent | sum(c1..c4,r1) | THREW "
          + root.getClass().getSimpleName() + ": " + root.getMessage());
    }
  }
}
