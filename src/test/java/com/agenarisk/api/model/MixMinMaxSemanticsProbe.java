package com.agenarisk.api.model;

import org.junit.jupiter.api.Test;
import uk.co.agena.minerva.util.nptgenerator.ExpressionParser;

/** What does mixminmax actually compute? Checked against the candidate closed form. */
public class MixMinMaxSemanticsProbe {
  private static double eval(String e) throws Exception {
    ExpressionParser p = ExpressionParser.getNewInstance();
    return ((Number) p.evaluate(p.parse(e))).doubleValue();
  }

  @Test
  public void formula() throws Exception {
    // mixminmax(wMin, wMax, x1..xn) == (wMin*min(x) + wMax*max(x)) / (wMin + wMax)
    String[][] cases = {
      { "mixminmax(1,1,4,9,2)", "(1*min(min(4,9),2) + 1*max(max(4,9),2)) / (1+1)" },
      { "mixminmax(3,1,4,9,2)", "(3*min(min(4,9),2) + 1*max(max(4,9),2)) / (3+1)" },
      { "mixminmax(1,4,10,20,30,40)", "(1*min(min(min(10,20),30),40) + 4*max(max(max(10,20),30),40)) / (1+4)" },
    };
    for (String[] c : cases) {
      System.out.printf("MIX %-30s = %8.4f   formula = %8.4f%n", c[0],
          Double.valueOf(eval(c[0])), Double.valueOf(eval(c[1])));
    }
    // Is mixminmax(...) left untouched by the desugarer, despite containing "min" and "max"?
    System.out.println("MIX desugar passthrough = "
        + uk.co.agena.minerva.util.binaryfactorisation.AggregationDesugarer.desugar("mixminmax(1,1,4,9,2)"));
  }
}
