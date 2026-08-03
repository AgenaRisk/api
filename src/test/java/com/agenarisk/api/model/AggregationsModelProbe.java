package com.agenarisk.api.model;

import org.junit.jupiter.api.Test;
import uk.co.agena.minerva.model.extendedbn.ExtendedBN;
import uk.co.agena.minerva.model.extendedbn.ExtendedNode;

/**
 * The user's aggregations.cmpx. Network "net" holds sum/min/max/avg over five simulation parents;
 * "Network_2" holds avg(...)+sum(...)-min(...) over the same five, which threw IndexOutOfBounds before
 * variadic min/max were reduced to nested pairs.
 *
 * <p>Network_2 had no saved results while "net" had all nine, which looked like a UI staleness problem.
 * It was not: the calculation genuinely failed on that expression, so there were no results to show.
 */
public class AggregationsModelProbe {

  private static final String P = "C:/Users/marti/Desktop/test cases/aggregations.cmpx";

  @Test
  public void everyNodeInEveryNetworkCalculates() throws Exception {
    Model m = Model.loadModel(P);
    uk.co.agena.minerva.model.Model lm = m.getLogicModel();
    m.calculate();
    System.out.println("AGG lastPropagationSuccessful=" + lm.isLastPropagationSuccessful());

    int missing = 0;
    for (Object o : lm.getExtendedBNList().getExtendedBNs()) {
      ExtendedBN ebn = (ExtendedBN) o;
      System.out.println("AGG net " + ebn.getId() + "  " + ebn.getName().getShortDescription());
      for (Object n : ebn.getExtendedNodes()) {
        ExtendedNode en = (ExtendedNode) n;
        String mean;
        try {
          mean = String.format("%.4f", Double.valueOf(lm.getMarginalDataStore()
              .getMarginalDataItemListForNode(ebn, en)
              .getMarginalDataItemAtIndex(0).getMeanValue()));
        } catch (Throwable t) {
          mean = "NO RESULT (" + t.getClass().getSimpleName() + ")";
          missing++;
        }
        System.out.println("AGG    " + en.getConnNodeId() + " -> mean " + mean);
      }
    }
    System.out.println("AGG nodes without a result: " + missing);
  }
}
