package com.agenarisk.api.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import org.junit.jupiter.api.Test;
import uk.co.agena.minerva.model.MarginalDataItem;
import uk.co.agena.minerva.model.extendedbn.ContinuousIntervalEN;
import uk.co.agena.minerva.model.extendedbn.ExtendedBN;
import uk.co.agena.minerva.model.extendedbn.ExtendedNode;
import uk.co.agena.minerva.model.extendedbn.ExtendedNodeFunction;

/**
 * Summary statistics must survive a factorisation working-copy detour.
 *
 * <p>They live ON the MarginalDataItem rather than being derived from the dataset, and the shared copy-back
 * only copied the dataset — so getMeanValue() answered 0.0 while the bins were correct and carried full
 * mass. Those values are what the api reports as `summaryStatistics`, so any model taking a detour saved
 * zeros for mean, median, variance, sd and percentiles.
 *
 * <p>Uses sumParents(), which always detours. Parents are near-point masses so the mean is exact arithmetic
 * on their means: 1+2+6+8 = 17.
 */
public class DetourStatsTest {

  private static final double[] MEANS = { 1.0, 2.0, 6.0, 8.0 };

  private static void setFn(ExtendedNode n, String fn, String... p) throws Exception {
    n.setFunctionMode(ExtendedNode.EDITABLE_NODE_FUNCTION);
    n.setCurrentNodeFunction(new ExtendedNodeFunction(fn, Arrays.asList(p), ExtendedNodeFunction.CURRENT_TYPE));
  }

  private static MarginalDataItem calcAndGetItem(String childExpr) throws Exception {
    uk.co.agena.minerva.model.Model m = uk.co.agena.minerva.model.Model.createEmptyModel();
    ExtendedBN ebn = m.getExtendedBNAtIndex(0);
    for (int i = 0; i < MEANS.length; i++) {
      ContinuousIntervalEN p = ebn.addContinuousIntervalNode("p" + (i + 1), "p" + (i + 1));
      p.setSimulationNode(true);
      p.setDynamicallyDiscretisable(true);
      setFn(p, "Normal", String.valueOf(MEANS[i]), "1e-6");
    }
    ContinuousIntervalEN y = ebn.addContinuousIntervalNode("y", "y");
    y.setSimulationNode(true);
    y.setDynamicallyDiscretisable(true);
    setFn(y, "Arithmetic", childExpr);
    for (int i = 1; i <= MEANS.length; i++) {
      ebn.getExtendedNodeWithUniqueIdentifier("p" + i).addChild(y);
    }
    m.calculate();
    assertTrue(m.isLastPropagationSuccessful(), childExpr + " should calculate");
    return m.getMarginalDataStore()
        .getMarginalDataItemListForNode(ebn, ebn.getExtendedNodeWithUniqueIdentifier("y"))
        .getMarginalDataItemAtIndex(0);
  }

  @Test
  public void meanSurvivesTheDetour() throws Exception {
    MarginalDataItem mdi = calcAndGetItem("sumParents()");
    System.out.printf("STATS mean=%.4f median=%.4f sd=%.4f var=%.4f entropy=%.6f%n",
        Double.valueOf(mdi.getMeanValue()), Double.valueOf(mdi.getMedianValue()),
        Double.valueOf(mdi.getStandardDeviationValue()), Double.valueOf(mdi.getVarianceValue()),
        Double.valueOf(mdi.getEntropyValue()));
    // The defect: this was exactly 0.0 while the marginal was centred on 17.
    assertEquals(17.0, mdi.getMeanValue(), 0.05, "mean must come across from the working copy");
    assertEquals(17.0, mdi.getMedianValue(), 0.05, "median must come across too");
  }

  @Test
  public void anUnfactorisedModelIsUnaffected() throws Exception {
    // Same model with the parents named explicitly: no detour, so this always worked and must keep doing.
    MarginalDataItem mdi = calcAndGetItem("p1+p2+p3+p4");
    assertEquals(17.0, mdi.getMeanValue(), 0.05);
  }

  @Test
  public void theDatasetStillCarriesFullMass() throws Exception {
    MarginalDataItem mdi = calcAndGetItem("sumParents()");
    uk.co.agena.minerva.util.model.DataSet ds = mdi.getDataset();
    double mass = 0;
    for (int i = 0; i < ds.size(); i++) {
      mass += ds.getDataPointAtOrderPosition(i).getValue();
    }
    assertEquals(1.0, mass, 1e-6, "copying stats must not disturb the marginal itself");
  }
}
