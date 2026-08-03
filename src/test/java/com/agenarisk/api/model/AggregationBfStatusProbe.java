package com.agenarisk.api.model;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import uk.co.agena.minerva.model.extendedbn.ContinuousIntervalEN;
import uk.co.agena.minerva.model.extendedbn.ExtendedBN;
import uk.co.agena.minerva.model.extendedbn.ExtendedNode;
import uk.co.agena.minerva.model.extendedbn.ExtendedNodeFunction;
import uk.co.agena.minerva.util.binaryfactorisation.BinaryBNConverter;

/**
 * What does binary factorisation ACTUALLY do with each aggregation form? Reading the converter's
 * string-rewriting is not a reliable way to find out, so this builds one model per form over N
 * continuous parents, runs the converter, and reports the generated BN's shape.
 *
 * <p>The number that matters is the maximum in-degree in the generated model. BF's whole purpose is to
 * leave no node with more than 2 parents; a form it does not understand comes through with all N parents
 * on one node, and that node's clique is bins^N.
 */
public class AggregationBfStatusProbe {

  private static final int N = 5;

  private static void setFn(ExtendedNode n, String fn, String... params) throws Exception {
    n.setFunctionMode(ExtendedNode.EDITABLE_NODE_FUNCTION);
    n.setCurrentNodeFunction(
        new ExtendedNodeFunction(fn, Arrays.asList(params), ExtendedNodeFunction.CURRENT_TYPE));
  }

  private static uk.co.agena.minerva.model.Model build(String childExpr) throws Exception {
    uk.co.agena.minerva.model.Model model = uk.co.agena.minerva.model.Model.createEmptyModel();
    ExtendedBN ebn = model.getExtendedBNAtIndex(0);
    for (int i = 1; i <= N; i++) {
      ContinuousIntervalEN p = ebn.addContinuousIntervalNode("p" + i, "p" + i);
      p.setSimulationNode(true);
      p.setDynamicallyDiscretisable(true);
      setFn(p, "Normal", String.valueOf(i * 2.0), "1.0");
    }
    ContinuousIntervalEN y = ebn.addContinuousIntervalNode("y", "y");
    y.setSimulationNode(true);
    y.setDynamicallyDiscretisable(true);
    setFn(y, "Arithmetic", childExpr);
    // Only the parents the expression actually REFERENCES. Linking all five regardless would leave
    // unused parents on the child and inflate its in-degree, which reads exactly like a failure to
    // binarise — the measurement would then be of the test, not of the converter.
    for (int i = 1; i <= N; i++) {
      if (childExpr.matches(".*\\bp" + i + "\\b.*")) {
        ebn.getExtendedNodeWithUniqueIdentifier("p" + i).addChild(y);
      }
    }
    return model;
  }

  /** Runs BF and reports generated node count + worst in-degree, or the failure. */
  private static void report(String label, String childExpr) {
    System.out.println("---- " + label + "   child = Arithmetic(" + childExpr + ")");
    try {
      uk.co.agena.minerva.model.Model model = build(childExpr);
      uk.co.agena.minerva.model.Model copy = uk.co.agena.minerva.model.Model.deepCopyInMemory(model);
      BinaryBNConverter converter = new BinaryBNConverter(model, copy);

      List<ExtendedBN> bns = new ArrayList<ExtendedBN>();
      bns.add(model.getExtendedBNAtIndex(0));
      Boolean[] flags = new Boolean[] { Boolean.TRUE };
      converter.convertBNList(bns, model, flags);
      uk.co.agena.minerva.model.Model built = converter.getBuiltBinaryModel();
      if (built == null) {
        System.out.println("     NOT FACTORISED (converter produced no binary model)");
        return;
      }

      ExtendedBN gen = built.getExtendedBNAtIndex(0);
      int worst = 0;
      String worstNode = "";
      StringBuilder wide = new StringBuilder();
      for (Object o : gen.getExtendedNodes()) {
        ExtendedNode en = (ExtendedNode) o;
        int deg = gen.getParentNodes(en).size();
        if (deg > worst) {
          worst = deg;
          worstNode = en.getConnNodeId();
        }
        if (deg > 2) {
          wide.append(' ').append(en.getConnNodeId()).append('(').append(deg).append(')');
        }
      }
      System.out.println("     generated nodes=" + gen.getExtendedNodes().size()
          + "  worst in-degree=" + worst + " at " + worstNode
          + (worst <= 2 ? "   => FULLY BINARISED" : "   => NOT binarised, wide nodes:" + wide));
      // What does the leaf's expression look like after conversion?
      ExtendedNode leaf = gen.getExtendedNodeWithUniqueIdentifier("y");
      if (leaf != null && leaf.getCurrentNodeFunction() != null) {
        System.out.println("     y expr now = " + leaf.getCurrentNodeFunction().getParameters());
      }
    } catch (Throwable t) {
      Throwable root = t;
      while (root.getCause() != null && root.getCause() != root) {
        root = root.getCause();
      }
      System.out.println("     BF FAILED " + t.getClass().getSimpleName() + ": " + t.getMessage()
          + "  | root: " + root.getClass().getSimpleName() + ": " + root.getMessage());
    }
  }

  /**
   * Does core's own parser know this function, and what does it evaluate to? This separates "core does
   * not know the function" from "BF cannot factorise it" — different problems with different fixes. Asked
   * of the parser directly rather than by calculating: an unfactorised model with five continuous parents
   * is precisely the blow-up BF exists to prevent, and takes long enough to look like a hang.
   */
  private static void parserKnows(String expr) {
    try {
      uk.co.agena.minerva.util.nptgenerator.ExpressionParser p =
          uk.co.agena.minerva.util.nptgenerator.ExpressionParser.getNewInstance();
      Object node = p.parse(expr);
      Object val = p.evaluate((com.singularsys.jep.parser.Node) node);
      System.out.println("     PARSER " + expr + "  =>  " + val);
    } catch (Throwable t) {
      System.out.println("     PARSER " + expr + "  =>  REJECTED "
          + t.getClass().getSimpleName() + ": " + t.getMessage());
    }
  }

  private static String args() {
    StringBuilder sb = new StringBuilder();
    for (int i = 1; i <= N; i++) {
      if (i > 1) {
        sb.append(',');
      }
      sb.append('p').append(i);
    }
    return sb.toString();
  }

  private static String plus() {
    StringBuilder sb = new StringBuilder();
    for (int i = 1; i <= N; i++) {
      if (i > 1) {
        sb.append('+');
      }
      sb.append('p').append(i);
    }
    return sb.toString();
  }

  /**
   * End to end, with default settings, on a model small enough that the unfactorised path is viable
   * either way. The modeller's function picker offers exactly min/max/sum/avg under "Aggregation", so if
   * avg() cannot survive BINARY_FACTORIZATION — which is on by default — that is a live user-facing
   * failure, not merely a missed optimisation.
   */
  @Test
  public void endToEndCalculateWithDefaults() throws Exception {
    for (String expr : new String[] { "sum(p1,p2,p3)", "avg(p1,p2,p3)", "min(p1,p2,p3)",
        "max(p1,p2,p3)", "min(p1,p2,p3) + 1", "sum(p1,p2) + max(p2,p3)" }) {
      try {
        uk.co.agena.minerva.model.Model m = build(expr);
        m.calculate();
        System.out.println("E2E  " + expr + "  ->  "
            + (m.isLastPropagationSuccessful() ? "OK" : "propagation reported FAILURE"));
      } catch (Throwable t) {
        Throwable root = t;
        while (root.getCause() != null && root.getCause() != root) {
          root = root.getCause();
        }
        System.out.println("E2E  " + expr + "  ->  THREW " + root.getClass().getSimpleName()
            + ": " + root.getMessage());
      }
    }
  }

  @Test
  public void probe() {
    report("baseline additive chain", plus());
    report("sum()", "sum(" + args() + ")");
    report("avg()", "avg(" + args() + ")");
    report("mean()", "mean(" + args() + ")");
    report("min()", "min(" + args() + ")");
    report("max()", "max(" + args() + ")");
    report("wmean()", "wmean(1,p1,1,p2,1,p3,1,p4,1,p5)");
    report("sum nested in arithmetic", "sum(" + args() + ") / " + N);
    report("min nested in arithmetic", "min(" + args() + ") + 1");

    System.out.println("==== does core's PARSER know these at all? ====");
    parserKnows("sum(1,2,3)");
    parserKnows("avg(1,2,3)");
    parserKnows("mean(1,2,3)");
    parserKnows("average(1,2,3)");
    parserKnows("min(1,2,3)");
    parserKnows("max(1,2,3)");
    parserKnows("wmean(1,1,1,2,1,3)");
  }

  /**
   * The sum rewrite is a textual {@code StrReplace(parm, "sum", "")} followed by replacing every comma
   * with a plus. Both halves are substring operations, so this checks the two ways that can misfire: a
   * node id that merely CONTAINS "sum", and a comma that does not belong to the sum's own argument list.
   */
  @Test
  public void textualSumRewriteMisfires() throws Exception {
    System.out.println("==== textual sum-rewrite hazards ====");

    // (a) An id containing "sum" — the node really exists this time.
    uk.co.agena.minerva.model.Model m = uk.co.agena.minerva.model.Model.createEmptyModel();
    ExtendedBN ebn = m.getExtendedBNAtIndex(0);
    for (String id : new String[] { "consumption", "other" }) {
      ContinuousIntervalEN p = ebn.addContinuousIntervalNode(id, id);
      p.setSimulationNode(true);
      p.setDynamicallyDiscretisable(true);
      setFn(p, "Normal", "5.0", "1.0");
    }
    ContinuousIntervalEN y = ebn.addContinuousIntervalNode("y", "y");
    y.setSimulationNode(true);
    y.setDynamicallyDiscretisable(true);
    setFn(y, "Arithmetic", "sum(consumption, other)");
    ebn.getExtendedNodeWithUniqueIdentifier("consumption").addChild(y);
    ebn.getExtendedNodeWithUniqueIdentifier("other").addChild(y);
    boolean saved = uk.co.agena.minerva.model.Model.BINARY_FACTORIZATION;
    try {
      uk.co.agena.minerva.model.Model.BINARY_FACTORIZATION = true;
      m.calculate();
      System.out.println("     sum() over an id containing \"sum\": "
          + (m.isLastPropagationSuccessful() ? "OK" : "propagation reported failure"));
    } catch (Throwable t) {
      Throwable root = t;
      while (root.getCause() != null && root.getCause() != root) {
        root = root.getCause();
      }
      System.out.println("     sum() over an id containing \"sum\" FAILED "
          + root.getClass().getSimpleName() + ": " + root.getMessage());
    } finally {
      uk.co.agena.minerva.model.Model.BINARY_FACTORIZATION = saved;
    }

    // (b) A comma that is not the sum's own separator.
    report("sum containing a nested call", "sum(p1, max(p2, p3), p4)");
    report("sum beside another function", "sum(p1, p2) + max(p3, p4)");

    System.out.println("==== remaining aggregation variants ====");
    report("wmin()", "wmin(1,p1,1,p2,1,p3,1,p4,1,p5)");
    report("wmax()", "wmax(1,p1,1,p2,1,p3,1,p4,1,p5)");
    report("mixminmax()", "mixminmax(p1,p2,p3,p4)");
    report("avg nested", "avg(" + args() + ") * 2");
    report("min of 2 (no split needed)", "min(p1,p2)");
    report("nested sums", "sum(sum(p1,p2), sum(p3,p4))");
    report("nested min/max", "max(min(p1,p2), min(p3,p4))");
  }
}
