package com.agenarisk.api.model;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import uk.co.agena.minerva.model.extendedbn.ExtendedBN;
import uk.co.agena.minerva.model.extendedbn.ExtendedNode;
import uk.co.agena.minerva.util.binaryfactorisation.BooleanFactoriser;
import uk.co.agena.minerva.util.binaryfactorisation.ComparativeFactoriser;
import uk.co.agena.minerva.util.binaryfactorisation.MultinomialLogitFactoriser;
import uk.co.agena.minerva.util.binaryfactorisation.ParentAggregationExpander;

/**
 * Export the FACTORISED form of a model, for inspection.
 *
 * <p>The synthesised nodes normally exist only inside {@code propagateDDAlgorithmPrivate}'s throwaway
 * working copy — deliberately, so a load → calculate → save cycle can never persist them into the user's
 * file. That makes the factorisation invisible: you can measure its effect on clique size but not read it.
 * This runs the same pre-passes, in the same order, on a copy that IS saved.</p>
 *
 * <p>The order matters and is copied from the real propagation, not invented:</p>
 * <ol>
 *   <li>{@code ParentAggregationExpander} — first, because sumParents() and friends name no parents, so
 *       nothing downstream can decompose an expression while the token is still in it;</li>
 *   <li>{@code MultinomialLogitFactoriser};</li>
 *   <li>{@code ComparativeFactoriser};</li>
 *   <li>{@code BooleanFactoriser} — last, so it can aggregate the indicator nodes the comparative pass
 *       produced.</li>
 * </ol>
 *
 * <p>Run with:
 * {@code mvn -o test -Dtest=FactorisedExportProbe -Dfx.run=true [-Dfx.in=<path>] [-Dfx.gate=production|all]}</p>
 */
public class FactorisedExportProbe {

  private static final String DIR = "C:/Users/marti/Desktop/test cases/";
  private static final String DEFAULT_IN = DIR + "Factorisation chain JOINED.cmpx";

  @Test
  public void exportFactorised() throws Exception {
    if (!"true".equals(System.getProperty("fx.run"))) {
      return; // inert during a normal build — reads and writes files outside the repo
    }
    String in = System.getProperty("fx.in", DEFAULT_IN);
    // production = the cost gate the real calculate() applies, so cheap nodes keep their single readable
    // table. all = expand every recognised aggregator, which is what the unit tests use.
    boolean ignoreGate = "all".equals(System.getProperty("fx.gate", "production"));

    Model api = Model.loadModel(in);
    uk.co.agena.minerva.model.Model m = api.getLogicModel();
    Map<String, Integer> before = census(m);
    System.out.println("FX in  = " + in);
    System.out.println("FX gate= " + (ignoreGate ? "all (cost gate bypassed)" : "production (cost gate applied)"));
    report("BEFORE", m);

    System.out.println("FX parentAggregation = " + tryExpand(m));
    System.out.println("FX mlogit            = " + MultinomialLogitFactoriser.factorise(m, ignoreGate));
    System.out.println("FX comparative       = " + ComparativeFactoriser.factorise(m, ignoreGate));
    System.out.println("FX boolean           = " + BooleanFactoriser.factorise(m, ignoreGate));
    report("AFTER", m);

    // What the factorisation actually built, grouped by the id prefix each scheme stamps on its nodes.
    System.out.println("FX --- synthesised nodes by scheme ---");
    Map<String, List<String>> byPrefix = new LinkedHashMap<String, List<String>>();
    for (Object o : m.getExtendedBNList().getExtendedBNs()) {
      ExtendedBN ebn = (ExtendedBN) o;
      for (Object n : ebn.getExtendedNodes()) {
        ExtendedNode en = (ExtendedNode) n;
        String id = en.getConnNodeId();
        String prefix = prefixOf(id);
        if (prefix == null) {
          continue;
        }
        List<String> list = byPrefix.get(prefix);
        if (list == null) {
          list = new ArrayList<String>();
          byPrefix.put(prefix, list);
        }
        StringBuilder parents = new StringBuilder();
        for (Object p : ebn.getParentNodes(en)) {
          if (parents.length() > 0) {
            parents.append(", ");
          }
          parents.append(((ExtendedNode) p).getConnNodeId());
        }
        list.add(String.format("%s [%d states] <- %s", id, en.getExtendedStates().size(), parents));
      }
    }
    for (Map.Entry<String, List<String>> e : byPrefix.entrySet()) {
      System.out.println("FX   " + e.getKey() + "*  (" + e.getValue().size() + ")");
      for (String s : e.getValue()) {
        System.out.println("FX      " + s);
      }
    }

    // Per-network node growth, so the shape of the rewrite is visible at a glance.
    System.out.println("FX --- growth by network ---");
    Map<String, Integer> after = census(m);
    for (Map.Entry<String, Integer> e : after.entrySet()) {
      Integer b = before.get(e.getKey());
      System.out.println(String.format("FX   %-34s %3d -> %3d  (+%d)",
          e.getKey(), b == null ? 0 : b.intValue(), e.getValue(),
          e.getValue() - (b == null ? 0 : b.intValue())));
    }

    String out = in.replaceAll("\\.cmpx$", "") + " FACTORISED.cmpx";
    Model.createModel(m).save(out);
    stripRegenerableTables(out);
    System.out.println("FX wrote " + out);
  }

  private static boolean tryExpand(uk.co.agena.minerva.model.Model m) {
    try {
      return ParentAggregationExpander.expand(m);
    } catch (Throwable t) {
      System.out.println("FX   parent aggregation failed: " + t);
      return false;
    }
  }

  /** Which scheme synthesised this node, by the prefix its factoriser stamps on the id. */
  private static String prefixOf(String id) {
    String[] prefixes = {"bool_chain_", "bool_noisy_", "bool_count_", "bool_group_", "bool_rule_",
      "cmp_", "ind_", "mlogit_", "score_", "agg_", "sum_", "bf_"};
    for (String p : prefixes) {
      if (id != null && id.startsWith(p)) {
        return p;
      }
    }
    return null;
  }

  private static Map<String, Integer> census(uk.co.agena.minerva.model.Model m) {
    Map<String, Integer> out = new LinkedHashMap<String, Integer>();
    for (Object o : m.getExtendedBNList().getExtendedBNs()) {
      ExtendedBN ebn = (ExtendedBN) o;
      out.put(ebn.getConnID(), Integer.valueOf(ebn.getExtendedNodes().size()));
    }
    return out;
  }

  private static void report(String label, uk.co.agena.minerva.model.Model m) {
    int nodes = 0;
    for (Object o : m.getExtendedBNList().getExtendedBNs()) {
      nodes += ((ExtendedBN) o).getExtendedNodes().size();
    }
    System.out.println("FX " + label + ": networks=" + m.getExtendedBNList().getExtendedBNs().size()
        + " nodes=" + nodes);
  }

  /**
   * Drop every regenerable table from the saved file (same reason as FactorisationChainGenerator): an
   * expression node's NPT is a function of its parents' CURRENT discretisation, so a table written now is
   * stale as soon as dynamic discretisation refines them — and a stale table of the wrong width makes the
   * junction-tree compile throw. The synthesised nodes' Manual tables are NOT stripped: those are the
   * factorisation, and are the point of the export.
   */
  private static void stripRegenerableTables(String path) throws Exception {
    String json = new String(java.nio.file.Files.readAllBytes(java.nio.file.Paths.get(path)), "UTF-8");
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
    System.out.println("FX   stripped " + stripped + " regenerable table(s)");
  }
}
