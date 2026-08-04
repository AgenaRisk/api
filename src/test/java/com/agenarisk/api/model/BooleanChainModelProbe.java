package com.agenarisk.api.model;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import uk.co.agena.minerva.model.extendedbn.ExtendedBN;
import uk.co.agena.minerva.model.extendedbn.ExtendedNode;
import uk.co.agena.minerva.util.model.DataPoint;

/**
 * End-to-end check on the boolean test models, through CMPX → api → engine.
 *
 * <p>Two questions the unit tests cannot answer, because they build models programmatically rather than
 * reading the file the user actually opens:</p>
 * <ol>
 *   <li>does every node in the saved model calculate at all — after the repairs to `g_mixedops` (a mixed
 *       spine that a stale boolean stash had flattened to `or(...)`) and `g_or` (mode/stash cleanup);</li>
 *   <li>is the boolean factorisation still <b>exact</b> on it — every node's marginal identical with
 *       {@code BOOLEAN_FACTORIZATION} off and on. That is the whole claim of the design, and this model
 *       exists to test it, so a repair that changed a marginal would be a repair that broke the case.</li>
 * </ol>
 *
 * <p>Manually invoked (loads from the user's Desktop, so it must not run in a normal build):
 * {@code mvn -o test -Dtest=BooleanChainModelProbe -Dagena.probe.run=true}</p>
 */
public class BooleanChainModelProbe {

  private static final String DIR = "C:/Users/marti/Desktop/test cases/";
  private static final String[] FILES = {
    "Factorisation chain BOOLEAN.cmpx",
    "Boolean functions SMALL.cmpx",
  };

  /**
   * Marginals come back as floats, and the two paths accumulate differently: OFF marginalises a 12-parent
   * clique — 4096 float columns — while ON folds a chain of 8-cell families. On this model that showed up as
   * ~1.6e-6 on the OR node's parents, and in the direction that says the factorised path is the ACCURATE
   * one: s23's declared prior is exactly 0.36 and s24's exactly 0.25, and ON returns those while OFF drifts
   * to 0.359998971 and 0.249998376. So the tolerance is float-accumulation slack, not a claim about the
   * decomposition, which is exact by construction (see DD_BOOLEAN_FACTORISATION §2).
   */
  private static final double DELTA = 1e-5;

  /** Root nodes with a declared prior, so the check can say WHICH path is closer to the exact answer. */
  private static final String[][] ROOT_PRIORS = {
    {"Factorisation chain BOOLEAN.cmpx", "Boolean factorisation chain/s23", "0.36"},
    {"Factorisation chain BOOLEAN.cmpx", "Boolean factorisation chain/s24", "0.25"},
  };

  @Test
  public void offEqualsOnOnEveryNodeOfTheSavedModels() throws Exception {
    if (!"true".equals(System.getProperty("agena.probe.run"))) {
      return; // inert during a normal build — it reads files outside the repo
    }
    List<String> failures = new ArrayList<String>();
    for (String file : FILES) {
      System.out.println("=== " + file);
      Map<String, double[]> off = run(DIR + file, false);
      Map<String, double[]> on = run(DIR + file, true);

      if (off == null || on == null) {
        failures.add(file + ": propagation failed (off=" + (off != null) + " on=" + (on != null) + ")");
        continue;
      }
      // Every node present OFF must be present ON with the same distribution. ON also adds the synthesised
      // bool_* nodes; those are expected and simply not compared.
      int compared = 0;
      double worst = 0;
      String worstAt = "-";
      for (Map.Entry<String, double[]> e : off.entrySet()) {
        double[] a = e.getValue();
        double[] b = on.get(e.getKey());
        if (b == null) {
          failures.add(file + ": " + e.getKey() + " has no result with factorisation ON");
          continue;
        }
        if (a.length != b.length) {
          failures.add(file + ": " + e.getKey() + " state count " + a.length + " -> " + b.length);
          continue;
        }
        for (int i = 0; i < a.length; i++) {
          double d = Math.abs(a[i] - b[i]);
          if (d > worst) {
            worst = d;
            worstAt = e.getKey() + " state " + i;
          }
          if (d > DELTA) {
            failures.add(String.format("%s: %s state %d  off=%.9f on=%.9f", file, e.getKey(), i, a[i], b[i]));
          }
        }
        compared++;
      }
      // Print the worst deviation rather than only whether it cleared the threshold — a tolerance that
      // hides its own margin is how a drift gets to grow unnoticed.
      System.out.println(String.format("  nodes compared: %d   worst |off-on| = %.3e at %s",
          compared, worst, worstAt));

      // Where a root node's prior is declared, say which path reproduces it. This is what turns the
      // residual from "within tolerance" into "the factorised path is the accurate one".
      for (String[] rp : ROOT_PRIORS) {
        if (!rp[0].equals(file)) {
          continue;
        }
        double[] a = off.get(rp[1]);
        double[] b = on.get(rp[1]);
        if (a == null || b == null) {
          continue;
        }
        double exact = Double.parseDouble(rp[2]);
        System.out.println(String.format("  %s prior=%.2f  off err=%.3e  on err=%.3e%s",
            rp[1], exact, Math.abs(a[0] - exact), Math.abs(b[0] - exact),
            Math.abs(b[0] - exact) <= Math.abs(a[0] - exact) ? "   <- ON at least as exact" : "   <- ON WORSE"));
        if (Math.abs(b[0] - exact) > Math.abs(a[0] - exact)) {
          failures.add(file + ": " + rp[1] + " is LESS exact with factorisation on");
        }
      }
    }
    if (!failures.isEmpty()) {
      StringBuilder sb = new StringBuilder("OFF vs ON disagreed:\n");
      for (String f : failures) {
        sb.append("  ").append(f).append('\n');
      }
      throw new AssertionError(sb.toString());
    }
    System.out.println("OK: every node identical with factorisation off and on");
  }

  /** Load, calculate, and collect every node's full distribution by connNodeId. Null if propagation fails. */
  private static Map<String, double[]> run(String path, boolean factorise) throws Exception {
    boolean saved = uk.co.agena.minerva.model.Model.BOOLEAN_FACTORIZATION;
    uk.co.agena.minerva.model.Model.BOOLEAN_FACTORIZATION = factorise;
    try {
      Model m = Model.loadModel(path);
      uk.co.agena.minerva.model.Model lm = m.getLogicModel();
      m.calculate();
      if (!lm.isLastPropagationSuccessful()) {
        System.out.println("  PROPAGATION FAILED with factorisation " + (factorise ? "ON" : "OFF"));
        return null;
      }
      Map<String, double[]> out = new LinkedHashMap<String, double[]>();
      for (Object o : lm.getExtendedBNList().getExtendedBNs()) {
        ExtendedBN ebn = (ExtendedBN) o;
        for (Object n : ebn.getExtendedNodes()) {
          ExtendedNode en = (ExtendedNode) n;
          try {
            List dps = lm.getMarginalDataStore().getMarginalDataItemListForNode(ebn, en)
                .getMarginalDataItemAtIndex(0).getDataset().getDataPoints();
            double[] v = new double[dps.size()];
            for (int i = 0; i < dps.size(); i++) {
              v[i] = ((DataPoint) dps.get(i)).getValue();
            }
            out.put(ebn.getConnID() + "/" + en.getConnNodeId(), v);
          } catch (Throwable t) {
            System.out.println("  NO RESULT " + en.getConnNodeId() + " (" + t.getClass().getSimpleName() + ")");
          }
        }
      }
      return out;
    } finally {
      uk.co.agena.minerva.model.Model.BOOLEAN_FACTORIZATION = saved;
    }
  }
}
