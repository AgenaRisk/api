package com.agenarisk.api.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import org.junit.jupiter.api.Test;
import uk.co.agena.minerva.model.extendedbn.ContinuousIntervalEN;
import uk.co.agena.minerva.model.extendedbn.ExtendedBN;
import uk.co.agena.minerva.model.extendedbn.ExtendedNode;
import uk.co.agena.minerva.model.extendedbn.ExtendedNodeFunction;
import uk.co.agena.minerva.model.extendedbn.LabelledEN;
import uk.co.agena.minerva.util.binaryfactorisation.MultinomialLogitFactoriser;
import uk.co.agena.minerva.util.model.DataSet;

/**
 * Does encoding the mlogit 0/1 dummies exactly actually buy accuracy? Measured against a closed form
 * rather than argued.
 *
 * <p>The model is a 2-category logit whose single predictor is
 * {@code B0 + BIND*Indicator(region,"middle") + B1*x1 + B2*x2 + B3*x3}, with region uniform over three
 * states and x1..x3 independent standard normals. So
 *
 * <pre>
 *   P(y=b) = E[ sigma(eta) ] = (1/3) * sum_r integral phi(x1)phi(x2)phi(x3) sigma(eta) dx
 * </pre>
 *
 * which is a smooth 3-D Gaussian-weighted integral — computed here on a fine grid to far more precision
 * than the engine's discretisation error, so it serves as the oracle.
 *
 * <p>Three continuous covariates rather than one because the pre-pass only expands a logit whose
 * estimated clique exceeds its cost threshold; one covariate leaves the logit too cheap to touch, and
 * this test deliberately goes through the real {@code calculate()} path, gate included, rather than
 * calling the factoriser directly.
 */
public class IndicatorAccuracyOracleTest {

  private static final String[] REGIONS = { "south", "middle", "north" };
  private static final double B0 = 0.7;
  private static final double BIND = 1.3;
  private static final double[] BX = { 0.4, 0.3, 0.2 };

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

  private static uk.co.agena.minerva.model.Model build() throws Exception {
    uk.co.agena.minerva.model.Model model = uk.co.agena.minerva.model.Model.createEmptyModel();
    ExtendedBN ebn = model.getExtendedBNAtIndex(0);
    LabelledEN region = labelled(ebn, "region", REGIONS);
    LabelledEN y = labelled(ebn, "y", "a", "b");
    region.addChild(y);

    StringBuilder eta = new StringBuilder(B0 + " + " + BIND + " * Indicator(region, \"middle\")");
    for (int i = 0; i < BX.length; i++) {
      String id = "x" + (i + 1);
      ContinuousIntervalEN x = ebn.addContinuousIntervalNode(id, id);
      x.setSimulationNode(true);
      x.setDynamicallyDiscretisable(true);
      setFn(x, "Normal", "0.0", "1.0");
      x.addChild(y);
      eta.append(" + ").append(BX[i]).append(" * ").append(id);
    }
    setFn(y, "MultinomialLogit", eta.toString());
    return model;
  }

  /**
   * P(y = "b"), the non-reference category. Read from the MarginalDataStore rather than
   * ExtendedNode.getMarginals(), which answers with the node's uniform prior here and so silently
   * reports 0.5 for everything.
   */
  private static double pB(uk.co.agena.minerva.model.Model model) throws Exception {
    ExtendedBN ebn = model.getExtendedBNAtIndex(0);
    ExtendedNode y = ebn.getExtendedNodeWithUniqueIdentifier("y");
    DataSet ds = model.getMarginalDataStore()
        .getMarginalDataItemListForNode(ebn, y)
        .getMarginalDataItemAtIndex(0)
        .getDataset();
    return ds.getDataPointAtOrderPosition(1).getValue();
  }

  /**
   * The oracle. Trapezoid over [-8,8] per covariate: the integrand is a product of a Gaussian density
   * and a bounded smooth sigmoid, so convergence is fast and h=0.1 is already far below the engine's
   * discretisation error — which is the only thing this needs to out-resolve.
   */
  private static double oracle() {
    final int n = 161;
    final double lo = -8.0, hi = 8.0, h = (hi - lo) / (n - 1);
    double[] node = new double[n];
    double[] w = new double[n];
    double wsum = 0;
    for (int i = 0; i < n; i++) {
      node[i] = lo + i * h;
      double density = Math.exp(-0.5 * node[i] * node[i]) / Math.sqrt(2 * Math.PI);
      w[i] = density * ((i == 0 || i == n - 1) ? 0.5 : 1.0) * h;
      wsum += w[i];
    }
    // Renormalise so the truncated grid integrates to exactly 1 per dimension: this removes the tail
    // truncation and the trapezoid's own mass error from the comparison.
    for (int i = 0; i < n; i++) {
      w[i] /= wsum;
    }

    double total = 0;
    for (int r = 0; r < REGIONS.length; r++) {
      double base = B0 + (r == 1 ? BIND : 0.0);
      for (int i = 0; i < n; i++) {
        double e1 = base + BX[0] * node[i];
        for (int j = 0; j < n; j++) {
          double e2 = e1 + BX[1] * node[j];
          double wij = w[i] * w[j];
          for (int k = 0; k < n; k++) {
            double eta = e2 + BX[2] * node[k];
            total += wij * w[k] * (1.0 / (1.0 + Math.exp(-eta)));
          }
        }
      }
    }
    return total / REGIONS.length;
  }

  @Test
  public void exactDummyStatesBeatTheOldContinuousEncoding() throws Exception {
    double exactOracle = oracle();

    boolean saved = MultinomialLogitFactoriser.EXACT_INDICATOR_STATES;
    double pExact;
    double pLegacy;
    try {
      MultinomialLogitFactoriser.EXACT_INDICATOR_STATES = true;
      uk.co.agena.minerva.model.Model a = build();
      a.calculate();
      pExact = pB(a);

      MultinomialLogitFactoriser.EXACT_INDICATOR_STATES = false;
      uk.co.agena.minerva.model.Model b = build();
      b.calculate();
      pLegacy = pB(b);
    } finally {
      MultinomialLogitFactoriser.EXACT_INDICATOR_STATES = saved;
    }

    double errExact = Math.abs(pExact - exactOracle);
    double errLegacy = Math.abs(pLegacy - exactOracle);
    System.out.printf("ORACLE   P(y=b) = %.8f%n", Double.valueOf(exactOracle));
    System.out.printf("EXACT    P(y=b) = %.8f   err = %.3e%n",
        Double.valueOf(pExact), Double.valueOf(errExact));
    System.out.printf("LEGACY   P(y=b) = %.8f   err = %.3e%n",
        Double.valueOf(pLegacy), Double.valueOf(errLegacy));
    System.out.printf("IMPROVEMENT factor = %.2fx%n", Double.valueOf(errLegacy / errExact));

    assertTrue(errExact < errLegacy,
        "the exact dummy encoding should be closer to the closed form: exact err " + errExact
            + " vs legacy err " + errLegacy);
    // Sanity: the exact encoding should be genuinely close, not merely less wrong. Loose enough to
    // tolerate DD's discretisation of the three covariates and the intermediate score.
    assertEquals(exactOracle, pExact, 5e-3,
        "exact-dummy result is too far from the closed form to be explained by discretisation alone");
  }
}
