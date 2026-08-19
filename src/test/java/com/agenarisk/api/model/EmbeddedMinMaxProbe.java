package com.agenarisk.api.model;

import java.util.Arrays;
import org.junit.jupiter.api.Test;
import uk.co.agena.minerva.model.extendedbn.ContinuousIntervalEN;
import uk.co.agena.minerva.model.extendedbn.ExtendedBN;
import uk.co.agena.minerva.model.extendedbn.ExtendedNode;
import uk.co.agena.minerva.model.extendedbn.ExtendedNodeFunction;

/**
 * min/max are handled only by MinMaxParser, which requires the WHOLE expression to be the call. The open
 * question for fixing the embedded case is whether BF's generic tree walker can handle a 2-argument
 * min/max on its own — because if it can, reducing variadic min/max to nested pairs in
 * AggregationDesugarer fixes the entire class, and if it cannot, the walker needs real min/max support.
 *
 * <p>Ladder of cases, simplest first, so the boundary of what works is visible rather than inferred.
 * Parents are near-point masses so the expected value is exact arithmetic on the means.
 */
public class EmbeddedMinMaxProbe {

  private static final double[] MEANS = { 1.0, 2.0, 6.0, 8.0, 5.0 };

  private static void setFn(ExtendedNode n, String fn, String... params) throws Exception {
    n.setFunctionMode(ExtendedNode.EDITABLE_NODE_FUNCTION);
    n.setCurrentNodeFunction(
        new ExtendedNodeFunction(fn, Arrays.asList(params), ExtendedNodeFunction.CURRENT_TYPE));
  }

  private static uk.co.agena.minerva.model.Model build(String expr) throws Exception {
    uk.co.agena.minerva.model.Model model = uk.co.agena.minerva.model.Model.createEmptyModel();
    ExtendedBN ebn = model.getExtendedBNAtIndex(0);
    for (int i = 0; i < MEANS.length; i++) {
      ContinuousIntervalEN p = ebn.addContinuousIntervalNode("node_" + (i + 1), "node_" + (i + 1));
      p.setSimulationNode(true);
      p.setDynamicallyDiscretisable(true);
      setFn(p, "Normal", String.valueOf(MEANS[i]), "1e-6");
    }
    ContinuousIntervalEN y = ebn.addContinuousIntervalNode("y", "y");
    y.setSimulationNode(true);
    y.setDynamicallyDiscretisable(true);
    setFn(y, "Arithmetic", expr);
    for (int i = 1; i <= MEANS.length; i++) {
      if (expr.matches(".*\\bnode_" + i + "\\b.*")) {
        ebn.getExtendedNodeWithUniqueIdentifier("node_" + i).addChild(y);
      }
    }
    return model;
  }

  private static void probe(String label, String expr, String expected) {
    try {
      uk.co.agena.minerva.model.Model m = build(expr);
      m.calculate();
      ExtendedBN ebn = m.getExtendedBNAtIndex(0);
      double mean = m.getMarginalDataStore()
          .getMarginalDataItemListForNode(ebn, ebn.getExtendedNodeWithUniqueIdentifier("y"))
          .getMarginalDataItemAtIndex(0).getMeanValue();
      System.out.printf("EMB %-13s ok=%-5s E[y]=%10.4f  expected %-16s | %s%n", label,
          Boolean.valueOf(m.isLastPropagationSuccessful()), Double.valueOf(mean), expected, expr);
    } catch (Throwable t) {
      Throwable root = t;
      while (root.getCause() != null && root.getCause() != root) {
        root = root.getCause();
      }
      System.out.printf("EMB %-13s THREW %s: %s%n   | %s%n", label,
          root.getClass().getSimpleName(), root.getMessage(), expr);
    }
  }

  @Test
  public void ladder() {
    // Baseline: whole-expression min/max, known to work.
    probe("whole-2", "min(node_1,node_2)", "1");
    probe("whole-5", "min(node_1,node_2,node_3,node_4,node_5)", "1");
    // The decisive one: a 2-arg min with anything at all around it.
    probe("embedded-2", "min(node_1,node_2) + 1", "2");
    probe("emb-2-node", "min(node_1,node_2) + node_3", "7");
    // Already in nested-pair form and embedded — what the desugarer would produce for 5 args.
    probe("nested-pairs", "min(min(min(min(node_1,node_2),node_3),node_4),node_5) + 1", "2");
    // Variadic min embedded.
    probe("embedded-5", "min(node_1,node_2,node_3,node_4,node_5) + 1", "2");
    // The reported failure, verbatim: avg=4.4, sum=22, min=1 -> 25.4
    probe("reported",
        "avg(node_1,node_2,node_3,node_4,node_5)+sum(node_1,node_2,node_3,node_4,node_5)"
            + "-min(node_1,node_2,node_3,node_4,node_5)", "25.4");
  }
}
