package com.agenarisk.api.model;

import java.util.Arrays;
import org.junit.jupiter.api.Test;
import uk.co.agena.minerva.model.extendedbn.ContinuousEN;
import uk.co.agena.minerva.model.extendedbn.ExtendedBN;
import uk.co.agena.minerva.model.extendedbn.ExtendedNode;
import uk.co.agena.minerva.model.extendedbn.IntegerIntervalEN;

/**
 * What values does a child's NPT generation actually see when it references an mlogit indicator?
 * Prints the samples for the current ContinuousIntervalEN encoding, and for the IntegerIntervalEN
 * alternative, so the accuracy difference is measured rather than argued.
 */
public class IndicatorSampleProbe {

  private static final String P =
      "C:/Users/marti/Desktop/test cases/Factorisation chain FACTORISED.cmpx";

  @Test
  public void probe() throws Exception {
    Model m = Model.loadModel(P);
    uk.co.agena.minerva.model.Model lm = m.getLogicModel();

    for (Object o : lm.getExtendedBNList().getExtendedBNs()) {
      ExtendedBN ebn = (ExtendedBN) o;
      for (Object n : ebn.getExtendedNodes()) {
        ExtendedNode en = (ExtendedNode) n;
        if (!en.getConnNodeId().startsWith("mlogit_ind_") || !(en instanceof ContinuousEN)) {
          continue;
        }
        ContinuousEN c = (ContinuousEN) en;
        System.out.println("IND " + en.getConnNodeId() + "  class=" + en.getClass().getSimpleName()
            + " sim=" + c.isSimulationNode() + " states=" + c.getExtendedStates().size());
        for (int i = 0; i < c.getExtendedStates().size(); i++) {
          double[] bounds = (double[]) c.getSamplesForState(i, true, 20);
          double[] full = (double[]) c.getSamplesForState(i, false, 5);
          System.out.println("   state " + i + " boundsOnly=" + Arrays.toString(bounds)
              + "  sampled=" + Arrays.toString(full));
        }
        break;
      }
      break;
    }

    // The proposed encoding: two single-integer states, built through the same factory the factoriser
    // uses so the node is fully initialised.
    ExtendedBN host = (ExtendedBN) lm.getExtendedBNList().getExtendedBNs().get(0);
    IntegerIntervalEN ii = host.addIntegerIntervalNode("probe_ind", "probe_ind");
    uk.co.agena.minerva.util.model.DataSet ds = new uk.co.agena.minerva.util.model.DataSet();
    ds.addIntervalDataPoint(0, 0);
    ds.addIntervalDataPoint(1, 1);
    ii.createExtendedStates(ds);
    ii.setSimulationNode(false);
    System.out.println("PROPOSED IntegerIntervalEN states=" + ii.getExtendedStates().size());
    for (int i = 0; i < ii.getExtendedStates().size(); i++) {
      System.out.println("   state " + i
          + " range=" + ((uk.co.agena.minerva.model.extendedbn.ExtendedState)
              ii.getExtendedStates().get(i)).getRange()
          + " boundsOnly=" + Arrays.toString((double[]) ii.getSamplesForState(i, true, 20))
          + "  sampled=" + Arrays.toString((double[]) ii.getSamplesForState(i, false, 5)));
    }
  }
}
