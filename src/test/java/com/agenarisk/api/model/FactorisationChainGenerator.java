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
 * One CONNECTED network containing all three factorisation forms in a single dependency chain, written
 * out before and after the factorisation sequence.
 *
 * <pre>
 *   c1..c7 (7 Normals)
 *      |
 *      +--> risk_total   = c1+..+c7                       BINARY FACTORISATION
 *      +--> risk_spread  = c1*c2+c3*c4-c5/c6+c7            BINARY FACTORISATION
 *              |
 *              +--> severity (4 states)  MultinomialLogit  MLOGIT (3 predictors, Indicators)
 *              |        |
 *              |        +--> escalation  MultinomialLogit  MLOGIT, PARTITIONED on severity
 *              |        |
 *              |        +--> breach      Comparative       COMPARATIVE, PARTITIONED on severity
 *              |
 *              +--> capacity_ok          Comparative       COMPARATIVE, 4-way diagonal
 *                        |
 *                        +--> final_verdict (plain NPT over capacity_ok + breach)
 * </pre>
 *
 * <p>Deliberately avoids comparing a discrete parent to a state label ({@code flag == "True"}), which the
 * modeller flags as unsupported and steers to a Partition instead — the engine honours it exactly, but the
 * partitioned form is the idiomatic one and it is also what connects the logit to the comparative here.</p>
 *
 * <p>Run: {@code mvn -o test -Dtest=FactorisationChainGenerator -Dgen.run=true}</p>
 */
public class FactorisationChainGenerator {

  private static final String OUT_DIR = System.getProperty("gen.dir", "C:/Users/marti/Desktop");

  private static ContinuousIntervalEN normal(ExtendedBN ebn, String id, String name,
      double mean, double var) throws Exception {
    ContinuousIntervalEN n = ebn.addContinuousIntervalNode(id, name);
    n.setSimulationNode(true);
    n.setDynamicallyDiscretisable(true);
    setFn(n, "Normal", String.valueOf(mean), String.valueOf(var));
    return n;
  }

  private static ContinuousIntervalEN arithmetic(ExtendedBN ebn, String id, String name, String expr)
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
    ebn.getName().setShortDescription("Factorisation chain");

    // LAYER 1 — continuous leaves
    for (int i = 1; i <= 7; i++) {
      normal(ebn, "c" + i, "Contributor " + i, i * 4.0, 3.0 + i * 0.5);
    }

    // LAYER 2 — BINARY FACTORISATION: two wide arithmetic nodes over all seven leaves
    arithmetic(ebn, "risk_total", "Risk total (5-way sum)",
        "c1 + c2 + c3 + c4 + c5");
    arithmetic(ebn, "risk_spread", "Risk spread (product + sum)",
        "c5 * c6 + c7");
    // Deliberately only PARTIALLY overlapping parent sets (they share c5 alone). Two wide functions
    // over the SAME seven leaves moralise into a densely-coupled graph that binary factorisation cannot
    // decouple — the junction tree stays enormous however well each expression is binarised.
    for (int i = 1; i <= 5; i++) {
      link(ebn, "c" + i, "risk_total");
    }
    for (int i = 5; i <= 7; i++) {
      link(ebn, "c" + i, "risk_spread");
    }

    // LAYER 3 — MULTINOMIAL LOGIT, fed by the two BF outputs plus two raw leaves and two categoricals
    LabelledEN region = labelled(ebn, "region", "Region", "North", "Middle", "South");
    BooleanEN urban = ebn.addBooleanNode("urban", "Urban site");
    LabelledEN severity = labelled(ebn, "severity", "Severity (logit, 3 states)",
        "Negligible", "Moderate", "Severe");
    // K=3 so K-1 = 2 score nodes. Kept deliberately modest: the K-1 scores all depend on the SAME
    // continuous covariates, so triangulation couples them — a K=4 logit over four shared covariates
    // makes the junction tree too large to compile at all, which is the residual ceiling that predictor
    // extraction does not remove (see DD_MLOGIT_BINARY_FACTORISATION).
    for (String p : new String[]{"risk_total", "risk_spread", "region", "urban"}) {
      link(ebn, p, "severity");
    }
    setFn(severity, "MultinomialLogit",
        "0.30 + 0.020*risk_total"
            + " + 0.40*Indicator(region,\"" + st(region, 0) + "\")"
            + " + 0.70*Indicator(urban,\"True\")",
        "-0.50 + 0.015*risk_spread"
            + " + 0.90*Indicator(region,\"" + st(region, 1) + "\")");

    // LAYER 3b — PARTITIONED MULTINOMIAL LOGIT, partitioned on the logit outcome above.
    // Different slope AND intercept per severity band — the thing Indicator dummies cannot express.
    BooleanEN escalation = ebn.addBooleanNode("escalation", "Escalation (logit, partitioned on severity)");
    // Only ONE continuous covariate besides the extracted Indicator. With two, the partitioned score
    // node BF produces becomes BF-eligible itself and gets nested (a node partitioned on severity whose
    // cells reference another node partitioned on severity), which inflates a clique to ~3e8 and makes
    // the saved factorised model too large to compile.
    for (String p : new String[]{"risk_total", "urban", "severity"}) {
      link(ebn, p, "escalation");
    }
    List<ExtendedNodeFunction> escCells = new ArrayList<ExtendedNodeFunction>();
    for (int c = 0; c < severity.getExtendedStates().size(); c++) {
      double slope = 0.02 * (c + 1);
      double icept = -2.0 + c;
      escCells.add(new ExtendedNodeFunction("MultinomialLogit",
          Arrays.asList(new String[]{icept + " + " + slope + "*risk_total + "
              + (0.3 * (c + 1)) + "*Indicator(urban,\"True\")"}),
          ExtendedNodeFunction.CURRENT_TYPE));
    }
    escalation.setPartitionedExpressionModelNodes(Arrays.asList(new ExtendedNode[]{severity}));
    escalation.setPartitionedExpressions(escCells);

    // LAYER 4 — COMPARATIVE, wide diagonal over two BF outputs and two thresholds
    // Thresholds are centred on the operands so neither comparative is degenerate: a condition that
    // is always true leaves an impossible child state (an all-zero NPT row), which the engine reports
    // as inconsistent evidence.
    normal(ebn, "cap_a", "Capacity allowance A", 180.0, 25.0);
    normal(ebn, "cap_b", "Capacity allowance B", 180.0, 25.0);
    BooleanEN capacityOk = ebn.addBooleanNode("capacity_ok", "Capacity OK (4-way diagonal)");
    for (String p : new String[]{"risk_total", "risk_spread", "cap_a", "cap_b"}) {
      link(ebn, p, "capacity_ok");
    }
    setFn(capacityOk, "Comparative",
        "if(risk_total + risk_spread > cap_a + cap_b, \"True\", \"False\")");

    // LAYER 4b — PARTITIONED COMPARATIVE, partitioned on severity. Cells disagree in shape: two
    // constants and two inequalities that are affine images of one another, so the two inequalities
    // collapse onto ONE score node with TWO pinned boundaries.
    normal(ebn, "limit_a", "Breach limit", 110.0, 20.0);
    BooleanEN breach = ebn.addBooleanNode("breach", "Breach (comparative, partitioned on severity)");
    for (String p : new String[]{"risk_total", "limit_a", "severity"}) {
      link(ebn, p, "breach");
    }
    breach.setPartitionedExpressionModelNodes(Arrays.asList(new ExtendedNode[]{severity}));
    breach.setPartitionedExpressions(Arrays.asList(new ExtendedNodeFunction[]{
      new ExtendedNodeFunction("Comparative", Arrays.asList(new String[]{"\"False\""}),
          ExtendedNodeFunction.CURRENT_TYPE),
      new ExtendedNodeFunction("Comparative",
          Arrays.asList(new String[]{"if(risk_total > limit_a, \"True\", \"False\")"}),
          ExtendedNodeFunction.CURRENT_TYPE),
      new ExtendedNodeFunction("Comparative",
          Arrays.asList(new String[]{"if(risk_total > limit_a - 5, \"True\", \"False\")"}),
          ExtendedNodeFunction.CURRENT_TYPE),
      new ExtendedNodeFunction("Comparative", Arrays.asList(new String[]{"\"True\""}),
          ExtendedNodeFunction.CURRENT_TYPE)}));

    // LAYER 5 — a discrete sink joining the two comparatives, with a hand-written NPT.
    BooleanEN verdict = ebn.addBooleanNode("final_verdict", "Final verdict");
    link(ebn, "capacity_ok", "final_verdict");
    link(ebn, "breach", "final_verdict");
    link(ebn, "escalation", "final_verdict");
    // [childState][parentCombination]; 2 x (2*2*2) = 8 columns. Row 1 = "True".
    double[][] npt = new double[2][8];
    for (int col = 0; col < 8; col++) {
      double pTrue = 0.05 + 0.12 * (col % 7);
      npt[0][col] = 1.0 - pTrue;
      npt[1][col] = pTrue;
    }
    verdict.setNPT(npt, Arrays.asList(new ExtendedNode[]{
      ebn.getExtendedNodeWithUniqueIdentifier("capacity_ok"),
      ebn.getExtendedNodeWithUniqueIdentifier("breach"),
      ebn.getExtendedNodeWithUniqueIdentifier("escalation")}));

    // Every table must be sized against the FINAL parent set. Without this the saved file carries a
    // table sized from when the node was created — the defect that made "Hypothesis (copy)" refuse to
    // calculate — and the junction-tree compile walks off the end of it.
    for (Object o : ebn.getExtendedNodes()) {
      ExtendedNode n = (ExtendedNode) o;
      n.setNptReCalcRequired(true);
      try {
        ebn.regenerateNPT(n);
      } catch (Throwable ignored) {
        // a node whose parents are not yet discretised can fail here; the export strips its table anyway
      }
    }
    return model;
  }

  /**
   * Drops every regenerable table from the saved file.
   *
   * <p>An expression node's NPT is a function of its parents' CURRENT discretisation, so a table written
   * now is stale the moment dynamic discretisation refines those parents — and a stale table of the wrong
   * width makes the junction-tree compile throw ArrayIndexOutOfBounds. Writing {@code nptCompiled:false}
   * with no {@code probabilities} tells the engine to build the table itself, which is the same repair
   * that fixed "Hypothesis (copy)".</p>
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
    java.nio.file.Files.write(java.nio.file.Paths.get(path),
        root.toString(2).getBytes("UTF-8"));
    System.out.println("GEN   stripped " + stripped + " regenerable table(s) from " + path);
  }

  @Test
  public void generate() throws Exception {
    if (!"true".equals(System.getProperty("gen.run"))) {
      return;
    }

    uk.co.agena.minerva.model.Model original = build();
    System.out.println("GEN engine expression check on ORIGINAL = "
        + original.checkExpressions(new ArrayList(original.getExtendedBNList().getExtendedBNs())));
    String p1 = OUT_DIR + "/Factorisation chain ORIGINAL.cmpx";
    Model.createModel(original).save(p1);
    stripRegenerableTables(p1);
    System.out.println("GEN wrote " + p1 + "  nodes=" + count(original));

    uk.co.agena.minerva.model.Model work = build();
    System.out.println("GEN mlogit changed = " + MultinomialLogitFactoriser.factorise(work, true)
        + " nodes=" + count(work));
    System.out.println("GEN comparative changed = " + ComparativeFactoriser.factorise(work, true)
        + " nodes=" + count(work));

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
        System.out.println("GEN BF produced nodes=" + count(built));
      }
    } catch (Throwable t) {
      System.out.println("GEN BF FAILED " + t.getClass().getSimpleName() + ": " + t.getMessage());
    }

    String p2 = OUT_DIR + "/Factorisation chain FACTORISED.cmpx";
    Model.createModel(afterBf).save(p2);
    stripRegenerableTables(p2);
    System.out.println("GEN wrote " + p2 + "  nodes=" + count(afterBf));

    System.out.println("GEN --- rewritten chain nodes ---");
    for (String id : new String[]{"risk_total", "risk_spread", "severity", "escalation",
      "capacity_ok", "breach", "final_verdict"}) {
      ExtendedNode n = find(afterBf, id);
      System.out.println("GEN   " + id + " = " + (n == null ? "(absorbed)" : describe(n)));
    }
    System.out.println("GEN --- score / ind nodes ---");
    for (Object o : afterBf.getExtendedBNList().getExtendedBNs()) {
      for (Object n : ((ExtendedBN) o).getExtendedNodes()) {
        String id = ((ExtendedNode) n).getConnNodeId();
        if (id.startsWith("mlogit_") || id.startsWith("cmp_score_")) {
          System.out.println("GEN   " + id + " = " + describe((ExtendedNode) n));
        }
      }
    }
    System.out.println("GEN reload ORIGINAL   = " + reloads(p1));
    System.out.println("GEN reload FACTORISED = " + reloads(p2));
    System.out.println("GEN connected components ORIGINAL   = " + components(original));
    System.out.println("GEN connected components FACTORISED = " + components(afterBf));
  }

  /** Undirected connected-component count, to prove the network really is one graph. */
  private static int components(uk.co.agena.minerva.model.Model m) throws Exception {
    ExtendedBN ebn = m.getExtendedBNAtIndex(0);
    List nodes = ebn.getExtendedNodes();
    List<String> ids = new ArrayList<String>();
    for (Object o : nodes) {
      ids.add(((ExtendedNode) o).getConnNodeId());
    }
    java.util.Map<String, java.util.Set<String>> adj = new java.util.HashMap<String, java.util.Set<String>>();
    for (String id : ids) {
      adj.put(id, new java.util.HashSet<String>());
    }
    for (Object o : nodes) {
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
    java.util.Set<String> seen = new java.util.HashSet<String>();
    int comps = 0;
    for (String id : ids) {
      if (seen.contains(id)) {
        continue;
      }
      comps++;
      java.util.Deque<String> stack = new java.util.ArrayDeque<String>();
      stack.push(id);
      while (!stack.isEmpty()) {
        String cur = stack.pop();
        if (!seen.add(cur)) {
          continue;
        }
        for (String nb : adj.get(cur)) {
          if (!seen.contains(nb)) {
            stack.push(nb);
          }
        }
      }
    }
    return comps;
  }

  private static String reloads(String path) {
    try {
      Model m = Model.loadModel(path);
      return "OK (" + m.getNetworks().size() + " network(s))";
    } catch (Throwable t) {
      return "FAILED " + t.getClass().getSimpleName() + ": " + t.getMessage();
    }
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
