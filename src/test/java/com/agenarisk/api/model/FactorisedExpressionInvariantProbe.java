package com.agenarisk.api.model;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import uk.co.agena.minerva.model.extendedbn.ContinuousIntervalEN;
import uk.co.agena.minerva.model.extendedbn.ExtendedBN;
import uk.co.agena.minerva.model.extendedbn.ExtendedNode;
import uk.co.agena.minerva.model.extendedbn.ExtendedNodeFunction;
import uk.co.agena.minerva.util.binaryfactorisation.BinaryBNConverter;
import uk.co.agena.minerva.util.nptgenerator.Arithmetic;
import uk.co.agena.minerva.util.nptgenerator.ExpressionParser;

/**
 * THE INVARIANT: in the model binary factorisation produces, every node identifier appearing in a
 * generated node's Arithmetic expression must be one of that node's own parents.
 *
 * <p>Why it matters, and why this is worth a probe of its own. Dynamic discretisation parses each
 * Arithmetic expression with ONLY that node's parents registered as variables, and the engine's parser
 * runs with {@code setAllowUndeclared(false)}:
 *
 * <pre>
 *   DynDiscPropagation.createRequiredDiscreteValueStates:
 *       List parents = ebn.getParentNodes(cen);
 *       ...
 *       parser.addStringVars(parentNames);      // ONLY the parents
 *       ...
 *       parser.parse(param);                    // throws if anything else is referenced
 * </pre>
 *
 * So an expression that names a non-parent does not merely look untidy — it throws
 * {@code ParseException: Unrecognized symbol "new_node_N"} out of {@code ddPropagate}, inside a
 * ForkJoin task, mid-propagation. That was seen for real in the engine log during the unary-function
 * experiments:
 *
 * <pre>
 *   com.singularsys.jep.ParseException: Unrecognized symbol "new_node_2"
 *     at uk.co.agena.minerva.util.nptgenerator.ExpressionParser.parse(ExpressionParser.java:198)
 *     at ...DynDiscPropagation.createRequiredDiscreteValueStates(DynDiscPropagation.java:6752)
 *     at ...DynDiscPropagation.ddPropagate(DynDiscPropagation.java:1013)
 * </pre>
 *
 * A propagation abandoned part-way leaves whatever marginals it had reached, which is exactly the
 * shape of the two unexplained symptoms: a parentless leaf coming back skewed, and a network
 * reporting no results at all.
 *
 * <p>This probe reproduces the failing call WITHOUT dynamic discretisation: run the converter, then for
 * every generated node build a parser, register only its parents exactly as DD does, and parse its
 * expression. No licence, no DD, no iteration count — if the invariant is broken it shows immediately.
 *
 * <p>Run: {@code mvn -o test -Dtest=FactorisedExpressionInvariantProbe}
 */
public class FactorisedExpressionInvariantProbe {

  private static void setFn(ExtendedNode n, String fn, String... params) throws Exception {
    n.setFunctionMode(ExtendedNode.EDITABLE_NODE_FUNCTION);
    n.setCurrentNodeFunction(
        new ExtendedNodeFunction(fn, Arrays.asList(params), ExtendedNodeFunction.CURRENT_TYPE));
  }

  /** n continuous simulated parents p1..pn, child y with the given Arithmetic expression. */
  private static uk.co.agena.minerva.model.Model build(int n, String childExpr) throws Exception {
    uk.co.agena.minerva.model.Model model = uk.co.agena.minerva.model.Model.createEmptyModel();
    ExtendedBN ebn = model.getExtendedBNAtIndex(0);
    for (int i = 1; i <= n; i++) {
      ContinuousIntervalEN p = ebn.addContinuousIntervalNode("p" + i, "p" + i);
      p.setSimulationNode(true);
      p.setDynamicallyDiscretisable(true);
      setFn(p, "Normal", String.valueOf(i * 2.0), "1.0");
    }
    ContinuousIntervalEN y = ebn.addContinuousIntervalNode("y", "y");
    y.setSimulationNode(true);
    y.setDynamicallyDiscretisable(true);
    setFn(y, "Arithmetic", childExpr);
    for (int i = 1; i <= n; i++) {
      if (childExpr.matches(".*\\bp" + i + "\\b.*")) {
        ebn.getExtendedNodeWithUniqueIdentifier("p" + i).addChild(y);
      }
    }
    return model;
  }

  /**
   * Exactly what DynDiscPropagation does before it evaluates an Arithmetic expression: a fresh parser,
   * the node's parents registered as string vars, then parse. Returns null when it parses, or the
   * failure message when it does not.
   */
  private static String parseAsDdWould(ExtendedBN gen, ExtendedNode node, String expr) {
    try {
      ExpressionParser parser = ExpressionParser.getNewInstance();
      List<String> parentNames = new ArrayList<String>();
      for (Object o : gen.getParentNodes(node)) {
        parentNames.add(((ExtendedNode) o).getConnNodeId());
      }
      parser.addStringVars(parentNames);
      parser.addVariableVars(node.getExpressionVariables().getVariables());
      parser.parse(expr);
      return null;
    } catch (Throwable t) {
      return t.getClass().getSimpleName() + ": " + t.getMessage();
    }
  }

  private static int check(String label, int n, String childExpr) {
    System.out.println("---- " + label + "   child = Arithmetic(" + childExpr + ")");
    int broken = 0;
    try {
      uk.co.agena.minerva.model.Model model = build(n, childExpr);
      uk.co.agena.minerva.model.Model copy = uk.co.agena.minerva.model.Model.deepCopyInMemory(model);
      BinaryBNConverter converter = new BinaryBNConverter(model, copy);
      List<ExtendedBN> bns = new ArrayList<ExtendedBN>();
      bns.add(model.getExtendedBNAtIndex(0));
      converter.convertBNList(bns, model, new Boolean[] { Boolean.TRUE });
      uk.co.agena.minerva.model.Model built = converter.getBuiltBinaryModel();
      if (built == null) {
        System.out.println("     not factorised — nothing to check");
        return 0;
      }
      ExtendedBN gen = built.getExtendedBNAtIndex(0);

      Map<String, String> exprs = new LinkedHashMap<String, String>();
      for (Object o : gen.getExtendedNodes()) {
        ExtendedNode en = (ExtendedNode) o;
        ExtendedNodeFunction f = en.getCurrentNodeFunction();
        if (f == null || !Arithmetic.displayName.equalsIgnoreCase(f.getName())) {
          continue;
        }
        List params = f.getParameters();
        if (params == null || params.isEmpty()) {
          System.out.println("     " + en.getConnNodeId() + "  *** Arithmetic with NO parameters ***");
          broken++;
          continue;
        }
        String expr = String.valueOf(params.get(0));
        exprs.put(en.getConnNodeId(), expr);
        if (expr == null || "null".equals(expr) || expr.trim().isEmpty()) {
          System.out.println("     " + en.getConnNodeId() + "  *** NULL/EMPTY expression ***");
          broken++;
          continue;
        }
        String fail = parseAsDdWould(gen, en, expr);
        if (fail != null) {
          StringBuilder ps = new StringBuilder();
          for (Object p : gen.getParentNodes(en)) {
            ps.append(' ').append(((ExtendedNode) p).getConnNodeId());
          }
          System.out.println("     " + en.getConnNodeId() + " = " + expr);
          System.out.println("        parents:" + ps + "   *** " + fail + " ***");
          broken++;
        }
      }
      System.out.println("     " + exprs.size() + " Arithmetic nodes checked, "
          + (broken == 0 ? "INVARIANT HOLDS" : broken + " BROKEN"));
    } catch (Throwable t) {
      Throwable root = t;
      while (root.getCause() != null && root.getCause() != root) {
        root = root.getCause();
      }
      System.out.println("     conversion failed " + t.getClass().getSimpleName()
          + "  | root: " + root.getClass().getSimpleName() + ": " + root.getMessage());
    }
    return broken;
  }

  /**
   * The same invariant on a REAL model, every network, which is what matters for the shipping
   * {@code compoundParents()} form. Synthetic 5-parent graphs are not evidence at scale.
   *
   * <p>{@code mvn -o test -Dtest=FactorisedExpressionInvariantProbe#realModel -Dinv.in="<path to .cmpx>"}
   */
  @Test
  public void realModel() throws Exception {
    String path = System.getProperty("inv.in");
    if (path == null) {
      System.out.println("realModel: skipped (pass -Dinv.in=<path to .cmpx>)");
      return;
    }
    System.out.println("==== invariant over every network of " + path + " ====");
    com.agenarisk.api.model.Model api = com.agenarisk.api.model.Model.loadModel(path);
    uk.co.agena.minerva.model.Model model = api.getLogicModel();
    // Model.propagateDDAlgorithm does this BEFORE binary factorisation runs, so a probe that skips it
    // hands BF a literal compoundParents() token and fails on the empty argument list. Not a model
    // defect - a missing pre-pass.
    if (uk.co.agena.minerva.util.binaryfactorisation.ParentAggregationExpander.anyQualifies(model)) {
      boolean changed = uk.co.agena.minerva.util.binaryfactorisation.ParentAggregationExpander.expand(model);
      System.out.println("     parent-aggregation pre-pass applied (changed=" + changed + ")");
    }
    uk.co.agena.minerva.model.Model copy = uk.co.agena.minerva.model.Model.deepCopyInMemory(model);
    BinaryBNConverter converter = new BinaryBNConverter(model, copy);

    List<ExtendedBN> bns = new ArrayList<ExtendedBN>();
    List<Boolean> flags = new ArrayList<Boolean>();
    for (Object o : model.getExtendedBNList().getExtendedBNs()) {
      bns.add((ExtendedBN) o);
      flags.add(Boolean.TRUE);
    }
    try {
      converter.convertBNList(bns, model, flags.toArray(new Boolean[0]));
    } catch (Throwable t) {
      Throwable root = t;
      while (root.getCause() != null && root.getCause() != root) {
        root = root.getCause();
      }
      System.out.println("     CONVERSION FAILED " + t.getClass().getSimpleName()
          + "  | root: " + root.getClass().getName() + ": " + root.getMessage());
      for (StackTraceElement e : root.getStackTrace()) {
        if (e.getClassName().startsWith("uk.co.agena")) {
          System.out.println("        at " + e);
        }
        if (e.getClassName().contains("DynDisc") || e.getClassName().contains("ExpressionParser")) break;
      }
      return;
    }
    uk.co.agena.minerva.model.Model built = converter.getBuiltBinaryModel();
    if (built == null) {
      System.out.println("     converter produced no binary model");
      return;
    }

    int checked = 0, broken = 0, nets = 0;
    for (Object o : built.getExtendedBNList().getExtendedBNs()) {
      ExtendedBN gen = (ExtendedBN) o;
      nets++;
      for (Object no : gen.getExtendedNodes()) {
        ExtendedNode en = (ExtendedNode) no;
        ExtendedNodeFunction f = en.getCurrentNodeFunction();
        if (f == null || !Arithmetic.displayName.equalsIgnoreCase(f.getName())) {
          continue;
        }
        List params = f.getParameters();
        String expr = (params == null || params.isEmpty()) ? null : String.valueOf(params.get(0));
        checked++;
        if (expr == null || "null".equals(expr) || expr.trim().isEmpty()) {
          System.out.println("     " + gen.getConnID() + "/" + en.getConnNodeId()
              + "  *** NULL/EMPTY expression ***");
          broken++;
          continue;
        }
        String fail = parseAsDdWould(gen, en, expr);
        if (fail != null) {
          StringBuilder ps = new StringBuilder();
          for (Object p : gen.getParentNodes(en)) {
            ps.append(' ').append(((ExtendedNode) p).getConnNodeId());
          }
          System.out.println("     " + gen.getConnID() + "/" + en.getConnNodeId() + " = " + expr);
          System.out.println("        parents:" + ps + "   *** " + fail + " ***");
          broken++;
        }
      }
    }
    System.out.println("     " + nets + " networks, " + checked + " Arithmetic nodes, "
        + (broken == 0 ? "INVARIANT HOLDS" : broken + " BROKEN"));
  }

  @Test
  public void everyGeneratedExpressionReferencesOnlyItsOwnParents() {
    int broken = 0;
    broken += check("plain additive chain", 5, "p1+p2+p3+p4+p5");
    broken += check("sum()", 5, "sum(p1,p2,p3,p4,p5)");
    broken += check("avg()", 5, "avg(p1,p2,p3,p4,p5)");
    broken += check("product()", 5, "product(p1,p2,p3,p4,p5)");
    broken += check("compound()", 5, "compound(p1,p2,p3,p4,p5)");
    broken += check("compound() 10 parents", 10, "compound(p1,p2,p3,p4,p5,p6,p7,p8,p9,p10)");
    broken += check("variadic min", 5, "min(p1,p2,p3,p4,p5)");
    broken += check("min + 1", 5, "min(p1,p2,p3,p4,p5) + 1");
    broken += check("two-arg pow", 3, "pow(p1,p2) + p3");
    broken += check("wmean", 5, "wmean(2,p1,3,p2,1,p3,4,p4,2,p5)");
    broken += check("wmin equal weights", 5, "wmin(2,p1,2,p2,2,p3,2,p4,2,p5)");
    broken += check("wmin unequal weights", 5, "wmin(2,p1,3,p2,1,p3,4,p4,2,p5)");
    broken += check("mixminmax", 5, "mixminmax(1,2,p1,p2,p3,p4,p5)");
    // The shapes the unary experiment used, now that BF no longer carries unary functions: these are
    // expected to FAIL CONVERSION, which is different from breaking the invariant.
    broken += check("sqrt of a sum of squares", 4, "sqrt(p1^2.0+p2^2.0+p3^2.0+p4^2.0)");
    broken += check("abs of a wide sum", 4, "abs(p1+p2+p3+p4)");
    System.out.println();
    System.out.println("==== TOTAL nodes breaking the invariant: " + broken + " ====");
  }
}
