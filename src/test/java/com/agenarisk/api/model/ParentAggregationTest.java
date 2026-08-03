package com.agenarisk.api.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import org.junit.jupiter.api.Test;
import uk.co.agena.minerva.model.extendedbn.ContinuousIntervalEN;
import uk.co.agena.minerva.model.extendedbn.ExtendedBN;
import uk.co.agena.minerva.model.extendedbn.ExtendedNode;
import uk.co.agena.minerva.model.extendedbn.ExtendedNodeFunction;

/**
 * sumParents() / avgParents() / minParents() / maxParents() expand against the node's real parent list.
 * Parents are near-point masses so each expectation is exact arithmetic on the means (1,2,6,8 -> sum 17,
 * avg 4.25, min 1, max 8).
 */
public class ParentAggregationTest {

  private static final double[] MEANS = { 1.0, 2.0, 6.0, 8.0 };

  private static void setFn(ExtendedNode n, String fn, String... params) throws Exception {
    n.setFunctionMode(ExtendedNode.EDITABLE_NODE_FUNCTION);
    n.setCurrentNodeFunction(
        new ExtendedNodeFunction(fn, Arrays.asList(params), ExtendedNodeFunction.CURRENT_TYPE));
  }

  private static uk.co.agena.minerva.model.Model build(String childExpr, int nParents) throws Exception {
    uk.co.agena.minerva.model.Model model = uk.co.agena.minerva.model.Model.createEmptyModel();
    ExtendedBN ebn = model.getExtendedBNAtIndex(0);
    for (int i = 0; i < nParents; i++) {
      ContinuousIntervalEN p = ebn.addContinuousIntervalNode("p" + (i + 1), "p" + (i + 1));
      p.setSimulationNode(true);
      p.setDynamicallyDiscretisable(true);
      setFn(p, "Normal", String.valueOf(MEANS[i]), "1e-6");
    }
    ContinuousIntervalEN y = ebn.addContinuousIntervalNode("y", "y");
    y.setSimulationNode(true);
    y.setDynamicallyDiscretisable(true);
    setFn(y, "Arithmetic", childExpr);
    for (int i = 1; i <= nParents; i++) {
      ebn.getExtendedNodeWithUniqueIdentifier("p" + i).addChild(y);
    }
    return model;
  }

  /**
   * Mean from the marginal itself, NOT MarginalDataItem.getMeanValue(). After a working-copy detour -
   * which these expressions all take - copyBack transfers the marginal dataset but not the summary
   * statistics, so getMeanValue() answers 0.0 while the bins are correct. Measuring it would report this
   * feature as broken when it is not; the staleness is a separate defect in copyBack.
   */
  private static double meanOfY(uk.co.agena.minerva.model.Model m) throws Exception {
    ExtendedBN ebn = m.getExtendedBNAtIndex(0);
    uk.co.agena.minerva.util.model.DataSet ds = m.getMarginalDataStore()
        .getMarginalDataItemListForNode(ebn, ebn.getExtendedNodeWithUniqueIdentifier("y"))
        .getMarginalDataItemAtIndex(0).getDataset();
    double mean = 0;
    double mass = 0;
    for (int i = 0; i < ds.size(); i++) {
      uk.co.agena.minerva.util.model.DataPoint dp = ds.getDataPointAtOrderPosition(i);
      if (dp instanceof uk.co.agena.minerva.util.model.IntervalDataPoint) {
        uk.co.agena.minerva.util.model.IntervalDataPoint ip =
            (uk.co.agena.minerva.util.model.IntervalDataPoint) dp;
        mean += ((ip.getIntervalLowerBound() + ip.getIntervalUpperBound()) / 2.0) * dp.getValue();
        mass += dp.getValue();
      }
    }
    assertEquals(1.0, mass, 1e-6, "the marginal should carry all the probability mass");
    return mean;
  }

  private static void check(String expr, double expected) throws Exception {
    uk.co.agena.minerva.model.Model m = build(expr, 4);
    m.calculate();
    assertTrue(m.isLastPropagationSuccessful(), expr + " should calculate");
    double got = meanOfY(m);
    System.out.printf("PARENTS %-28s expected %8.4f  got %8.4f%n", expr,
        Double.valueOf(expected), Double.valueOf(got));
    assertEquals(expected, got, 0.05, expr);
  }

  @Test
  public void theFourFormsAggregateEveryParent() throws Exception {
    check("sumParents()", 17.0);
    check("avgParents()", 17.0 / 4.0);
    check("minParents()", 1.0);
    check("maxParents()", 8.0);
  }

  @Test
  public void theyComposeWithSurroundingArithmetic() throws Exception {
    check("sumParents() - minParents()", 16.0);
    check("maxParents() - minParents()", 7.0);
    check("avgParents() * 2", 8.5);
    check("sumParents() / 4", 4.25);
  }

  /** The whole point: the expression tracks the graph, so adding a parent changes the answer. */
  @Test
  public void addingAParentChangesTheResultWithNoEditToTheExpression() throws Exception {
    uk.co.agena.minerva.model.Model three = build("sumParents()", 3);
    three.calculate();
    assertEquals(1.0 + 2.0 + 6.0, meanOfY(three), 0.05, "three parents");

    uk.co.agena.minerva.model.Model four = build("sumParents()", 4);
    four.calculate();
    assertEquals(1.0 + 2.0 + 6.0 + 8.0, meanOfY(four), 0.05, "four parents, same expression text");
  }

  /** The caller's model must be left holding the token, or the expression stops tracking the graph. */
  @Test
  public void theAuthoredExpressionIsNotRewritten() throws Exception {
    uk.co.agena.minerva.model.Model m = build("sumParents()", 4);
    m.calculate();
    ExtendedNode y = m.getExtendedBNAtIndex(0).getExtendedNodeWithUniqueIdentifier("y");
    String after = y.getCurrentNodeFunction().getParameters().get(0).toString();
    assertEquals("sumParents()", after, "the caller's expression must survive calculation verbatim");
  }

  /** A form on a node with no parents is a modelling error, not an empty sum quietly worth zero. */
  @Test
  public void noParentsIsReportedRatherThanTreatedAsZero() throws Exception {
    uk.co.agena.minerva.model.Model model = uk.co.agena.minerva.model.Model.createEmptyModel();
    ExtendedBN ebn = model.getExtendedBNAtIndex(0);
    ContinuousIntervalEN lonely = ebn.addContinuousIntervalNode("lonely", "lonely");
    lonely.setSimulationNode(true);
    lonely.setDynamicallyDiscretisable(true);
    setFn(lonely, "Arithmetic", "sumParents()");
    String msg = null;
    boolean ok = false;
    try {
      model.calculate();
      ok = model.isLastPropagationSuccessful();
    } catch (Throwable t) {
      msg = String.valueOf(t.getMessage());
    }
    System.out.println("PARENTS no-parents: threw=" + msg + " successful=" + ok);
    // The contract is that it must NOT quietly succeed with a plausible-looking number. Either an
    // exception naming the node, or a failed propagation, is acceptable; a clean success is not.
    assertTrue(msg != null || !ok,
        "a form used on a node with no parents must not report a successful calculation");
    if (msg != null) {
      assertTrue(msg.contains("no parents") || msg.contains("lonely"),
          "if it throws, the message should name the problem or the node, was: " + msg);
    }
  }
}
