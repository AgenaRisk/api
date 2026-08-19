package com.agenarisk.api.model;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import uk.co.agena.minerva.util.nptgenerator.ExpressionParser;

/**
 * BF's textual sum rewrite replaces EVERY comma in the parameter with a plus, not just the sum's own, so
 * {@code sum(p1,p2) + max(p3,p4)} becomes {@code (p1+p2) + max(p3+p4)} — max left holding one argument.
 * The generated model shows it as {@code max(new_node_1,null)}.
 *
 * <p>Whether that is a wrong ANSWER or merely a broken model depends on how a one-argument max evaluates.
 * If {@code max(x)} silently returns x, the rewrite computes {@code p3+p4} where the author wrote
 * {@code max(p3,p4)} — a wrong number with no error. Asked of the parser directly: the equivalent
 * question via a full model needs an unfactorised run over four continuous parents, which is exactly the
 * blow-up BF exists to prevent and takes long enough to be impractical as a check.
 */
public class MaxArityParserTest {

  private static Object eval(String expr) throws Exception {
    ExpressionParser p = ExpressionParser.getNewInstance();
    return p.evaluate(p.parse(expr));
  }

  private static void show(String expr) {
    try {
      System.out.println("ARITY  " + expr + "  =>  " + eval(expr));
    } catch (Throwable t) {
      System.out.println("ARITY  " + expr + "  =>  REJECTED " + t.getClass().getSimpleName()
          + ": " + t.getMessage());
    }
  }

  @Test
  public void oneArgumentMaxIsTheCorruptionsConsequence() throws Exception {
    show("max(7)");
    show("min(7)");
    show("max(3+4)");
    show("max(3,4)");
    show("sum(7)");

    // The concrete corruption, as numbers: what the author wrote versus what the rewrite computes.
    double authored = ((Number) eval("max(6,8)")).doubleValue();
    double afterRewrite = ((Number) eval("max(6+8)")).doubleValue();
    System.out.println("ARITY  authored max(6,8)=" + authored
        + "   after the comma rewrite max(6+8)=" + afterRewrite);

    assertEquals(8.0, authored, 1e-12, "max(6,8) is 8");
    // The point: a one-argument max is accepted and returns its argument, so the rewrite yields the SUM
    // where the author asked for the LARGER. No exception is raised anywhere along the way.
    assertEquals(14.0, afterRewrite, 1e-12,
        "a one-argument max returns its argument, so the corrupted form computes p3+p4");
  }
}
