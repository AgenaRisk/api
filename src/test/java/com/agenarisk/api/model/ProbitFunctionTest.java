package com.agenarisk.api.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import uk.co.agena.minerva.model.extendedbn.ExtendedBN;
import uk.co.agena.minerva.model.extendedbn.ExtendedNode;
import uk.co.agena.minerva.model.extendedbn.ExtendedNodeFunction;
import uk.co.agena.minerva.util.binaryfactorisation.ProbitFactoriser;
import uk.co.agena.minerva.util.nptgenerator.NPTGeneratorException;
import uk.co.agena.minerva.util.nptgenerator.Probit;

/**
 * Probit: the closed-form values, the failure modes that used to be silent, and the shape the predictor lift
 * produces. Everything here is licence-free and DD-independent — no propagation — which is deliberate: the
 * real defects found while building this were all structural, and the one accuracy question (does the engine
 * reproduce the closed form?) is answered by the worked example model rather than by a unit test.
 *
 * <p>References are exact:
 * {@code P(S_k) = Phi((tau_k - eta)/sigma) - Phi((tau_{k-1} - eta)/sigma)}.
 */
public class ProbitFunctionTest {

  private static final double TOL = 1e-7;

  private static double[] dist(double eta, double sigma, double... tau) throws Exception {
    // Probit.distribution is package-private; reach it the same way the engine does, through the static
    // no-sampling entry point, which needs an Object[] of the full argument list.
    Object[] args = new Object[2 + tau.length];
    args[0] = Double.valueOf(eta);
    args[1] = Double.valueOf(sigma);
    for (int i = 0; i < tau.length; i++) {
      args[2 + i] = Double.valueOf(tau[i]);
    }
    List<Object> states = new ArrayList<>();
    for (int i = 0; i <= tau.length; i++) {
      states.add(new Object()); // only size() is read on this path
    }
    return Probit.Probit(states, args);
  }

  private static void assertDist(double[] got, double... want) {
    assertEquals(want.length, got.length, "band count");
    double sum = 0;
    for (int i = 0; i < want.length; i++) {
      assertEquals(want[i], got[i], TOL, "band " + i);
      sum += got[i];
    }
    assertEquals(1.0, sum, 1e-12, "bands must sum to 1");
  }

  @Test
  public void orderedProbitMatchesClosedForm() throws Exception {
    // eta = 0.5, tau = (-1, 0, 1) — the case DD_PROBIT_HIERARCHICAL_LOGIT section 2 measured against the
    // hand-built latent+Comparative form, so these numbers tie the two mechanisms together.
    assertDist(dist(0.5, 1, -1, 0, 1), 0.0668072, 0.2417303, 0.3829249, 0.3085375);
  }

  @Test
  public void binaryProbitMatchesClosedForm() throws Exception {
    assertDist(dist(0.5, 1, 0), 0.3085375, 0.6914625);
  }

  @Test
  public void heteroskedasticScaleMatchesClosedForm() throws Exception {
    // sigma = exp(0.3): the scale enters as a divisor, so this is Phi((tau - 0.9)/1.3498588).
    assertDist(dist(0.9, Math.exp(0.3), -1, 0, 1), 0.0796315, 0.1728388, 0.2770571, 0.4704726);
  }

  @Test
  public void constantNonUnitScaleMatchesClosedForm() throws Exception {
    assertDist(dist(0.5, 2, -1, 0, 1), 0.2266274, 0.1746663, 0.1974127, 0.4012937);
  }

  @Test
  public void farTailBandsAreNotLostToCancellation() throws Exception {
    // Phi(-6) = 9.8658765e-10. Computed as a difference of two ~0.5 numbers this would be noise; the CDF is
    // erfc-based (cern.jet.stat.Probability.normal) precisely so it is not.
    double[] got = dist(0, 1, -6, 6);
    assertEquals(9.8658765e-10, got[0], 1e-16);
    assertEquals(9.8658765e-10, got[2], 1e-16);
  }

  @Test
  public void nonPositiveScaleThrowsRatherThanReturningUniform() {
    // Must THROW, not return zeros: a zero column is normalised into a uniform distribution downstream, so
    // the model would compute a plausible wrong answer. Function.evaluate swallows JepException, which is why
    // this is raised from the probability calculation itself.
    assertThrows(NPTGeneratorException.class, () -> dist(0.5, 0, -1, 0, 1));
    assertThrows(NPTGeneratorException.class, () -> dist(0.5, -2, -1, 0, 1));
    assertThrows(NPTGeneratorException.class, () -> dist(0.5, Double.NaN, 0));
  }

  @Test
  public void nonIncreasingCutpointsThrow() {
    assertThrows(NPTGeneratorException.class, () -> dist(0.5, 1, 0, -1, 1));
    assertThrows(NPTGeneratorException.class, () -> dist(0.5, 1, 0, 0, 1));
  }

  @Test
  public void wrongArityThrows() {
    // A 4-state node needs 2 + 3 = 5 arguments. Supply 4 (as if the scale had been forgotten) and 6.
    List<Object> fourStates = new ArrayList<>();
    for (int i = 0; i < 4; i++) {
      fourStates.add(new Object());
    }
    assertThrows(NPTGeneratorException.class, () -> Probit.Probit(fourStates,
        new Object[] { 0.5, -1.0, 0.0, 1.0 }));
    assertThrows(NPTGeneratorException.class, () -> Probit.Probit(fourStates,
        new Object[] { 0.5, 1.0, -1.0, 0.0, 1.0, 2.0 }));
  }

  // ---------------------------------------------------------------------------------------------------
  // The predictor lift.
  // ---------------------------------------------------------------------------------------------------

  private static Model twoCovariateProbit() throws Exception {
    Model m = Model.createModel();
    Network net = m.createNetwork("n", "n");
    Node x1 = net.createNode("x1", Node.Type.ContinuousInterval);
    x1.convertToSimulated();
    x1.setTableFunction("Normal(0,1)");
    Node x2 = net.createNode("x2", Node.Type.ContinuousInterval);
    x2.convertToSimulated();
    x2.setTableFunction("Normal(0,1)");
    Node y = net.createNode("y", Node.Type.Labelled);
    y.setStates(java.util.Arrays.asList("None", "Minor", "Major", "Severe"));
    x1.linkTo(y);
    x2.linkTo(y);
    y.setTableFunction("Probit(0.5+0.8*x1-0.6*x2,1,-1,0,1)");
    return m;
  }

  private static ExtendedNode node(uk.co.agena.minerva.model.Model lm, String id) {
    for (Object o : lm.getExtendedBNList().getExtendedBNs()) {
      ExtendedNode n = ((ExtendedBN) o).getExtendedNodeWithUniqueIdentifier(id);
      if (n != null) {
        return n;
      }
    }
    return null;
  }

  @Test
  public void liftLeavesTheHostWithOneParentAndKeepsTheCutpointsVerbatim() throws Exception {
    uk.co.agena.minerva.model.Model lm = twoCovariateProbit().getLogicModel();
    assertTrue(ProbitFactoriser.factorise(lm), "a two-covariate probit should qualify");

    ExtendedBN ebn = (ExtendedBN) lm.getExtendedBNList().getExtendedBNs().get(0);
    ExtendedNode y = node(lm, "y");
    List parents = ebn.getParentNodes(y);
    assertEquals(1, parents.size(), "the host keeps exactly one parent — the lifted predictor");
    String scoreId = ((ExtendedNode) parents.get(0)).getConnNodeId();
    assertTrue(scoreId.startsWith(ProbitFactoriser.SCORE_PREFIX), scoreId);

    ExtendedNodeFunction fn = y.getCurrentNodeFunction();
    assertEquals(Probit.displayName, fn.getName());
    List<String> args = fn.getParameters();
    assertEquals(5, args.size());
    assertEquals(scoreId, args.get(0), "argument 0 becomes the score node");
    // Compared numerically, not textually: the api normalises a numeric literal on the way in, so "1" is
    // stored as "1.0". "Verbatim" here means the VALUE is untouched — the lift must not turn a cutpoint into
    // a node, which is what would add K-1 spurious parents to the host.
    assertEquals(1.0, Double.parseDouble(args.get(1).trim()), 0, "a literal scale is NOT lifted");
    assertEquals(-1.0, Double.parseDouble(args.get(2).trim()), 0, "cutpoints pass through");
    assertEquals(0.0, Double.parseDouble(args.get(3).trim()), 0);
    assertEquals(1.0, Double.parseDouble(args.get(4).trim()), 0);

    // The score node carries the covariates, so binary factorisation tames the sum rather than the host.
    ExtendedNode score = node(lm, scoreId);
    assertNotNull(score);
    assertEquals(2, ebn.getParentNodes(score).size());
  }

  @Test
  public void liftIsIdempotent() throws Exception {
    uk.co.agena.minerva.model.Model lm = twoCovariateProbit().getLogicModel();
    assertTrue(ProbitFactoriser.factorise(lm));
    int nodesAfterFirst = ((ExtendedBN) lm.getExtendedBNList().getExtendedBNs().get(0))
        .getExtendedNodes().size();
    assertFalse(ProbitFactoriser.factorise(lm), "a second pass must be a no-op");
    assertEquals(nodesAfterFirst, ((ExtendedBN) lm.getExtendedBNList().getExtendedBNs().get(0))
        .getExtendedNodes().size(), "no nodes added by the second pass");
  }

  @Test
  public void aHeteroskedasticScaleIsLiftedToo() throws Exception {
    Model m = Model.createModel();
    Network net = m.createNetwork("n", "n");
    Node x1 = net.createNode("x1", Node.Type.ContinuousInterval);
    x1.convertToSimulated();
    x1.setTableFunction("Normal(0,1)");
    Node x2 = net.createNode("x2", Node.Type.ContinuousInterval);
    x2.convertToSimulated();
    x2.setTableFunction("Normal(0,1)");
    Node y = net.createNode("y", Node.Type.Labelled);
    y.setStates(java.util.Arrays.asList("None", "Minor", "Major", "Severe"));
    x1.linkTo(y);
    x2.linkTo(y);
    y.setTableFunction("Probit(0.5+0.8*x1,exp(0.3*x2),-1,0,1)");

    uk.co.agena.minerva.model.Model lm = m.getLogicModel();
    assertTrue(ProbitFactoriser.factorise(lm));
    ExtendedBN ebn = (ExtendedBN) lm.getExtendedBNList().getExtendedBNs().get(0);
    ExtendedNode y2 = node(lm, "y");
    assertEquals(2, ebn.getParentNodes(y2).size(),
        "a live scale is a second lifted parent — clique K*b^2 rather than K*b");
    List<String> args = y2.getCurrentNodeFunction().getParameters();
    assertTrue(args.get(1).startsWith(ProbitFactoriser.SCORE_PREFIX), args.get(1));
  }

  @Test
  public void aSingleParentPredictorIsLeftAlone() throws Exception {
    Model m = Model.createModel();
    Network net = m.createNetwork("n", "n");
    Node x1 = net.createNode("x1", Node.Type.ContinuousInterval);
    x1.convertToSimulated();
    x1.setTableFunction("Normal(0,1)");
    Node y = net.createNode("y", Node.Type.Labelled);
    y.setStates(java.util.Arrays.asList("Low", "High"));
    x1.linkTo(y);
    y.setTableFunction("Probit(0.8*x1,1,0)");

    uk.co.agena.minerva.model.Model lm = m.getLogicModel();
    assertFalse(ProbitFactoriser.factorise(lm),
        "nothing to gain: the clique is K*b either way and a lifted node only adds a discretisation");
    assertNull(node(lm, ProbitFactoriser.SCORE_PREFIX + "y_0"));
  }
}
