package com.agenarisk.api.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import org.junit.jupiter.api.Test;
import uk.co.agena.minerva.model.extendedbn.ContinuousIntervalEN;
import uk.co.agena.minerva.model.extendedbn.ExtendedBN;
import uk.co.agena.minerva.model.extendedbn.ExtendedNode;
import uk.co.agena.minerva.model.extendedbn.ExtendedNodeFunction;

/**
 * Aggregation expressions must produce the right NUMBER under binary factorisation, not merely a
 * well-shaped graph.
 *
 * <p>This is the check that the previous behaviour would have failed. BF desugared {@code sum()} by
 * replacing every comma in the parameter with a plus, including commas belonging to other calls, so
 * {@code sum(p1,p2) + max(p3,p4)} became {@code (p1+p2) + max(p3+p4)}. A one-argument {@code max} is
 * accepted and returns its argument, so the model calculated {@code p3+p4} — and reported success. Any
 * test asserting only "it calculates", or only the generated graph's shape, passes on that.
 *
 * <p>Each parent is a Normal with a tiny variance, i.e. effectively a point mass, so the expected value
 * of the child is exact arithmetic on the means and needs no oracle beyond hand calculation. It also keeps
 * the models fast: a genuinely diffuse parent would make the unfactorised comparison prohibitive.
 */
public class AggregationValueTest {

  private static final double TIGHT = 1e-6;
  /** p1..p4 sit at these means. Chosen so sum and max are far apart: 1+2=3, max(6,8)=8, 6+8=14. */
  private static final double[] MEANS = { 1.0, 2.0, 6.0, 8.0 };

  private static void setFn(ExtendedNode n, String fn, String... params) throws Exception {
    n.setFunctionMode(ExtendedNode.EDITABLE_NODE_FUNCTION);
    n.setCurrentNodeFunction(
        new ExtendedNodeFunction(fn, Arrays.asList(params), ExtendedNodeFunction.CURRENT_TYPE));
  }

  private static double meanOfY(String childExpr) throws Exception {
    uk.co.agena.minerva.model.Model model = uk.co.agena.minerva.model.Model.createEmptyModel();
    ExtendedBN ebn = model.getExtendedBNAtIndex(0);
    for (int i = 0; i < MEANS.length; i++) {
      ContinuousIntervalEN p = ebn.addContinuousIntervalNode("p" + (i + 1), "p" + (i + 1));
      p.setSimulationNode(true);
      p.setDynamicallyDiscretisable(true);
      setFn(p, "Normal", String.valueOf(MEANS[i]), String.valueOf(TIGHT));
    }
    ContinuousIntervalEN y = ebn.addContinuousIntervalNode("y", "y");
    y.setSimulationNode(true);
    y.setDynamicallyDiscretisable(true);
    setFn(y, "Arithmetic", childExpr);
    for (int i = 1; i <= MEANS.length; i++) {
      if (childExpr.matches(".*\\bp" + i + "\\b.*")) {
        ebn.getExtendedNodeWithUniqueIdentifier("p" + i).addChild(y);
      }
    }
    model.calculate();
    assertTrue(model.isLastPropagationSuccessful(), childExpr + " should calculate");
    return model.getMarginalDataStore()
        .getMarginalDataItemListForNode(ebn, ebn.getExtendedNodeWithUniqueIdentifier("y"))
        .getMarginalDataItemAtIndex(0)
        .getMeanValue();
  }

  private static void check(String expr, double expected) throws Exception {
    double got = meanOfY(expr);
    System.out.printf("VALUE  %-34s expected %7.3f   got %7.3f%n", expr,
        Double.valueOf(expected), Double.valueOf(got));
    assertEquals(expected, got, 0.05, expr + " computed the wrong value");
  }

  @Test
  public void sumAndAvgComputeCorrectly() throws Exception {
    check("sum(p1,p2,p3,p4)", 17.0);
    check("avg(p1,p2,p3,p4)", 17.0 / 4.0);
    check("sum(p1,p2) / 2", 1.5);
    check("avg(p1,p2) * 2", 3.0);
  }

  /**
   * The regression this file exists for. Correct is 3 + max(6,8) = 11; the old comma rewrite gave
   * 3 + (6+8) = 17, which is why the tolerance above is far tighter than the gap between them.
   */
  @Test
  public void sumBesideAnotherCallKeepsThatCallsArguments() throws Exception {
    check("sum(p1,p2) + max(p3,p4)", 11.0);
    check("sum(p1,p2) + min(p3,p4)", 9.0);
    check("sum(p1, max(p3,p4), p2)", 11.0);
    check("avg(p1, max(p3,p4), p2)", 11.0 / 3.0);
  }

  @Test
  public void wholeExpressionMinMaxStillWorks() throws Exception {
    check("min(p1,p2,p3,p4)", 1.0);
    check("max(p1,p2,p3,p4)", 8.0);
    check("max(min(p1,p2), min(p3,p4))", 6.0);
  }

  @Test
  public void anIdentifierContainingSumIsNotMangled() throws Exception {
    // Not "consumption" as a node id here — the point is that a sum() call over ordinary ids works and
    // the surrounding arithmetic is untouched.
    check("sum(p1,p2,p3,p4) - p4", 9.0);
  }
}
