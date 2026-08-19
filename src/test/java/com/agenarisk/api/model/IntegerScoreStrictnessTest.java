package com.agenarisk.api.model;

import java.util.Arrays;
import org.junit.jupiter.api.Test;
import uk.co.agena.minerva.model.extendedbn.BooleanEN;
import uk.co.agena.minerva.model.extendedbn.ExtendedBN;
import uk.co.agena.minerva.model.extendedbn.ExtendedNode;
import uk.co.agena.minerva.model.extendedbn.ExtendedNodeFunction;
import uk.co.agena.minerva.model.extendedbn.IntegerIntervalEN;

/**
 * A comparative over two INTEGER parents: {@code if(a - b > 0, "True", "False")}.
 *
 * <p>The score a-b is integer valued, so P(score = 0) is a genuine atom rather than measure zero. The
 * comparative factorisation creates its score node as a continuous simulation node and
 * ComparativeConditionParser.strictAgainst collapses >= into > on the stated grounds that equality is
 * "a measure-zero test" — true for a continuous score, false here. If the atom at 0 lands on the wrong
 * side of the pinned boundary, P(True) is overstated by exactly P(a = b).
 *
 * <p>Closed form with a,b ~ Binomial(4, 0.5) independent: p = [1,4,6,4,1]/16, so
 * P(a=b) = sum p_k^2 = 70/256 = 0.2734375 and, by symmetry, P(a>b) = (1 - P(a=b))/2 = 0.36328125.
 * Those two numbers are far enough apart that any misclassification of the atom is unmistakable.
 */
public class IntegerScoreStrictnessTest {

  private static final double P_EQUAL = 70.0 / 256.0;         // 0.2734375
  private static final double P_A_GT_B = (1.0 - P_EQUAL) / 2;  // 0.36328125

  private static void setFn(ExtendedNode n, String fn, String... params) throws Exception {
    n.setFunctionMode(ExtendedNode.EDITABLE_NODE_FUNCTION);
    n.setCurrentNodeFunction(
        new ExtendedNodeFunction(fn, Arrays.asList(params), ExtendedNodeFunction.CURRENT_TYPE));
  }

  private static IntegerIntervalEN binomialInt(ExtendedBN ebn, String id) throws Exception {
    IntegerIntervalEN n = ebn.addIntegerIntervalNode(id, id);
    n.setSimulationNode(true);
    n.setDynamicallyDiscretisable(true);
    setFn(n, "Binomial", "4", "0.5");
    return n;
  }

  /** @param op the comparison operator to put in the condition */
  private static uk.co.agena.minerva.model.Model build(String op) throws Exception {
    uk.co.agena.minerva.model.Model model = uk.co.agena.minerva.model.Model.createEmptyModel();
    ExtendedBN ebn = model.getExtendedBNAtIndex(0);
    IntegerIntervalEN a = binomialInt(ebn, "a");
    IntegerIntervalEN b = binomialInt(ebn, "b");
    BooleanEN y = ebn.addBooleanNode("y", "y");
    a.addChild(y);
    b.addChild(y);
    setFn(y, "Comparative", "if(a - b " + op + " 0, \"True\", \"False\")");
    return model;
  }

  private static double pTrue(uk.co.agena.minerva.model.Model model) throws Exception {
    ExtendedBN ebn = model.getExtendedBNAtIndex(0);
    ExtendedNode y = ebn.getExtendedNodeWithUniqueIdentifier("y");
    return model.getMarginalDataStore()
        .getMarginalDataItemListForNode(ebn, y)
        .getMarginalDataItemAtIndex(0)
        .getDataset()
        .getDataPointAtOrderPosition(1)
        .getValue();
  }

  private static double run(String op, boolean factorise, boolean integerScores) throws Exception {
    boolean savedF = uk.co.agena.minerva.model.Model.COMPARATIVE_FACTORIZATION;
    boolean savedI = uk.co.agena.minerva.util.binaryfactorisation.ComparativeFactoriser
        .INTEGER_SCORE_NODES;
    try {
      uk.co.agena.minerva.model.Model.COMPARATIVE_FACTORIZATION = factorise;
      uk.co.agena.minerva.util.binaryfactorisation.ComparativeFactoriser.INTEGER_SCORE_NODES =
          integerScores;
      uk.co.agena.minerva.model.Model m = build(op);
      m.calculate();
      return pTrue(m);
    } finally {
      uk.co.agena.minerva.model.Model.COMPARATIVE_FACTORIZATION = savedF;
      uk.co.agena.minerva.util.binaryfactorisation.ComparativeFactoriser.INTEGER_SCORE_NODES =
          savedI;
    }
  }

  private static void measure(String op, double expected) throws Exception {
    double pOff = run(op, false, false);
    double pCont = run(op, true, false); // factorised, score node kept continuous
    double pInt = run(op, true, true);   // factorised, score node declared integer
    System.out.printf(
        "ATOM op=%-3s exact=%.8f  OFF=%.8f (%+.3e)  ON/cont=%.8f (%+.3e)  ON/int=%.8f (%+.3e)%n",
        op, Double.valueOf(expected),
        Double.valueOf(pOff), Double.valueOf(pOff - expected),
        Double.valueOf(pCont), Double.valueOf(pCont - expected),
        Double.valueOf(pInt), Double.valueOf(pInt - expected));

    org.junit.jupiter.api.Assertions.assertEquals(expected, pOff, 1e-6,
        "unfactorised " + op + " should match the closed form");
    // The regression this pins: strictAgainstZero used to collapse >= into >, discarding P(a=b).
    org.junit.jupiter.api.Assertions.assertEquals(expected, pCont, 1e-6,
        "factorised " + op + " (continuous score node) should match the closed form — a non-strict "
            + "operator collapsed to a strict one loses the whole atom at the threshold");
    org.junit.jupiter.api.Assertions.assertEquals(expected, pInt, 1e-6,
        "factorised " + op + " (integer score node) should match the closed form");
  }

  /**
   * The mixed case: one integer parent, one continuous. The score x - a is then a mixture of shifted
   * continuous densities, so P(score = 0) = 0 — there is no atom for a boundary to absorb, and > and >=
   * must agree. Exact: P(x > a) = sum_k p_k * (1 - Phi(k)).
   */
  private static uk.co.agena.minerva.model.Model buildMixed(String op) throws Exception {
    uk.co.agena.minerva.model.Model model = uk.co.agena.minerva.model.Model.createEmptyModel();
    ExtendedBN ebn = model.getExtendedBNAtIndex(0);
    IntegerIntervalEN a = binomialInt(ebn, "a");
    uk.co.agena.minerva.model.extendedbn.ContinuousIntervalEN x =
        ebn.addContinuousIntervalNode("x", "x");
    x.setSimulationNode(true);
    x.setDynamicallyDiscretisable(true);
    setFn(x, "Normal", "2.0", "4.0");
    BooleanEN y = ebn.addBooleanNode("y", "y");
    a.addChild(y);
    x.addChild(y);
    setFn(y, "Comparative", "if(x - a " + op + " 0, \"True\", \"False\")");
    return model;
  }

  private static double mixedExact() {
    double[] p = { 1 / 16.0, 4 / 16.0, 6 / 16.0, 4 / 16.0, 1 / 16.0 };
    double total = 0;
    for (int k = 0; k < p.length; k++) {
      // P(x > k) for x ~ Normal(mean 2, variance 4) => sd 2
      total += p[k] * 0.5 * erfc((k - 2.0) / (2.0 * Math.sqrt(2.0)));
    }
    return total;
  }

  /** Abramowitz & Stegun 7.1.26-style rational approximation; ~1e-7, ample for this comparison. */
  private static double erfc(double z) {
    double t = 1.0 / (1.0 + 0.5 * Math.abs(z));
    double ans = t * Math.exp(-z * z - 1.26551223 + t * (1.00002368 + t * (0.37409196
        + t * (0.09678418 + t * (-0.18628806 + t * (0.27886807 + t * (-1.13520398
        + t * (1.48851587 + t * (-0.82215223 + t * 0.17087277)))))))));
    return z >= 0 ? ans : 2.0 - ans;
  }

  private static double measureMixed(String op, double expected) {
    double result = Double.NaN;
    boolean saved = uk.co.agena.minerva.model.Model.COMPARATIVE_FACTORIZATION;
    try {
      uk.co.agena.minerva.model.Model.COMPARATIVE_FACTORIZATION = false;
      uk.co.agena.minerva.model.Model off = buildMixed(op);
      off.calculate();
      double pOff = pTrue(off);

      uk.co.agena.minerva.model.Model.COMPARATIVE_FACTORIZATION = true;
      uk.co.agena.minerva.model.Model on = buildMixed(op);
      on.calculate();
      double pOn = pTrue(on);

      System.out.printf("MIXED op=%-3s exact=%.8f  OFF=%.8f (err %+.3e)  ON=%.8f (err %+.3e)%n",
          op, Double.valueOf(expected),
          Double.valueOf(pOff), Double.valueOf(pOff - expected),
          Double.valueOf(pOn), Double.valueOf(pOn - expected));
      result = pOn;
    } catch (Throwable t) {
      System.out.println("MIXED op=" + op + " FAILED " + t.getClass().getSimpleName() + ": "
          + t.getMessage());
    } finally {
      uk.co.agena.minerva.model.Model.COMPARATIVE_FACTORIZATION = saved;
    }
    return result;
  }

  /**
   * A WIDE integer range with a NON-ZERO threshold. This is where declaring the score node integer
   * could backfire: DynDiscPropagation.insertMustSplitValues deliberately skips IntegerIntervalEN ("a
   * pinned real boundary buys nothing and could land mid-integer"), so an integer score node does not
   * get its threshold pinned. If DD then puts a multi-integer state astride the threshold, the hit count
   * within that state is an approximation — whereas a continuous score node has the boundary pinned and
   * is exact for any monotone test regardless of bin width.
   *
   * <p>a,b ~ Binomial(20, 0.5) independent, so a-b+20 ~ Binomial(40, 0.5) and
   * P(a-b >= t) = sum_{d>=t} C(40, 20+d) / 2^40 exactly.
   */
  private static uk.co.agena.minerva.model.Model buildWide(String op, int threshold)
      throws Exception {
    uk.co.agena.minerva.model.Model model = uk.co.agena.minerva.model.Model.createEmptyModel();
    ExtendedBN ebn = model.getExtendedBNAtIndex(0);
    for (String id : new String[] { "a", "b" }) {
      IntegerIntervalEN n = ebn.addIntegerIntervalNode(id, id);
      n.setSimulationNode(true);
      n.setDynamicallyDiscretisable(true);
      setFn(n, "Binomial", "20", "0.5");
    }
    BooleanEN y = ebn.addBooleanNode("y", "y");
    ebn.getExtendedNodeWithUniqueIdentifier("a").addChild(y);
    ebn.getExtendedNodeWithUniqueIdentifier("b").addChild(y);
    setFn(y, "Comparative", "if(a - b " + op + " " + threshold + ", \"True\", \"False\")");
    return model;
  }

  private static double wideExact(int threshold, boolean strict) {
    int twoN = 40, n = 20;
    double[] c = new double[twoN + 1];
    c[0] = 1;
    for (int i = 1; i <= twoN; i++) {
      for (int j = i; j >= 1; j--) {
        c[j] = c[j] + c[j - 1];
      }
    }
    double denom = Math.pow(2, twoN);
    double total = 0;
    for (int d = strict ? threshold + 1 : threshold; d <= n; d++) {
      total += c[n + d] / denom;
    }
    return total;
  }

  private static void measureWide(String op, int threshold, double expected) {
    boolean savedF = uk.co.agena.minerva.model.Model.COMPARATIVE_FACTORIZATION;
    boolean savedI = uk.co.agena.minerva.util.binaryfactorisation.ComparativeFactoriser
        .INTEGER_SCORE_NODES;
    try {
      double[] got = new double[3];
      boolean[][] cfg = { { false, false }, { true, false }, { true, true } };
      for (int i = 0; i < 3; i++) {
        uk.co.agena.minerva.model.Model.COMPARATIVE_FACTORIZATION = cfg[i][0];
        uk.co.agena.minerva.util.binaryfactorisation.ComparativeFactoriser.INTEGER_SCORE_NODES =
            cfg[i][1];
        uk.co.agena.minerva.model.Model m = buildWide(op, threshold);
        m.calculate();
        got[i] = pTrue(m);
      }
      System.out.printf(
          "WIDE  a-b %s %d  exact=%.8f  OFF=%.8f (%+.3e)  ON/cont=%.8f (%+.3e)  ON/int=%.8f (%+.3e)%n",
          op, Integer.valueOf(threshold), Double.valueOf(expected),
          Double.valueOf(got[0]), Double.valueOf(got[0] - expected),
          Double.valueOf(got[1]), Double.valueOf(got[1] - expected),
          Double.valueOf(got[2]), Double.valueOf(got[2] - expected));
      for (int i = 0; i < 3; i++) {
        org.junit.jupiter.api.Assertions.assertEquals(expected, got[i], 1e-6,
            "wide-range a-b " + op + " " + threshold + " config " + i + " (0=off, 1=continuous score, "
                + "2=integer score) should match the closed form");
      }
    } catch (Throwable t) {
      throw new AssertionError("WIDE " + op + " " + threshold + " failed", t);
    } finally {
      uk.co.agena.minerva.model.Model.COMPARATIVE_FACTORIZATION = savedF;
      uk.co.agena.minerva.util.binaryfactorisation.ComparativeFactoriser.INTEGER_SCORE_NODES =
          savedI;
    }
  }

  @Test
  public void probe() throws Exception {
    System.out.printf("ATOM P(a=b) = %.8f  P(a>b) = %.8f  P(a>=b) = %.8f%n",
        Double.valueOf(P_EQUAL), Double.valueOf(P_A_GT_B),
        Double.valueOf(P_A_GT_B + P_EQUAL));
    // > excludes the atom, >= includes it. A score node that cannot represent the atom must get at
    // least one of these wrong, and the difference between them is P(a=b) = 0.273.
    measure(">", P_A_GT_B);
    measure(">=", P_A_GT_B + P_EQUAL);
    // < / <= must behave symmetrically: P(a<b) = P(a>b), P(a<=b) = P(a>b) + P(a=b).
    measure("<", P_A_GT_B);
    measure("<=", P_A_GT_B + P_EQUAL);

    // Mixed: no atom, so > and >= should both be right and should agree.
    double mx = mixedExact();
    System.out.printf("MIXED exact P(x>a) = %.8f (no atom: > and >= coincide)%n", Double.valueOf(mx));
    double mixedStrict = measureMixed(">", mx);
    double mixedNonStrict = measureMixed(">=", mx);
    // Mixed = one integer parent, one continuous. The continuous component makes P(score = 0) = 0, so
    // there is no atom for a boundary to absorb and the two operators must agree exactly. This is why
    // the mixed case needs no special handling.
    org.junit.jupiter.api.Assertions.assertEquals(mixedStrict, mixedNonStrict, 1e-12,
        "with a continuous component there is no atom at the threshold, so > and >= must coincide");
    org.junit.jupiter.api.Assertions.assertEquals(mx, mixedStrict, 1e-3,
        "mixed case should track the closed form to within DD discretisation");

    // Wide integer range, non-zero threshold: does integer typing (unpinned threshold) degrade?
    measureWide(">=", 7, wideExact(7, false));
    measureWide(">", 7, wideExact(7, true));
    measureWide(">=", 0, wideExact(0, false));
  }
}
