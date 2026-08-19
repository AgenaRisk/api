package com.agenarisk.api.model;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import org.junit.jupiter.api.Test;
import uk.co.agena.minerva.model.extendedbn.ContinuousIntervalEN;
import uk.co.agena.minerva.model.extendedbn.ExtendedBN;
import uk.co.agena.minerva.model.extendedbn.ExtendedNode;
import uk.co.agena.minerva.model.extendedbn.ExtendedNodeFunction;
import uk.co.agena.minerva.model.jtinspect.JunctionTreeReport;

/**
 * The pre-calculation junction-tree probe must inspect the tree the real calculation will BUILD, not the
 * graph as authored. It already applies the MultinomialLogit and Comparative pre-passes and binary
 * factorisation for exactly that reason.
 *
 * <p>sumParents() names no parents, so binary factorisation cannot decompose the expression while the
 * token is still in it: a node summing five simulation parents reached the probe with all five attached and
 * its clique estimated at bins^5, tripping the feasibility warning even though the real calculation expands
 * the token, binarises the sum, and never builds that clique.
 */
public class JtProbeParentAggTest {

  private static void setFn(ExtendedNode n, String fn, String... p) throws Exception {
    n.setFunctionMode(ExtendedNode.EDITABLE_NODE_FUNCTION);
    n.setCurrentNodeFunction(new ExtendedNodeFunction(fn, Arrays.asList(p), ExtendedNodeFunction.CURRENT_TYPE));
  }

  /** Five simulation parents feeding one child — the shape whose un-factorised clique is bins^5. */
  private static uk.co.agena.minerva.model.Model build(String childExpr) throws Exception {
    uk.co.agena.minerva.model.Model m = uk.co.agena.minerva.model.Model.createEmptyModel();
    ExtendedBN ebn = m.getExtendedBNAtIndex(0);
    for (int i = 1; i <= 5; i++) {
      ContinuousIntervalEN p = ebn.addContinuousIntervalNode("node_" + i, "node_" + i);
      p.setSimulationNode(true);
      p.setDynamicallyDiscretisable(true);
      setFn(p, "Normal", String.valueOf(i * 10.0), "100.0");
    }
    ContinuousIntervalEN y = ebn.addContinuousIntervalNode("node_6", "sumAgg");
    y.setSimulationNode(true);
    y.setDynamicallyDiscretisable(true);
    setFn(y, "Arithmetic", childExpr);
    for (int i = 1; i <= 5; i++) {
      ebn.getExtendedNodeWithUniqueIdentifier("node_" + i).addChild(y);
    }
    return m;
  }

  private static int worstCliqueWidth(JunctionTreeReport r) {
    int worst = 0;
    for (JunctionTreeReport.NetworkJT n : r.networks) {
      for (JunctionTreeReport.CliqueDim c : n.cliques) {
        worst = Math.max(worst, c.memberNodeIds.size());
      }
    }
    return worst;
  }

  @Test
  public void sumParentsIsExpandedBeforeTheProbeMeasuresCliques() throws Exception {
    JunctionTreeReport spelled = build("sum(node_1,node_2,node_3,node_4,node_5)").inspectJunctionTrees(20);
    JunctionTreeReport token = build("sumParents()").inspectJunctionTrees(20);

    System.out.println("JT spelled-out: infeasible=" + spelled.infeasible + " worstWidth=" + worstCliqueWidth(spelled));
    System.out.println("JT sumParents(): infeasible=" + token.infeasible + " worstWidth=" + worstCliqueWidth(token));

    // The two expressions mean the same thing, so the probe must reach the same verdict.
    assertFalse(spelled.infeasible, "a binarised sum of five parents is feasible");
    assertFalse(token.infeasible, "sumParents() must be expanded before the clique estimate, not after");
    assertTrue(worstCliqueWidth(token) <= worstCliqueWidth(spelled) + 1,
        "sumParents() should give the same binarised tree as the spelled-out sum: "
            + worstCliqueWidth(token) + " vs " + worstCliqueWidth(spelled));
    // Binarised means no clique holds all five parents plus the child.
    assertTrue(worstCliqueWidth(token) < 6,
        "a clique of 6+ members means the sum was never binarised, width=" + worstCliqueWidth(token));
  }
}
