package com.agenarisk.api.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import org.junit.jupiter.api.Test;
import uk.co.agena.minerva.model.extendedbn.ContinuousEN;
import uk.co.agena.minerva.model.extendedbn.ExtendedBN;
import uk.co.agena.minerva.model.extendedbn.ExtendedNode;
import uk.co.agena.minerva.model.extendedbn.ExtendedNodeFunction;
import uk.co.agena.minerva.model.extendedbn.IntegerIntervalEN;
import uk.co.agena.minerva.model.extendedbn.LabelledEN;
import uk.co.agena.minerva.util.binaryfactorisation.MultinomialLogitFactoriser;
import uk.co.agena.minerva.util.model.DataSet;

/**
 * The mlogit 0/1 dummies are integer nodes with the zero-width states [0,0] and [1,1]. Two things must
 * hold for that to be an improvement rather than a new bug:
 *
 * <ol>
 *   <li>a child's expression must see exactly 0 or exactly 1 — the old continuous [-0.5,0.5]/[0.5,1.5]
 *       encoding smeared it over ±0.5 and, under the boundsOnly sampling NPTGenerator uses for
 *       Arithmetic, never evaluated 0 at all; and</li>
 *   <li>the dummy's OWN NPT must still put all its mass in the right state — zero-width interval
 *       containment is exactly where a fencepost would hide.</li>
 * </ol>
 *
 * (2) is the one that could silently produce a degenerate dummy, so it is asserted per parent state
 * against a 3-state parent whose MIDDLE state is the indicated one: a fencepost at either end shows up.
 */
public class IndicatorExactStatesTest {

  private static final String[] REGIONS = { "south", "middle", "north" };
  private static final int INDICATED = 1; // "middle"

  private static LabelledEN labelled(ExtendedBN ebn, String id, String... states) throws Exception {
    LabelledEN n = ebn.addLabelledNode(id, id);
    DataSet ds = new DataSet();
    for (String s : states) {
      ds.addLabelledDataPoint(s);
    }
    n.createExtendedStates(ds);
    return n;
  }

  private static void setFn(ExtendedNode n, String fn, String... params) throws Exception {
    n.setFunctionMode(ExtendedNode.EDITABLE_NODE_FUNCTION);
    n.setCurrentNodeFunction(
        new ExtendedNodeFunction(fn, Arrays.asList(params), ExtendedNodeFunction.CURRENT_TYPE));
  }

  /**
   * region (3 states) + x (continuous) -> y (2 states) via MultinomialLogit with one Indicator dummy.
   * The continuous covariate is required, not decoration: the pre-pass only expands a logit with at
   * least one continuous parent, since a pure-discrete one has no fan-in for BF to tame afterwards.
   */
  private static uk.co.agena.minerva.model.Model threeStateLogit() throws Exception {
    uk.co.agena.minerva.model.Model model = uk.co.agena.minerva.model.Model.createEmptyModel();
    ExtendedBN ebn = model.getExtendedBNAtIndex(0);
    LabelledEN region = labelled(ebn, "region", REGIONS);

    uk.co.agena.minerva.model.extendedbn.ContinuousIntervalEN x =
        ebn.addContinuousIntervalNode("x", "x");
    x.setSimulationNode(true);
    x.setDynamicallyDiscretisable(true);
    setFn(x, "Normal", "0.0", "1.0");

    LabelledEN y = labelled(ebn, "y", "a", "b");
    region.addChild(y);
    x.addChild(y);
    setFn(y, "MultinomialLogit", "0.7 + 1.3 * Indicator(region, \"middle\") + 0.4 * x");
    return model;
  }

  /**
   * The caller's model is never mutated during a real calculation, so re-derive the rewrite directly.
   * ignoreCostGate=true because this model is deliberately tiny — the clique-cost gate exists to skip
   * cheap logits, and skipping is not what is under test here.
   */
  private static ExtendedNode factoriseAndFindDummy(uk.co.agena.minerva.model.Model model)
      throws Exception {
    MultinomialLogitFactoriser.factorise(model, true);
    ExtendedBN bn = model.getExtendedBNAtIndex(0);
    for (Object o : bn.getExtendedNodes()) {
      ExtendedNode en = (ExtendedNode) o;
      if (en.getConnNodeId().startsWith(MultinomialLogitFactoriser.IND_PREFIX)) {
        return en;
      }
    }
    return null;
  }

  @Test
  public void dummyIsAnIntegerNodeWhoseStatesSampleExactly() throws Exception {
    ExtendedNode ind = factoriseAndFindDummy(threeStateLogit());
    assertTrue(ind != null, "no indicator node was synthesised — the logit was not expanded");
    assertTrue(ind instanceof IntegerIntervalEN,
        "dummy should be an integer node, was " + ind.getClass().getSimpleName());

    ContinuousEN c = (ContinuousEN) ind;
    assertTrue(!c.isSimulationNode(), "dummy must not be a simulation node");
    assertEquals(2, c.getExtendedStates().size(), "a 0/1 dummy needs exactly two states");

    for (int state = 0; state < 2; state++) {
      // boundsOnly=true is what NPTGenerator uses for Arithmetic, so it is the one that matters.
      assertExactlyOneValue(state, (double[]) c.getSamplesForState(state, true, 20), "boundsOnly");
      assertExactlyOneValue(state, (double[]) c.getSamplesForState(state, false, 5), "sampled");
    }
  }

  private static void assertExactlyOneValue(int state, double[] got, String mode) {
    assertEquals(1, got.length,
        mode + " for state " + state + " should be a single exact value, got " + Arrays.toString(got));
    assertEquals((double) state, got[0], 0.0,
        mode + " for state " + state + " should be exactly " + state);
  }

  /**
   * The dummy's own NPT: a point mass on 1 for the indicated parent state and on 0 for every other. A
   * zero-width containment bug would show up as mass in the wrong state, or as a 50/50 split.
   */
  @Test
  public void dummyNptIsAPointMassInTheCorrectStatePerParentState() throws Exception {
    uk.co.agena.minerva.model.Model model = threeStateLogit();
    ExtendedNode ind = factoriseAndFindDummy(model);
    assertTrue(ind != null, "no indicator node was synthesised");
    model.calculate();

    float[][] npt = ind.getNPT();
    System.out.println("IND NPT states=" + npt.length + " parentCombos=" + npt[0].length);
    for (int s = 0; s < npt.length; s++) {
      System.out.println("  dummy=" + s + " -> " + Arrays.toString(npt[s]));
    }

    assertEquals(2, npt.length, "dummy should have 2 states");
    assertEquals(REGIONS.length, npt[0].length, "one column per region state");

    for (int col = 0; col < REGIONS.length; col++) {
      boolean on = (col == INDICATED);
      assertEquals(on ? 1.0 : 0.0, npt[1][col], 1e-6,
          "P(dummy=1 | region=" + REGIONS[col] + ") is wrong — zero-width state containment suspect");
      assertEquals(on ? 0.0 : 1.0, npt[0][col], 1e-6,
          "P(dummy=0 | region=" + REGIONS[col] + ") is wrong");
    }
  }
}
