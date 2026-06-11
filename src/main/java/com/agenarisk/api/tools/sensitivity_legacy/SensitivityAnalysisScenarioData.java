package com.agenarisk.api.tools.sensitivity_legacy;

import java.util.*;
import uk.co.agena.minerva.model.extendedbn.*;
import uk.co.agena.minerva.model.scenario.Scenario;
import uk.co.agena.minerva.util.model.DataSet;

public class SensitivityAnalysisScenarioData {

    public double baselineMean;
    public double baselineMedian;
    public double baselineVariance;
    public double baselinerMean;
    public double baselinerVar;
    public double baselineSD;
    public double baselineUP;
    public double baselineLP;
    public double MA_sourceNodeMean;
    public double MA_sourceNodeVariance;
    public double MA_sourceNodeMedian;
    public double MA_sourceNoderMean;
    public double MA_sourceNoderVar;
    public Scenario scenario;
    public DataSet targetDataSet;
    public DataSet[] sourcesDataSet;
    public DataSet[] sourcesDataSetForTornado;
    public List<ExtendedState> targetInitialStates;
    public List[] sourceInitialStates;
    public HashMap<ExtendedNode, HashMap> sourcesDetails;
    public HashMap<ExtendedNode, HashMap> sourcesDetailsOverride;

    public SensitivityAnalysisScenarioData(Scenario scn) {
        scenario = scn;
        sourcesDetails = new HashMap();
        sourcesDetailsOverride = new HashMap<>();
    }

}
