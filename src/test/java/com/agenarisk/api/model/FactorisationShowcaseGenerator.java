package com.agenarisk.api.model;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import uk.co.agena.minerva.model.extendedbn.BooleanEN;
import uk.co.agena.minerva.model.extendedbn.ContinuousIntervalEN;
import uk.co.agena.minerva.model.extendedbn.ExtendedBN;
import uk.co.agena.minerva.model.extendedbn.ExtendedNode;
import uk.co.agena.minerva.model.extendedbn.ExtendedNodeFunction;
import uk.co.agena.minerva.model.extendedbn.ExtendedState;
import uk.co.agena.minerva.model.extendedbn.LabelledEN;
import uk.co.agena.minerva.util.binaryfactorisation.BinaryBNConverter;
import uk.co.agena.minerva.util.binaryfactorisation.ComparativeFactoriser;
import uk.co.agena.minerva.util.binaryfactorisation.MultinomialLogitFactoriser;
import uk.co.agena.minerva.util.model.DataSet;

/**
 * ONE fully joined-up network containing every factorisation form, sized to stay computable.
 *
 * <pre>
 *   c1..c10 ─┬─► sub_a  = c1+c2+c3+c4          BF
 *            ├─► sub_b  = c4*c5+c6             BF
 *            └─► sub_c  = c7+c8+c9+c10         BF
 *                   └──► total  = sub_a+sub_b+sub_c   BF (second level)
 *
 *   sub_a, sub_b, region, urban      ──► severity_a   MultinomialLogit  (K=3)
 *   sub_c, total, sector             ──► severity_b   MultinomialLogit  (K=3)
 *   total, urban, severity_a         ──► escalation   MultinomialLogit  PARTITIONED
 *
 *   sub_a, sub_b, lim_a, lim_b       ──► capacity_ok  Comparative  4-way diagonal
 *   total, lim_c                     ──► in_band      Comparative  two-sided band -> 1 score, 2 pins
 *   total, lim_d, severity_b         ──► breach       Comparative  PARTITIONED
 *
 *   capacity_ok, in_band, breach, escalation ──► verdict (manual NPT, 3 states)
 * </pre>
 *
 * <p>Three sizing rules keep it feasible, all learned the hard way:</p>
 * <ol>
 *   <li>No two wide arithmetic nodes share their whole parent set — {@code sub_a} and {@code sub_b}
 *       overlap on {@code c4} alone. Two 7-way nodes over the same seven leaves moralise into a graph BF
 *       cannot decouple, and the junction tree stays enormous however well each expression is binarised.</li>
 *   <li>Each logit predictor uses a DIFFERENT continuous covariate, so the K-1 score nodes do not share
 *       continuous parents. Sharing them couples the scores through triangulation, which is what makes a
 *       K=4 logit over four shared covariates fail to compile at all.</li>
 *   <li>The partitioned logit has ONE continuous covariate. With two, the partitioned score node becomes
 *       BF-eligible itself and gets nested, inflating a clique into the hundreds of millions.</li>
 * </ol>
 *
 * <p>Run: {@code mvn -o test -Dtest=FactorisationShowcaseGenerator -Dgen.run=true}</p>
 */
public class FactorisationShowcaseGenerator {

  private static final String OUT_DIR = System.getProperty("gen.dir", "C:/Users/marti/Desktop");

  private static ContinuousIntervalEN normal(ExtendedBN ebn, String id, String name,
      double mean, double var) throws Exception {
    ContinuousIntervalEN n = ebn.addContinuousIntervalNode(id, name);
    n.setSimulationNode(true);
    n.setDynamicallyDiscretisable(true);
    setFn(n, "Normal", String.valueOf(mean), String.valueOf(var));
    return n;
  }

  private static ContinuousIntervalEN arith(ExtendedBN ebn, String id, String name, String expr)
      throws Exception {
    ContinuousIntervalEN n = ebn.addContinuousIntervalNode(id, name);
    n.setSimulationNode(true);
    n.setDynamicallyDiscretisable(true);
    setFn(n, "Arithmetic", expr);
    return n;
  }

  private static void setFn(ExtendedNode n, String fn, String... params) throws Exception {
    n.setFunctionMode(ExtendedNode.EDITABLE_NODE_FUNCTION);
    n.setCurrentNodeFunction(new ExtendedNodeFunction(fn, Arrays.asList(params),
        ExtendedNodeFunction.CURRENT_TYPE));
  }

  private static LabelledEN labelled(ExtendedBN ebn, String id, String name, String... states)
      throws Exception {
    LabelledEN n = ebn.addLabelledNode(id, name);
    DataSet ds = new DataSet();
    for (String s : states) {
      ds.addLabelledDataPoint(s);
    }
    n.createExtendedStates(ds);
    return n;
  }

  private static String st(ExtendedNode n, int i) {
    return ((ExtendedState) n.getExtendedStates().get(i)).getName().getShortDescription();
  }

  private static void link(ExtendedBN ebn, String parent, String child) throws Exception {
    ebn.getExtendedNodeWithUniqueIdentifier(parent)
        .addChild(ebn.getExtendedNodeWithUniqueIdentifier(child));
  }

  private static uk.co.agena.minerva.model.Model build() throws Exception {
    uk.co.agena.minerva.model.Model model = uk.co.agena.minerva.model.Model.createEmptyModel();
    ExtendedBN ebn = model.getExtendedBNAtIndex(0);
    ebn.getName().setShortDescription("Factorisation showcase");

    // ---- leaves --------------------------------------------------------------------------------
    for (int i = 1; i <= 10; i++) {
      normal(ebn, "c" + i, "Contributor " + i, 8.0 + i * 2.0, 3.0 + i * 0.4);
    }

    // ---- BF: a two-level cascade of arithmetic nodes -------------------------------------------
    arith(ebn, "sub_a", "Subtotal A (4-way sum)", "c1 + c2 + c3 + c4");
    arith(ebn, "sub_b", "Subtotal B (product + sum)", "c4 * c5 + c6");
    arith(ebn, "sub_c", "Subtotal C (4-way sum)", "c7 + c8 + c9 + c10");
    for (int i = 1; i <= 4; i++) {
      link(ebn, "c" + i, "sub_a");
    }
    for (int i = 4; i <= 6; i++) {
      link(ebn, "c" + i, "sub_b");
    }
    for (int i = 7; i <= 10; i++) {
      link(ebn, "c" + i, "sub_c");
    }
    arith(ebn, "total", "Grand total (BF over BF outputs)", "sub_a + sub_b + sub_c");
    for (String p : new String[]{"sub_a", "sub_b", "sub_c"}) {
      link(ebn, p, "total");
    }

    // ---- categoricals --------------------------------------------------------------------------
    LabelledEN region = labelled(ebn, "region", "Region", "North", "Middle", "South");
    BooleanEN urban = ebn.addBooleanNode("urban", "Urban site");
    LabelledEN sector = labelled(ebn, "sector", "Sector", "Public", "Private", "Mixed");

    // ---- MULTINOMIAL LOGIT 1: predictors over DISJOINT covariates ------------------------------
    LabelledEN sevA = labelled(ebn, "severity_a", "Severity A (logit, 3 states)",
        "Negligible", "Moderate", "Severe");
    for (String p : new String[]{"sub_a", "sub_b", "region", "urban"}) {
      link(ebn, p, "severity_a");
    }
    setFn(sevA, "MultinomialLogit",
        "0.30 + 0.020*sub_a + 0.40*Indicator(region,\"" + st(region, 0) + "\")",
        "-0.50 + 0.015*sub_b + 0.70*Indicator(urban,\"True\")");

    // ---- MULTINOMIAL LOGIT 2 -------------------------------------------------------------------
    LabelledEN sevB = labelled(ebn, "severity_b", "Severity B (logit, 3 states)",
        "Low", "Elevated", "Critical");
    for (String p : new String[]{"sub_c", "total", "sector"}) {
      link(ebn, p, "severity_b");
    }
    setFn(sevB, "MultinomialLogit",
        "0.10 + 0.018*sub_c + 0.55*Indicator(sector,\"" + st(sector, 1) + "\")",
        "-0.80 + 0.008*total + 0.35*Indicator(sector,\"" + st(sector, 2) + "\")");

    // ---- PARTITIONED MULTINOMIAL LOGIT: per-band slope AND intercept ---------------------------
    BooleanEN esc = ebn.addBooleanNode("escalation", "Escalation (logit, partitioned on severity_a)");
    for (String p : new String[]{"total", "urban", "severity_a"}) {
      link(ebn, p, "escalation");
    }
    List<ExtendedNodeFunction> escCells = new ArrayList<ExtendedNodeFunction>();
    for (int c = 0; c < sevA.getExtendedStates().size(); c++) {
      double slope = 0.006 * (c + 1);
      escCells.add(new ExtendedNodeFunction("MultinomialLogit",
          Arrays.asList(new String[]{(-2.0 + c) + " + " + slope + "*total + "
              + (0.3 * (c + 1)) + "*Indicator(urban,\"True\")"}),
          ExtendedNodeFunction.CURRENT_TYPE));
    }
    esc.setPartitionedExpressionModelNodes(Arrays.asList(new ExtendedNode[]{sevA}));
    esc.setPartitionedExpressions(escCells);

    // ---- COMPARATIVE 1: 4-way diagonal ---------------------------------------------------------
    normal(ebn, "lim_a", "Allowance A", 40.0, 9.0);
    normal(ebn, "lim_b", "Allowance B", 45.0, 9.0);
    BooleanEN cap = ebn.addBooleanNode("capacity_ok", "Capacity OK (4-way diagonal)");
    for (String p : new String[]{"sub_a", "sub_b", "lim_a", "lim_b"}) {
      link(ebn, p, "capacity_ok");
    }
    setFn(cap, "Comparative",
        "if(sub_a + sub_b > lim_a + lim_b, \"True\", \"False\")");

    // ---- COMPARATIVE 2: two-sided band -> ONE score, TWO pinned boundaries ---------------------
    normal(ebn, "lim_c", "Band centre", 200.0, 30.0);
    BooleanEN band = ebn.addBooleanNode("in_band", "Within band (two-sided)");
    for (String p : new String[]{"total", "lim_c"}) {
      link(ebn, p, "in_band");
    }
    setFn(band, "Comparative",
        "if(total > lim_c - 5 && total < lim_c + 20, \"True\", \"False\")");

    // ---- COMPARATIVE 3: partitioned, cells of different shapes ---------------------------------
    normal(ebn, "lim_d", "Breach limit", 210.0, 25.0);
    BooleanEN breach = ebn.addBooleanNode("breach", "Breach (comparative, partitioned on severity_b)");
    for (String p : new String[]{"total", "lim_d", "severity_b"}) {
      link(ebn, p, "breach");
    }
    breach.setPartitionedExpressionModelNodes(Arrays.asList(new ExtendedNode[]{sevB}));
    breach.setPartitionedExpressions(Arrays.asList(new ExtendedNodeFunction[]{
      new ExtendedNodeFunction("Comparative", Arrays.asList(new String[]{"\"False\""}),
          ExtendedNodeFunction.CURRENT_TYPE),
      new ExtendedNodeFunction("Comparative",
          Arrays.asList(new String[]{"if(total > lim_d, \"True\", \"False\")"}),
          ExtendedNodeFunction.CURRENT_TYPE),
      new ExtendedNodeFunction("Comparative",
          Arrays.asList(new String[]{"if(total > lim_d - 10, \"True\", \"False\")"}),
          ExtendedNodeFunction.CURRENT_TYPE)}));

    // ---- discrete sink joining all four downstream nodes --------------------------------------
    LabelledEN verdict = labelled(ebn, "verdict", "Verdict", "Accept", "Review", "Reject");
    for (String p : new String[]{"capacity_ok", "in_band", "breach", "escalation"}) {
      link(ebn, p, "verdict");
    }
    // [childState][parentCombination]: 3 x (2*2*2*2) = 3 x 16
    double[][] npt = new double[3][16];
    for (int col = 0; col < 16; col++) {
      // Must stay a valid distribution in EVERY column: accept + review <= 1 for all 16 combinations.
      double accept = 0.08 + 0.030 * col;   // 0.08 .. 0.53
      double review = 0.25;
      npt[0][col] = accept;
      npt[1][col] = review;
      npt[2][col] = 1.0 - accept - review;  // 0.67 .. 0.22, always positive
    }
    verdict.setNPT(npt, Arrays.asList(new ExtendedNode[]{
      ebn.getExtendedNodeWithUniqueIdentifier("capacity_ok"),
      ebn.getExtendedNodeWithUniqueIdentifier("in_band"),
      ebn.getExtendedNodeWithUniqueIdentifier("breach"),
      ebn.getExtendedNodeWithUniqueIdentifier("escalation")}));

    // Size every table against the FINAL parent set (see stripRegenerableTables for why both).
    for (Object o : ebn.getExtendedNodes()) {
      ExtendedNode n = (ExtendedNode) o;
      if (n.getCurrentNodeFunction() != null
          || n.getFunctionMode() == ExtendedNode.EDITABLE_PARENT_STATE_FUNCTIONS) {
        n.setNptReCalcRequired(true);
        try {
          ebn.regenerateNPT(n);
        } catch (Throwable ignored) {
          // the export strips its table anyway
        }
      }
    }
    return model;
  }

  /**
   * Drops every regenerable table from the saved file: an expression node's NPT is a function of its
   * parents' CURRENT discretisation, so a table written before DD runs is stale, and a stale table of the
   * wrong width makes the junction-tree compile throw ArrayIndexOutOfBounds. A manual NPT (here,
   * {@code verdict}) is NOT regenerable and is left alone.
   */
  private static void stripRegenerableTables(String path) throws Exception {
    String json = new String(java.nio.file.Files.readAllBytes(
        java.nio.file.Paths.get(path)), "UTF-8");
    org.json.JSONObject root = new org.json.JSONObject(json);
    org.json.JSONArray nets = root.getJSONObject("model").getJSONArray("networks");
    int stripped = 0;
    for (int i = 0; i < nets.length(); i++) {
      org.json.JSONArray nodes = nets.getJSONObject(i).getJSONArray("nodes");
      for (int j = 0; j < nodes.length(); j++) {
        org.json.JSONObject cfg = nodes.getJSONObject(j).getJSONObject("configuration");
        if (!cfg.has("table")) {
          continue;
        }
        org.json.JSONObject t = cfg.getJSONObject("table");
        String type = t.optString("type", "");
        if (("Expression".equals(type) || "Partitioned".equals(type)) && t.has("probabilities")) {
          t.remove("probabilities");
          t.put("nptCompiled", false);
          stripped++;
        }
      }
    }
    java.nio.file.Files.write(java.nio.file.Paths.get(path), root.toString(2).getBytes("UTF-8"));
    System.out.println("GEN   stripped " + stripped + " regenerable table(s)");
  }

  @Test
  public void generate() throws Exception {
    if (!"true".equals(System.getProperty("gen.run"))) {
      return;
    }

    uk.co.agena.minerva.model.Model original = build();
    boolean exprOk = original.checkExpressions(
        new ArrayList(original.getExtendedBNList().getExtendedBNs()));
    System.out.println("GEN checkExpressions = " + exprOk);
    if (!exprOk) {
      for (Object e : original.getErrors()) {
        System.out.println("GEN   expression error: " + e);
      }
    }
    String p1 = OUT_DIR + "/Factorisation showcase ORIGINAL.cmpx";
    Model.createModel(original).save(p1);
    stripRegenerableTables(p1);
    System.out.println("GEN wrote ORIGINAL nodes=" + count(original)
        + " components=" + components(original));

    uk.co.agena.minerva.model.Model work = build();
    System.out.println("GEN mlogit changed = " + MultinomialLogitFactoriser.factorise(work, true));
    System.out.println("GEN comparative changed = " + ComparativeFactoriser.factorise(work, true));

    uk.co.agena.minerva.model.Model afterBf = work;
    try {
      uk.co.agena.minerva.model.Model copy = uk.co.agena.minerva.model.Model.deepCopyInMemory(work);
      BinaryBNConverter conv = new BinaryBNConverter(work, copy);
      List bns = new ArrayList(work.getExtendedBNList().getExtendedBNs());
      Boolean[] flags = new Boolean[bns.size()];
      Arrays.fill(flags, Boolean.TRUE);
      conv.convertBNList(bns, work, flags);
      uk.co.agena.minerva.model.Model built = conv.getBuiltBinaryModel();
      if (built != null && count(built) > 0) {
        afterBf = built;
      }
    } catch (Throwable t) {
      System.out.println("GEN BF FAILED " + t.getClass().getSimpleName() + ": " + t.getMessage());
    }
    String p2 = OUT_DIR + "/Factorisation showcase FACTORISED.cmpx";
    Model.createModel(afterBf).save(p2);
    stripRegenerableTables(p2);
    System.out.println("GEN wrote FACTORISED nodes=" + count(afterBf)
        + " components=" + components(afterBf));

    // ---- feasibility + calculation, both files ----------------------------------------------
    for (String p : new String[]{p1, p2}) {
      Model m = Model.loadModel(p);
      uk.co.agena.minerva.model.jtinspect.JunctionTreeReport r =
          m.getLogicModel().inspectJunctionTrees(20);
      String calc;
      try {
        m.calculate();
        calc = "calculate OK";
      } catch (Throwable t) {
        calc = "CALC FAILED: " + t.getMessage();
      }
      System.out.printf("GEN %-14s maxClique=%,12.0f estMB=%,6.0f infeasible=%s  %s%n",
          p.endsWith("ORIGINAL.cmpx") ? "ORIGINAL" : "FACTORISED",
          r.modelMaxCliqueCells, r.estimatedBytes / (1024.0 * 1024.0), r.infeasible, calc);
    }

    System.out.println("GEN --- rewritten nodes ---");
    for (String id : new String[]{"sub_a", "sub_b", "sub_c", "total", "severity_a", "severity_b",
      "escalation", "capacity_ok", "in_band", "breach", "verdict"}) {
      ExtendedNode n = find(afterBf, id);
      System.out.println("GEN   " + id + " = " + (n == null ? "(absorbed by BF)" : describe(n)));
    }
    System.out.println("GEN --- synthesised ---");
    for (Object o : afterBf.getExtendedBNList().getExtendedBNs()) {
      for (Object n : ((ExtendedBN) o).getExtendedNodes()) {
        String id = ((ExtendedNode) n).getConnNodeId();
        if (id.startsWith("mlogit_") || id.startsWith("cmp_score_")) {
          System.out.println("GEN   " + id + " = " + describe((ExtendedNode) n));
        }
      }
    }
  }

  private static int components(uk.co.agena.minerva.model.Model m) throws Exception {
    ExtendedBN ebn = m.getExtendedBNAtIndex(0);
    java.util.Map<String, java.util.Set<String>> adj = new java.util.HashMap<>();
    for (Object o : ebn.getExtendedNodes()) {
      adj.put(((ExtendedNode) o).getConnNodeId(), new java.util.HashSet<String>());
    }
    for (Object o : ebn.getExtendedNodes()) {
      ExtendedNode n = (ExtendedNode) o;
      for (Object c : ebn.getChildNodes(n)) {
        String a = n.getConnNodeId();
        String b = ((ExtendedNode) c).getConnNodeId();
        if (adj.containsKey(a) && adj.containsKey(b)) {
          adj.get(a).add(b);
          adj.get(b).add(a);
        }
      }
    }
    java.util.Set<String> seen = new java.util.HashSet<>();
    int comps = 0;
    for (String id : adj.keySet()) {
      if (seen.contains(id)) {
        continue;
      }
      comps++;
      java.util.Deque<String> st = new java.util.ArrayDeque<>();
      st.push(id);
      while (!st.isEmpty()) {
        String cur = st.pop();
        if (!seen.add(cur)) {
          continue;
        }
        for (String nb : adj.get(cur)) {
          if (!seen.contains(nb)) {
            st.push(nb);
          }
        }
      }
    }
    return comps;
  }

  private static ExtendedNode find(uk.co.agena.minerva.model.Model m, String id) {
    for (Object o : m.getExtendedBNList().getExtendedBNs()) {
      ExtendedNode n = ((ExtendedBN) o).getExtendedNodeWithUniqueIdentifier(id);
      if (n != null) {
        return n;
      }
    }
    return null;
  }

  private static String describe(ExtendedNode n) {
    try {
      if (n.getFunctionMode() == ExtendedNode.EDITABLE_PARENT_STATE_FUNCTIONS) {
        StringBuilder sb = new StringBuilder("PARTITIONED on ");
        for (Object p : n.getPartitionedExpressionModelNodes()) {
          sb.append(((ExtendedNode) p).getConnNodeId()).append(' ');
        }
        sb.append("| cells:");
        for (Object f : n.getPartitionedExpressions()) {
          sb.append(" [").append(((ExtendedNodeFunction) f).getParameters().get(0)).append(']');
        }
        return sb.toString();
      }
      ExtendedNodeFunction f = n.getCurrentNodeFunction();
      return (f == null) ? "(manual NPT)" : f.getName() + f.getParameters();
    } catch (Throwable t) {
      return "(" + t.getClass().getSimpleName() + ")";
    }
  }

  private static int count(uk.co.agena.minerva.model.Model m) {
    int c = 0;
    for (Object o : m.getExtendedBNList().getExtendedBNs()) {
      c += ((ExtendedBN) o).getExtendedNodes().size();
    }
    return c;
  }
}
