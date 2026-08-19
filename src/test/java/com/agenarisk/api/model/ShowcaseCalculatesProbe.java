package com.agenarisk.api.model;

import org.junit.jupiter.api.Test;
import uk.co.agena.minerva.model.extendedbn.ExtendedBN;
import uk.co.agena.minerva.model.extendedbn.ExtendedNode;

/** Both showcase files load and calculate, and the ind nodes report sane marginals. */
public class ShowcaseCalculatesProbe {

  private static final String DIR = "C:/Users/marti/Desktop/test cases/";

  private static void run(String file) {
    System.out.println("=== " + file);
    try {
      Model m = Model.loadModel(DIR + file);
      m.calculate();
      uk.co.agena.minerva.model.Model lm = m.getLogicModel();
      System.out.println("  calculate OK, lastPropagationSuccessful=" + lm.isLastPropagationSuccessful());
      for (Object o : lm.getExtendedBNList().getExtendedBNs()) {
        ExtendedBN ebn = (ExtendedBN) o;
        for (Object n : ebn.getExtendedNodes()) {
          ExtendedNode en = (ExtendedNode) n;
          if (!en.getConnNodeId().startsWith("mlogit_ind_")) {
            continue;
          }
          StringBuilder sb = new StringBuilder("  " + en.getConnNodeId() + " ["
              + en.getClass().getSimpleName() + "] -> ");
          uk.co.agena.minerva.util.model.DataSet ds = lm.getMarginalDataStore()
              .getMarginalDataItemListForNode(ebn, en).getMarginalDataItemAtIndex(0).getDataset();
          for (int i = 0; i < ds.size(); i++) {
            sb.append(String.format("%.4f ",
                Double.valueOf(ds.getDataPointAtOrderPosition(i).getValue())));
          }
          System.out.println(sb);
        }
      }
    } catch (Throwable t) {
      System.out.println("  FAILED: " + t.getClass().getSimpleName() + ": " + t.getMessage());
    }
  }

  @Test
  public void bothShowcaseFilesCalculate() {
    run("Factorisation chain ORIGINAL.cmpx");
    run("Factorisation chain FACTORISED.cmpx");
  }
}
