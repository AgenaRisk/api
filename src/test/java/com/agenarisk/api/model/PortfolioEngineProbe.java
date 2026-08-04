package com.agenarisk.api.model;

import java.util.ArrayList;
import org.junit.jupiter.api.Test;

/** Does the ENGINE accept the portfolio model, dynamic_* references and all? */
public class PortfolioEngineProbe {
  private static final String P =
      "C:/Users/marti/Desktop/test cases/portfolio grouped and collapsed.cmpx";

  @Test
  public void probe() {
    try {
      Model m = Model.loadModel(P);
      uk.co.agena.minerva.model.Model lm = m.getLogicModel();
      System.out.println("PF load OK, networks=" + lm.getExtendedBNList().getExtendedBNs().size());
      boolean ok = lm.checkExpressions(new ArrayList(lm.getExtendedBNList().getExtendedBNs()));
      System.out.println("PF engine checkExpressions = " + ok);
      int shown = 0;
      for (Object e : lm.getErrors()) {
        System.out.println("PF   engine error: " + e);
        if (++shown > 8) { System.out.println("PF   ..."); break; }
      }
    } catch (Throwable t) {
      System.out.println("PF FAILED " + t.getClass().getSimpleName() + ": " + t.getMessage());
    }
  }
}
