package com.agenarisk.api.model;

import org.junit.jupiter.api.Test;
import uk.co.agena.minerva.model.extendedbn.ExtendedBN;
import uk.co.agena.minerva.model.extendedbn.ExtendedNode;
import uk.co.agena.minerva.model.extendedbn.ExtendedState;

/** Do the 0/1 indicator nodes keep their two fixed states through a calculate()? */
public class IndNodeStatesProbe {

  private static final String P =
      "C:/Users/marti/Desktop/test cases/Factorisation chain FACTORISED.cmpx";

  private static void dump(uk.co.agena.minerva.model.Model lm, String when) throws Exception {
    for (Object o : lm.getExtendedBNList().getExtendedBNs()) {
      ExtendedBN ebn = (ExtendedBN) o;
      for (Object n : ebn.getExtendedNodes()) {
        ExtendedNode en = (ExtendedNode) n;
        if (!en.getConnNodeId().startsWith("mlogit_ind_")) {
          continue;
        }
        StringBuilder sb = new StringBuilder("IND " + when + "  " + en.getConnNodeId() + "  states=");
        for (Object s : en.getExtendedStates()) {
          sb.append('[').append(((ExtendedState) s).getRange()).append(']');
        }
        System.out.println(sb);
      }
    }
  }

  @Test
  public void probe() throws Exception {
    Model m = Model.loadModel(P);
    uk.co.agena.minerva.model.Model lm = m.getLogicModel();
    dump(lm, "BEFORE");
    try {
      m.calculate();
      System.out.println("IND calculate OK");
    } catch (Throwable t) {
      System.out.println("IND calculate FAILED: " + t.getMessage());
    }
    dump(lm, "AFTER ");

    // Save the CALCULATED model: its resultValues are exactly the payload the UI charts.
    try {
      java.io.File out = java.io.File.createTempFile("calc", ".cmpx");
      m.save(out.getAbsolutePath());
      System.out.println("IND SAVED " + out.getAbsolutePath());
    } catch (Throwable t) {
      System.out.println("IND save failed " + t);
    }

    // And what do the marginals look like?
    for (Object o : lm.getExtendedBNList().getExtendedBNs()) {
      ExtendedBN ebn = (ExtendedBN) o;
      for (Object n : ebn.getExtendedNodes()) {
        ExtendedNode en = (ExtendedNode) n;
        if (!en.getConnNodeId().startsWith("mlogit_ind_")) {
          continue;
        }
        try {
          uk.co.agena.minerva.util.model.DataSet ds = en.getMarginals();
          StringBuilder sb = new StringBuilder("IND MARGINAL " + en.getConnNodeId() + " -> ");
          for (int i = 0; i < ds.size(); i++) {
            sb.append(String.format("%.4f ", Double.valueOf(
                ds.getDataPointAtOrderPosition(i).getValue())));
          }
          System.out.println(sb);
        } catch (Throwable t) {
          System.out.println("IND MARGINAL " + en.getConnNodeId() + " -> " + t.getClass().getSimpleName());
        }
      }
    }
  }
}
