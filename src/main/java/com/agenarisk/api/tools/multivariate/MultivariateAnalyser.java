package com.agenarisk.api.tools.multivariate;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;

import uk.co.agena.minerva.model.MarginalDataItem;
import uk.co.agena.minerva.model.MarginalDataItemList;
import uk.co.agena.minerva.model.MessagePassingLinkException;
import uk.co.agena.minerva.model.Model;
import uk.co.agena.minerva.model.PropagationException;
import uk.co.agena.minerva.model.PropagationTerminatedException;
import uk.co.agena.minerva.model.corebn.CoreBNException;
import uk.co.agena.minerva.model.corebn.CoreBNNode;
import uk.co.agena.minerva.model.corebn.CoreBNNodeList;
import uk.co.agena.minerva.model.extendedbn.BooleanEN;
import uk.co.agena.minerva.model.extendedbn.ContinuousEN;
import uk.co.agena.minerva.model.extendedbn.ExtendedBN;
import uk.co.agena.minerva.model.extendedbn.ExtendedBNException;
import uk.co.agena.minerva.model.extendedbn.ExtendedNode;
import uk.co.agena.minerva.model.extendedbn.ExtendedState;
import uk.co.agena.minerva.model.extendedbn.ExtendedStateException;
import uk.co.agena.minerva.model.extendedbn.ExtendedStateNotFoundException;
import uk.co.agena.minerva.model.extendedbn.ExtendedStateNumberingException;
import uk.co.agena.minerva.model.extendedbn.LabelledEN;
import uk.co.agena.minerva.model.extendedbn.RankedEN;
import uk.co.agena.minerva.model.questionnaire.AnswerNotFoundException;
import uk.co.agena.minerva.model.scenario.Observation;
import uk.co.agena.minerva.model.scenario.ObservationNotFoundException;
import uk.co.agena.minerva.model.scenario.Scenario;
import uk.co.agena.minerva.model.scenario.ScenarioNotFoundException;
import uk.co.agena.minerva.util.Config;
import uk.co.agena.minerva.util.Environment;
import uk.co.agena.minerva.util.Logger;
import uk.co.agena.minerva.util.helpers.MathsHelper;
import uk.co.agena.minerva.util.model.DataPoint;
import uk.co.agena.minerva.util.model.DataSet;
import uk.co.agena.minerva.util.model.IntervalDataPoint;
import uk.co.agena.minerva.util.model.MinervaIndexException;
import uk.co.agena.minerva.util.model.MinervaRangeException;
import uk.co.agena.minerva.util.model.NodeBNPair;
import uk.co.agena.minerva.util.model.Progressable;
import uk.co.agena.minerva.util.model.Range;
import uk.co.agena.minerva.util.nptgenerator.NPTGeneratorException;
import uk.co.agena.minerva.util.nptgenerator.NPTGeneratorInsufficientStateRangeException;
import com.agenarisk.api.tools.sensitivity_legacy.SensitivityAnalysisScenarioData;
import com.agenarisk.api.tools.sensitivity_legacy.SensitivityAnalysisSettings;
import uk.co.agena.minerva.model.*;
import uk.co.agena.minerva.model.extendedbn.*;

public class MultivariateAnalyser implements Progressable {

    final static String APPLICATION_DIRECTORY = System.getProperty("user.home") + System.getProperty("file.separator") + "AgenaRisk";
    final static String MARGINALS = "MARGINALS";
    final static String IMAGES_DIR = "Images" + Environment.FILE_SEPARATOR;
    final static String INITIALSTATES = "INITIALSTATES";
    final static char SEPARATOR = (char) 0;
    final static String MEAN = "@MEAN";
    final static String UPPER_PERCENTILE = "@UPPERP";
    final static String LOWER_PERCENTILE = "@LOWERP";
    final static String MEDIAN = "@MEDIAN";
    final static String VARIANCE = "@VARIANCE";
    final static String STANDARD_DEVIATION = "@STDEV";
    final static String WITH_ZERO = "@WITHZERO";
    final public static String TORNADOW = "@TORNADOW";
    final public static String TORNADOH = "@TORNADOH";
    final public static String ROCW = "@ROCW";
    final public static String ROCH = "@ROCH";
    public static int DEFAULT_WIDTH = 800;
    public static int DEFAULT_HEIGHT = 250;
    private Model connModel;
    final String pathOriginal;
    final String pathWorking;
    public NodeBNPair target;
    private List<NodeBNPair> sourceNodeBNPairs;
    public List<Scenario> scenarios;
    public List<SensitivityAnalysisScenarioData> scenarioData;
    public SensitivityAnalysisSettings settings = new SensitivityAnalysisSettings();
    public String[][] BiData = null;
    private int increment = 0;
    public int[][] rankstates = null;
    private int rightnodelength = 0;
    public double pearson = 0.0;
    public double spearman = 0.0;

    public HashMap Meanlst = new HashMap();
    public HashMap Varlst = new HashMap();
    public HashMap Medianlst = new HashMap();

    public String leftnodeid = null;
    public String rightnodeid = null;
    private int actualleftlgth = 0;
    private int actualrightlgth = 0;

    public List pairids = null;
    public List BiDatalst = null;
    public List pearsonlst = null;
    public List spearmanlst = null;
    public List rankstateslst = null;

    public static boolean Localflag = true;
    public static boolean LocalAncestorflag = false;
    public static boolean simulationSettingsChanged = false;

    public List<NodeBNPair> getSources() {
        return sourceNodeBNPairs;
    }

    public NodeBNPair getTarget() {
        return target;
    }

    public Model getConnectedModel() {
        return connModel;
    }

    public MultivariateAnalyser(Model m, String pathOriginal, String pathWorking) {
        connModel = m;
        this.scenarios = new ArrayList();
        this.scenarioData = new ArrayList<SensitivityAnalysisScenarioData>();
        this.pathOriginal = pathOriginal;
        this.pathWorking = pathWorking;
    }

    public List<SensitivityAnalysisScenarioData> getScenarioData() {
        return scenarioData;
    }

    public void reset() {
        this.scenarios = new ArrayList();
        this.scenarioData = new ArrayList<SensitivityAnalysisScenarioData>();
        this.target = null;
        this.sourceNodeBNPairs = null;
    }

    public void addScenario(Scenario scenario) {
        this.scenarios.add(scenario);
        this.scenarioData.add(new SensitivityAnalysisScenarioData(scenario));
    }

    public void setTarget(NodeBNPair target) {
        this.target = target;
    }

    public void setSources(ExtendedBN ebn, String id) {
        List mylist = new ArrayList();
        ExtendedNode enode = ebn.getExtendedNodeWithUniqueIdentifier(id);
        NodeBNPair nbp = new NodeBNPair(ebn, enode);
        mylist.add(nbp);
        this.sourceNodeBNPairs = mylist;
    }

    public List getScenarios() {
        return this.scenarios;
    }

    private List combinationofqueryids(List querysetid) {
        List pairs = new ArrayList();
        for (int i = 0; i < querysetid.size() - 1; i++) {
            for (int j = i + 1; j < querysetid.size(); j++) {
                String[] pair = new String[2];
                pair[0] = (String) querysetid.get(i);
                pair[1] = (String) querysetid.get(j);
                pairs.add(pair);
            }
        }
        return pairs;
    }

    public boolean analyse(int ebn, List querysetid, int scn) throws MessagePassingLinkException, PropagationException, PropagationTerminatedException, MinervaIndexException, MinervaRangeException, ExtendedBNException, CoreBNException, NPTGeneratorInsufficientStateRangeException, NPTGeneratorException, ScenarioNotFoundException, AnswerNotFoundException {

        try {
            com.agenarisk.api.model.Model japiModel = com.agenarisk.api.model.Model.createModel(connModel);
            japiModel.factorize();
            com.agenarisk.api.model.DataSet ds = japiModel.getDataSet(scenarios.get(0).getName().getShortDescription());
            japiModel.calculate(
                    Arrays.asList(japiModel.getNetwork(connModel.getExtendedBN(ebn).getConnID())),
                    Arrays.asList(ds),
                    com.agenarisk.api.model.Model.CalculationFlag.WITH_ANCESTORS,
                    com.agenarisk.api.model.Model.CalculationFlag.KEEP_TAILS_ZERO_REGIONS
            );
            japiModel.convertToStatic(ds, com.agenarisk.api.model.Model.ConversionFlag.IgnoreErrors);
            japiModel = com.agenarisk.api.model.Model.createModel(
                    japiModel.export(
                            com.agenarisk.api.model.Model.ExportFlag.KEEP_OBSERVATIONS,
                            com.agenarisk.api.model.Model.ExportFlag.KEEP_META
                    )
            );
            connModel = japiModel.getLogicModel();
        } catch (Exception ex) {
            Model.SMA = false;
            throw new PropagationException(ex);
        }

        pairids = combinationofqueryids(querysetid);
        BiDatalst = new ArrayList();
        pearsonlst = new ArrayList();
        spearmanlst = new ArrayList();
        rankstateslst = new ArrayList();

        if (Localflag == true) {
            try {
                connModel.save(pathOriginal);
            } catch (Exception e) {
                e.printStackTrace(Logger.err());
            }
        }

        int worklength = pairids.size();
        if (!terminateProgressableTask) {
            final Model maSnapshot;
            try {
                maSnapshot = Model.deepCopyInMemory(connModel);
            } catch (Exception ex) {
                throw new PropagationException("Failed to snapshot model for parallel multivariate analysis", ex);
            }

            int nMAThreads = uk.co.agena.minerva.model.corebn.CoreBNJunctionTree.resolveThreadCount();
            java.util.concurrent.ExecutorService roundExec = java.util.concurrent.Executors.newFixedThreadPool(nMAThreads);
            List<java.util.concurrent.Future<RoundResult>> roundFutures = new ArrayList<>();

            final int fEbn = ebn;
            final int fScn = scn;
            final int fWorklength = worklength;
            final MultivariateAnalyser fMaster = this;
            final String fPathOriginal = this.pathOriginal;
            final String fPathWorking = this.pathWorking;
            final SensitivityAnalysisSettings fSettings = this.settings;

            for (int round = 0; round < worklength; round++) {
                final String[] query = (String[]) pairids.get(round);
                roundFutures.add(roundExec.submit(new java.util.concurrent.Callable<RoundResult>() {
                    public RoundResult call() throws Exception {
                        if (fMaster.terminateProgressableTask) return null;
                        Model roundModel = Model.deepCopyInMemory(maSnapshot);
                        MultivariateAnalyser worker = new MultivariateAnalyser(roundModel, fPathOriginal, fPathWorking);
                        worker.settings = fSettings;
                        return worker.runRound(query, fEbn, fScn, fWorklength, fMaster);
                    }
                }));
            }

            roundExec.shutdown();
            try {
                roundExec.awaitTermination(Long.MAX_VALUE, java.util.concurrent.TimeUnit.SECONDS);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }

            for (int round = 0; round < worklength; round++) {
                RoundResult r = null;
                try { r = roundFutures.get(round).get(); }
                catch (java.util.concurrent.ExecutionException ex) { ex.getCause().printStackTrace(Logger.err()); continue; }
                catch (InterruptedException ex) { Thread.currentThread().interrupt(); continue; }
                if (r == null || r.skip) continue;

                BiDatalst.add(r.biData);
                rankstateslst.add(r.rankstates);
                pearsonlst.add(r.pearson);
                spearmanlst.add(r.spearman);

                if (r.targetNodeConnId != null) {
                    if (!iscontainedkeyword(r.targetNodeConnId, Meanlst)) Meanlst.put(r.targetNodeConnId, r.targetMean);
                    if (!iscontainedkeyword(r.targetNodeConnId, Varlst))  Varlst.put(r.targetNodeConnId, r.targetVar);
                    if (!iscontainedkeyword(r.targetNodeConnId, Medianlst)) Medianlst.put(r.targetNodeConnId, r.targetMedian);
                }
                if (r.sourceNodeConnId != null) {
                    if (!iscontainedkeyword(r.sourceNodeConnId, Meanlst)) Meanlst.put(r.sourceNodeConnId, r.sourceMean);
                    if (!iscontainedkeyword(r.sourceNodeConnId, Varlst))  Varlst.put(r.sourceNodeConnId, r.sourceVar);
                    if (!iscontainedkeyword(r.sourceNodeConnId, Medianlst)) Medianlst.put(r.sourceNodeConnId, r.sourceMedian);
                }
            }
        }

        if (terminateProgressableTask) {
            return false;
        }

        connModel.SMA = false;
        return true;
    }

    private boolean iscontainedkeyword(String key, HashMap hm) {
        if (hm == null) return false;
        return hm.containsKey(key);
    }

    public String[][] getBiData() {
        return BiData;
    }

    private void resizeBiData() {
        int count = 0;
        for (int i = 0; i < BiData.length; i++) {
            if (BiData[i][2] != null) count++;
        }
        String[][] newbidata = new String[count][3];
        for (int j = 0; j < newbidata.length; j++) {
            newbidata[j][0] = BiData[j][0];
            newbidata[j][1] = BiData[j][1];
            newbidata[j][2] = BiData[j][2];
        }
        BiData = newbidata;
    }

    private void setcorrelationstats(String[][] states) {
        double exy = 0;
        String judge0 = states[0][0];
        String judge1 = states[0][1];

        if (judge0 != null) {
            if (judge0.contains(" - ") && judge1.contains(" - ")) {
                for (int x = 0; x < states.length; x++) {
                    String a = states[x][0];
                    String b = states[x][1];
                    String c = states[x][2];
                    double v = Double.valueOf(c);
                    String[] partsa = a.split(" - ");
                    String[] partsb = b.split(" - ");
                    double mida = (Double.valueOf(partsa[0]) + Double.valueOf(partsa[1])) / 2;
                    double midb = (Double.valueOf(partsb[0]) + Double.valueOf(partsb[1])) / 2;
                    exy += mida * midb * v;
                }

                if ((scenarioData.get(0).MA_sourceNodeVariance) != 0 && (scenarioData.get(0).baselineVariance) != 0) {
                    pearson = (exy - (scenarioData.get(0).MA_sourceNodeMean) * (scenarioData.get(0).baselineMean))
                            / (Math.sqrt((scenarioData.get(0).MA_sourceNodeVariance)) * Math.sqrt((scenarioData.get(0).baselineVariance)));
                } else {
                    pearson = Double.POSITIVE_INFINITY;
                }

                double exys = 0.0;
                double eRankTarget = 0.0, eRankTarget2 = 0.0;
                double eRankSource = 0.0, eRankSource2 = 0.0;
                for (int x = 0; x < rankstates.length; x++) {
                    int a = rankstates[x][0];
                    int b = rankstates[x][1];
                    String c = states[x][2];
                    double v = Double.valueOf(c);
                    exys += a * b * v;
                    eRankTarget  += a * v;
                    eRankTarget2 += a * a * v;
                    eRankSource  += b * v;
                    eRankSource2 += b * b * v;
                }
                double varRankTarget = eRankTarget2 - eRankTarget * eRankTarget;
                double varRankSource = eRankSource2 - eRankSource * eRankSource;

                if (varRankTarget != 0 && varRankSource != 0) {
                    spearman = (exys - eRankTarget * eRankSource)
                            / (Math.sqrt(varRankTarget) * Math.sqrt(varRankSource));
                } else {
                    spearman = Double.POSITIVE_INFINITY;
                }
            } else {
                pearson = Double.NaN;
                spearman = Double.NaN;
            }
        }
    }

    private boolean convertToNonSimulationNode(ContinuousEN cen, DataSet targetDataset, boolean wasSimulationNode, ExtendedBN ebn, Scenario scenario) throws ExtendedStateException, ExtendedStateNumberingException {
        if (cen.isSimulationNode()) {
            wasSimulationNode = true;
            ContinuousEN.ConvertToNonSimulation(cen, targetDataset, ebn, scenario);
        }
        return wasSimulationNode;
    }

    private void clearObservations(Scenario scenario, ExtendedNode targetNode, ExtendedBN targetBN, List<Observation> observationsOnSources) throws ExtendedStateNotFoundException, AnswerNotFoundException {
        Model.clearObservation(scenario, targetNode, targetBN, this.connModel);
        clearObservationsOnModel(scenario, observationsOnSources);
    }

    private void initScenarioTargetDataSet(SensitivityAnalysisScenarioData scenarioData, ExtendedBN targetBN, ExtendedNode targetNode, Scenario scenario) {
        scenarioData.targetDataSet = (MultivariateAnalyser.getMarginals(this.connModel, targetBN, targetNode, scenario)).getDataset();
    }

    private void clearObservationsOnModel(Scenario scenario, List<Observation> observationsOnSources) throws AnswerNotFoundException, ExtendedStateNotFoundException {
        for (int i = 0; i < sourceNodeBNPairs.size(); i++) {
            NodeBNPair nbpair = sourceNodeBNPairs.get(i);
            try {
                Observation obs = scenario.getObservation(nbpair.getBN().getId(), nbpair.getNode().getId());
                observationsOnSources.add(obs);
                Model.clearObservation(scenario, nbpair.getNode(), nbpair.getBN(), connModel);
            } catch (ObservationNotFoundException e) {
            }
        }
    }

    protected List getquerynodes() {
        List<ExtendedNode> querynodes = new ArrayList();
        for (int i = 0; i < sourceNodeBNPairs.size(); i++) {
            NodeBNPair nbp = sourceNodeBNPairs.get(i);
            ExtendedNode enode = nbp.getNode();
            querynodes.add(enode);
        }
        return querynodes;
    }

    private void setZerosOnStatesOutsidePercentile(DataSet clonedDataSet) throws MinervaIndexException, MinervaRangeException {
        double actUpperP = MathsHelper.percentile(settings.upperPercentileTornado, clonedDataSet);
        double actLowerP = MathsHelper.percentile(settings.lowerPercentileTornado, clonedDataSet);
        for (int i = 0; i < clonedDataSet.size(); i++) {
            IntervalDataPoint dp = (IntervalDataPoint) clonedDataSet.getDataPointAtOrderPosition(i);
            if (dp.getIntervalUpperBound() < actLowerP || dp.getIntervalLowerBound() > actUpperP) {
                dp.setValue(Double.NaN);
            }
        }
    }

    public void calculateSummaryStatistics() throws MinervaRangeException, MinervaIndexException {
        ContinuousEN cen = (ContinuousEN) sourceNodeBNPairs.get(0).getNode();
        for (int i = 0; i < scenarioData.size(); i++) {
            SensitivityAnalysisScenarioData scnData = (SensitivityAnalysisScenarioData) scenarioData.get(i);
            deriveSummaryStatistics(scnData.targetInitialStates, cen.getName().getShortDescription(), scnData);
        }
    }

    private void deriveBaseLineSummaryStatisticsFromDataSet(ContinuousEN cen, DataSet ds, SensitivityAnalysisScenarioData scnData) throws MinervaRangeException, MinervaIndexException {
        List cex = cen.getExtendedStates();
        double[] xVals = new double[cex.size()];
        double[] pXs = new double[cex.size()];
        Range[] xIntervals = new Range[cex.size()];

        for (int cexi = 0; cexi < cex.size(); cexi++) {
            ExtendedState trState = (ExtendedState) cex.get(cexi);
            Range r = trState.getRange();
            r = MathsHelper.scaleInfinities(r);
            xIntervals[cexi] = r;
            DataPoint dp = ds.getDataPointAtOrderPosition(cexi);
            if (ds.getDataPointAtOrderPosition(cexi).getValue() == 0) continue;
            double dbl = dp.getValue();
            xVals[cexi] = trState.getNumericalValue();
            pXs[cexi] = dbl;
        }

        scnData.baselineMean = MathsHelper.mean(pXs, xVals);
        scnData.baselineVariance = MathsHelper.variance(ds);
        scnData.baselineSD = Math.sqrt(scnData.baselineVariance);
        scnData.baselineMedian = MathsHelper.percentile(50, pXs, xIntervals);
        scnData.baselineUP = MathsHelper.percentile(settings.upperPercentile, pXs, xIntervals);
        scnData.baselineLP = MathsHelper.percentile(this.settings.lowerPercentile, pXs, xIntervals);

        double[] nonzeropotential = calnonzero(pXs);
        double rmean = MathsHelper.rmean(nonzeropotential);
        scnData.baselinerMean = rmean;
        scnData.baselinerVar = MathsHelper.rVar(rmean, nonzeropotential);
    }

    private void deriveSourceNodeSummaryStatisticsFromDataSet(ContinuousEN cen, DataSet ds, SensitivityAnalysisScenarioData scnData) throws MinervaRangeException, MinervaIndexException {
        List cex = cen.getExtendedStates();
        double[] xVals = new double[cex.size()];
        double[] pXs = new double[cex.size()];
        Range[] xIntervals = new Range[cex.size()];

        for (int cexi = 0; cexi < cex.size(); cexi++) {
            ExtendedState trState = (ExtendedState) cex.get(cexi);
            Range r = trState.getRange();
            r = MathsHelper.scaleInfinities(r);
            xIntervals[cexi] = r;
            DataPoint dp = ds.getDataPointAtOrderPosition(cexi);
            if (ds.getDataPointAtOrderPosition(cexi).getValue() == 0) continue;
            double dbl = dp.getValue();
            xVals[cexi] = trState.getNumericalValue();
            pXs[cexi] = dbl;
        }

        scnData.MA_sourceNodeMean = MathsHelper.mean(pXs, xVals);
        scnData.MA_sourceNodeVariance = MathsHelper.variance(ds);
        scnData.MA_sourceNodeMedian = MathsHelper.percentile(50, pXs, xIntervals);

        double[] nonzeropotential = calnonzero(pXs);
        scnData.MA_sourceNoderMean = MathsHelper.rmean(pXs);
        scnData.MA_sourceNoderVar = MathsHelper.rVar(scnData.MA_sourceNoderMean, pXs);
    }

    protected static double getValueOf(HashMap sourcesDetails, ExtendedNode a, ExtendedState a1, ExtendedState b1, boolean searchZero) {
        double value = 0;
        String key = a.getName().getShortDescription() + SEPARATOR + a1.getName().getShortDescription() + SEPARATOR + b1.getName().getShortDescription() + (searchZero ? WITH_ZERO : "");
        String keyZero = (String) sourcesDetails.get(key);
        if (keyZero == null) return 0;
        value = Double.parseDouble((String) sourcesDetails.get(key));
        return value;
    }

    private void deriveSummaryStatistics(List targetInitialStates, String targetName, SensitivityAnalysisScenarioData scnData) throws MinervaRangeException, MinervaIndexException {
        for (int sensi = 0; sensi < sourceNodeBNPairs.size(); sensi++) {
            DataSet clonedDataSet = (DataSet) scnData.sourcesDataSet[sensi].clone();
            NodeBNPair nbpair = sourceNodeBNPairs.get(sensi);
            ExtendedNode enode = nbpair.getNode();

            DataSet targetDataSet = new DataSet();
            DataSet targetDataSetWithZeros = new DataSet();

            if (ExtendedNode.isRealContinuous(enode)) {
                setZerosOnStatesOutsidePercentile(clonedDataSet);
            }

            scnData.sourcesDataSetForTornado[sensi] = clonedDataSet;
            List enstates = scnData.sourceInitialStates[sensi];
            HashMap srcDetails = (HashMap) scnData.sourcesDetails.get(enode);

            for (int statei = 0; statei < enstates.size(); statei++) {
                ExtendedState srcState = (ExtendedState) enstates.get(statei);

                double mean = 0.0, median = 0.0, variance = 0.0;
                double meanWZ = 0.0, medianWZ = 0.0, varianceWZ = 0.0;
                double standarddev = 0.0, upperp = 0.0, lowerp = 0.0;
                double standarddevWZ = 0.0, upperpWZ = 0.0, lowerpWZ = 0.0;

                double[] xVals = new double[targetInitialStates.size()];
                double[] pXs = new double[targetInitialStates.size()];
                double[] pXsWithZero = new double[targetInitialStates.size()];
                Range[] xIntervals = new Range[targetInitialStates.size()];

                boolean isAllNAN = Double.isNaN(clonedDataSet.getDataPointAtOrderPosition(statei).getValue());

                for (int targetInitialStatesIter = 0; targetInitialStatesIter < targetInitialStates.size(); targetInitialStatesIter++) {
                    ExtendedState trState = (ExtendedState) targetInitialStates.get(targetInitialStatesIter);
                    Range r = trState.getRange();
                    r = MathsHelper.scaleInfinities(r);
                    xIntervals[targetInitialStatesIter] = r;

                    if (scnData.targetDataSet.getDataPointAtOrderPosition(targetInitialStatesIter).getValue() == 0) continue;

                    double dbl = getValueOf(srcDetails, target.getNode(), trState, srcState, false);
                    double dblWithZero = Double.NaN;
                    if (!Double.isNaN(clonedDataSet.getDataPointAtOrderPosition(statei).getValue())) {
                        dblWithZero = dbl;
                    }

                    xVals[targetInitialStatesIter] = trState.getNumericalValue();
                    pXs[targetInitialStatesIter] = dbl;
                    pXsWithZero[targetInitialStatesIter] = dblWithZero;
                }

                pXs = MathsHelper.normaliseMarginal(pXs);
                pXsWithZero = MathsHelper.normaliseMarginal(pXsWithZero);

                for (int targetInitialStatesIter = 0; targetInitialStatesIter < targetInitialStates.size(); targetInitialStatesIter++) {
                    ExtendedState trState = (ExtendedState) targetInitialStates.get(targetInitialStatesIter);
                    Range r = trState.getRange();
                    r = MathsHelper.scaleInfinities(r);
                    double dblWithZero = pXsWithZero[targetInitialStatesIter];
                    IntervalDataPoint idpWithZero = new IntervalDataPoint();
                    idpWithZero.setValue(dblWithZero);
                    idpWithZero.setIntervalLowerBound(r.getLowerBound());
                    idpWithZero.setIntervalUpperBound(r.getUpperBound());
                    targetDataSetWithZeros.addDataPoint(idpWithZero);
                }

                mean = MathsHelper.mean(pXs, xVals);
                meanWZ = isAllNAN ? Double.NaN : MathsHelper.mean(pXsWithZero, xVals);
                variance = MathsHelper.variance(targetDataSet);
                varianceWZ = isAllNAN ? Double.NaN : MathsHelper.variance(targetDataSetWithZeros);
                standarddev = Math.sqrt(variance);
                standarddevWZ = isAllNAN ? Double.NaN : Math.sqrt(varianceWZ);

                if (Double.isNaN(mean) && Double.isNaN(variance)) {
                    median = Double.NaN;
                    upperp = Double.NaN;
                    lowerp = Double.NaN;
                    medianWZ = Double.NaN;
                    upperpWZ = Double.NaN;
                    lowerpWZ = Double.NaN;
                } else {
                    median = MathsHelper.percentile(50, pXs, xIntervals);
                    upperp = MathsHelper.percentile(this.settings.upperPercentile, pXs, xIntervals);
                    lowerp = MathsHelper.percentile(this.settings.lowerPercentile, pXs, xIntervals);
                    medianWZ = isAllNAN ? Double.NaN : MathsHelper.percentile(50, pXsWithZero, xIntervals);
                    upperpWZ = isAllNAN ? Double.NaN : MathsHelper.percentile(this.settings.upperPercentile, pXsWithZero, xIntervals);
                    lowerpWZ = isAllNAN ? Double.NaN : MathsHelper.percentile(this.settings.lowerPercentile, pXsWithZero, xIntervals);
                }

                setValueOf(srcDetails, MEAN, srcState, mean, false);
                setValueOf(srcDetails, MEDIAN, srcState, median, false);
                setValueOf(srcDetails, VARIANCE, srcState, variance, false);
                setValueOf(srcDetails, STANDARD_DEVIATION, srcState, standarddev, false);
                setValueOf(srcDetails, UPPER_PERCENTILE, srcState, upperp, false);
                setValueOf(srcDetails, LOWER_PERCENTILE, srcState, lowerp, false);
                setValueOf(srcDetails, MEAN, srcState, meanWZ, true);
                setValueOf(srcDetails, MEDIAN, srcState, medianWZ, true);
                setValueOf(srcDetails, VARIANCE, srcState, varianceWZ, true);
                setValueOf(srcDetails, STANDARD_DEVIATION, srcState, standarddevWZ, true);
                setValueOf(srcDetails, UPPER_PERCENTILE, srcState, upperpWZ, true);
                setValueOf(srcDetails, LOWER_PERCENTILE, srcState, lowerpWZ, true);

                targetDataSet.clearDataPoints();
                targetDataSetWithZeros.clearDataPoints();
            }
        }
    }

    private void setValueOf(HashMap srcDetails, String summaryStatistic, ExtendedState stName, double value, boolean withZero) {
        String extra = (withZero ? WITH_ZERO : "");
        srcDetails.put(summaryStatistic + SEPARATOR + stName.getName().getShortDescription() + extra, "" + value);
    }

    protected static double getValueOf(HashMap srcDetails, String summaryStatistic, ExtendedState stName, boolean withZero) {
        double value = 0;
        String extra = (withZero ? WITH_ZERO : "");
        String key = summaryStatistic + SEPARATOR + stName.getName().getShortDescription() + extra;
        value = Double.parseDouble((String) srcDetails.get(key));
        return value;
    }

    public static MarginalDataItem getMarginals(Model model, ExtendedBN ebn, ExtendedNode enode, Scenario scn) {
        MarginalDataItemList mdil = model.getMarginalDataStore().getMarginalDataItemListForNode(ebn, enode);
        MarginalDataItem mdi = mdil.getMarginalDataItemAtIndex(0);
        return mdi;
    }

    private void setMulrankstates(String[][] states, int rightlength) {
        this.rankstates = null;
        int[][] mul_rankstates = new int[states.length][2];
        int count1 = 1;
        for (int i = 0; i < states.length; i++) {
            mul_rankstates[i][0] = count1;
            if ((i + 1) % rightlength == 0) count1++;
        }
        int count2 = 1;
        for (int j = 0; j < states.length; j++) {
            mul_rankstates[j][1] = count2;
            count2++;
            if ((j + 1) % rightlength == 0) count2 = 1;
        }
        this.rankstates = mul_rankstates;
    }

    private CoreBNNode getnodefromlist(CoreBNNodeList mylist, String id) {
        CoreBNNode mynode = new CoreBNNode();
        for (int i = 0; i < mylist.size(); i++) {
            CoreBNNode round = mylist.get(i);
            if (round.getAltId().equals(id)) {
                mynode = round;
                break;
            }
        }
        return mynode;
    }

    private void ensureInputNodesHaveReceivedMarginals(Model model, ExtendedBN extendedBN, int scenarioCounter) throws ExtendedBNException {
        List mplinksToThisEBN = model.getMessagePassingLinksForChild(extendedBN.getId());
        List inputNodes = extendedBN.getInputNodes();
        for (int i = 0; i < inputNodes.size(); i++) {
            ExtendedNode inputNodeX = (ExtendedNode) inputNodes.get(i);
            if (!inputNodeX.isInputNodeReceivedMarginals()) {
                ExtendedBN outputBN = null;
                ExtendedNode outputNode = null;
                for (int kl = 0; kl < mplinksToThisEBN.size(); kl++) {
                    MessagePassingLinks mplinks = (MessagePassingLinks) mplinksToThisEBN.get(kl);
                    for (mplinks.startIterator(); mplinks.next();) {
                        if (mplinks.getIterationMessagePassingLink() instanceof ConstantMessagePassingLink) continue;
                        int listenerExtBnId = mplinks.getIterationChildExtendedBNId();
                        if (listenerExtBnId != extendedBN.getId()) continue;
                        int listenerExtNodeId = mplinks.getIterationChildExtendedNodeId();
                        if (listenerExtNodeId != inputNodeX.getId()) continue;
                        int senderExtBnId = mplinks.getIterationParentExtendedBNId();
                        ExtendedBN sourceBN = model.getExtendedBNList().getExtendedBN(senderExtBnId);
                        int senderExtendedNodeId = mplinks.getIterationParentExtendedNodeId();
                        ExtendedNode sourceNode = sourceBN.getExtendedNode(senderExtendedNodeId);
                        outputNode = sourceNode;
                        outputBN = sourceBN;
                        break;
                    }
                    if (outputNode != null) break;
                }
                boolean printWarningMessage = false;
                if (outputNode != null) {
                    try {
                        MarginalDataItemList mdil = model.getMarginalDataStore().getMarginalDataItemListForNode(outputBN, outputNode);
                        MarginalDataItem mdi = (MarginalDataItem) mdil.getMarginalDataItems().get(scenarioCounter);
                        DataSet ds = mdi.getDataset();
                        if (!ds.getDataPoints().isEmpty()) {
                            inputNodeX.createExtendedStates(ds);
                            ExtendedNodeEvent exNodeEvent = new ExtendedNodeEvent(outputNode, ds);
                            inputNodeX.setupActionForMarginalsChangedEvent(1, true, false, true, false, extendedBN);
                            inputNodeX.extendedNodeMarginalsChanged(exNodeEvent, true);
                            inputNodeX.setInputNodeReceivedMarginals(false);
                        } else {
                            printWarningMessage = true;
                        }
                    } catch (IndexOutOfBoundsException aioobe) {
                        printWarningMessage = true;
                    }
                } else {
                    printWarningMessage = true;
                }
            }
        }
    }

    private double[] calnonzero(double[] potential) {
        List lst = new ArrayList();
        for (int i = 0; i < potential.length; i++) {
            if (potential[i] != 0) lst.add(potential[i]);
        }
        double[] len = new double[lst.size()];
        for (int j = 0; j < len.length; j++) {
            len[j] = Double.valueOf(lst.get(j).toString());
        }
        return len;
    }

    private static class RoundResult {
        String[][] biData;
        int[][] rankstates;
        double pearson;
        double spearman;
        boolean onetooneflag;
        boolean skip;
        String targetNodeConnId;
        double targetMean, targetVar, targetMedian;
        String sourceNodeConnId;
        double sourceMean, sourceVar, sourceMedian;
    }

    private RoundResult runRound(String[] query, int ebnId, int scnId, int worklength, MultivariateAnalyser master)
            throws MessagePassingLinkException, PropagationException, PropagationTerminatedException,
                   MinervaIndexException, MinervaRangeException, ExtendedBNException, CoreBNException,
                   NPTGeneratorInsufficientStateRangeException, NPTGeneratorException,
                   ScenarioNotFoundException, AnswerNotFoundException {

        RoundResult result = new RoundResult();
        boolean onetooneflag = false;

        ExtendedBN myebn = this.connModel.getExtendedBN(ebnId);

        this.reset();
        ExtendedNode targetNode = myebn.getExtendedNodeWithUniqueIdentifier(query[1]);
        ExtendedBN targetBN = myebn;
        setTarget(new NodeBNPair(targetBN, targetNode));

        List<Scenario> allScenariosAvailableInModel = new ArrayList(connModel.getScenarioList().getScenarios());
        Scenario scenarioForMultivariateAnalysis = null;
        for (int i = 0; i < allScenariosAvailableInModel.size(); i++) {
            Scenario s = allScenariosAvailableInModel.get(i);
            if (s.getId() == scnId) {
                scenarioForMultivariateAnalysis = s;
                break;
            }
        }

        this.addScenario(scenarioForMultivariateAnalysis);
        connModel.getScenarioList().removeAllScenarios();
        setSources(myebn, query[0]);

        int noOfSources = sourceNodeBNPairs.size();
        this.increment = 0;

        if (!master.terminateProgressableTask) {
            SensitivityAnalysisScenarioData scenarioData = this.scenarioData.get(0);
            Scenario scenario = scenarioData.scenario;

            connModel.addScenario(scenario, true, targetBN);
            List<Observation> observationsOnSources = new ArrayList<Observation>();
            if (!master.terminateProgressableTask) {
                clearObservations(scenario, targetNode, targetBN, observationsOnSources);
            }

            if (!master.terminateProgressableTask) {
                connModel.propagateDDAlgorithm(Arrays.asList(scenario), Arrays.asList(target.getBN()), Model.PropagationFlag.WITH_ANCESTORS, Model.PropagationFlag.KEEP_TAILS_ZERO_REGIONS);
                if (targetBN.inconsistentEvidenceWarningGiven) {
                    result.skip = true;
                    return result;
                }
            }

            ensureInputNodesHaveReceivedMarginals(connModel, target.getBN(), scenarioForMultivariateAnalysis.getId());

            try {
                if (Logger.isDebugMode()) {
                    connModel.save(this.pathWorking);
                    Logger.logIfDebug("pathWorking " + this.pathWorking);
                    Files.copy(new File(this.pathWorking).toPath(), new File(Config.getDirectoryHomeAgenaRisk() + "MultivariateAnalysis_init.cmp").toPath(), StandardCopyOption.REPLACE_EXISTING);
                }
            } catch (Exception ex) {
                ex.printStackTrace(Logger.err());
            }

            master.updateCurrentProgress(5);

            initScenarioTargetDataSet(scenarioData, targetBN, targetNode, scenario);
            DataSet targetDataset = scenarioData.targetDataSet;

            List<ExtendedState> originalStates = null;
            boolean wasSimulationNode = false;
            if (ExtendedNode.isRealContinuous(targetNode)) {
                ContinuousEN cen = (ContinuousEN) targetNode;
                originalStates = new ArrayList<ExtendedState>(cen.getExtendedStates());
                wasSimulationNode = convertToNonSimulationNode(cen, targetDataset, wasSimulationNode, targetBN, scenario);
            }

            List targetes = new ArrayList();
            for (int z = 0; z < targetDataset.size(); z++) {
                DataPoint dp = (DataPoint) targetDataset.getDataPoints().get(z);
                targetes.add(dp.getLabel());
            }

            scenarioData.targetInitialStates = new ArrayList<ExtendedState>(targetNode.getExtendedStates());
            scenarioData.sourcesDataSet = new DataSet[noOfSources];
            scenarioData.sourceInitialStates = new List[noOfSources];
            HashMap nodesUsed = new HashMap();
            nodesUsed.put(targetNode, originalStates);
            HashMap nodesUsedForDD = new HashMap();
            HashSet wasSimulation = new HashSet();

            List<ExtendedNode> allnodes = targetBN.getExtendedNodes();
            for (int extendedNodeIndex = 0; extendedNodeIndex < allnodes.size(); extendedNodeIndex++) {
                ExtendedNode mynode = allnodes.get(extendedNodeIndex);
                if (nodesUsed.containsKey(mynode)) {
                    continue;
                } else {
                    try {
                        scenario.getObservation(targetBN.getId(), mynode.getId());
                        continue;
                    } catch (ObservationNotFoundException e) {
                        nodesUsedForDD.put(mynode, new ArrayList(mynode.getExtendedStates()));
                    }
                }
                if (ExtendedNode.isRealContinuous(mynode)) {
                    MarginalDataItem myMdi = getMarginals(connModel, targetBN, mynode, scenario);
                    ContinuousEN cenmynode = (ContinuousEN) mynode;
                    if (cenmynode.isSimulationNode()) {
                        wasSimulation.add(cenmynode);
                        ContinuousEN.ConvertToNonSimulation(cenmynode, myMdi.getDataset(), targetBN, scenario);
                    }
                }
            }

            if ((wasSimulationNode || wasSimulation.size() > 0) && (!master.terminateProgressableTask)) {
                connModel.getExtendedBNList().regenerateNPTforEveryExtendedNode(false);
            }

            connModel.propagateDDAlgorithm(Arrays.asList(scenario), Arrays.asList(target.getBN()), Model.PropagationFlag.KEEP_TAILS_ZERO_REGIONS);

            initScenarioTargetDataSet(scenarioData, targetBN, targetNode, scenario);
            if (ExtendedNode.isRealContinuous(targetNode)) {
                ContinuousEN cen = (ContinuousEN) targetNode;
                originalStates = new ArrayList<ExtendedState>(cen.getExtendedStates());
                if (!master.terminateProgressableTask) {
                    deriveBaseLineSummaryStatisticsFromDataSet(cen, targetDataset, scenarioData);
                }
            }
            scenarioData.targetInitialStates = new ArrayList<ExtendedState>(targetNode.getExtendedStates());
            scenarioData.sourcesDataSet = new DataSet[noOfSources];
            scenarioData.sourcesDataSetForTornado = new DataSet[noOfSources];
            scenarioData.sourceInitialStates = new List[noOfSources];
            for (int sourceIndex = 0; sourceIndex < noOfSources; sourceIndex++) {
                NodeBNPair nbpairSource = sourceNodeBNPairs.get(sourceIndex);
                MarginalDataItem myMdi = getMarginals(connModel, nbpairSource.getBN(), nbpairSource.getNode(), scenario);
                scenarioData.sourcesDataSet[sourceIndex] = myMdi.getDataset();
                List cenOriginalStates = new ArrayList(nbpairSource.getNode().getExtendedStates());
                nodesUsed.put(nbpairSource.getNode(), cenOriginalStates);
                List iStates = new ArrayList(nbpairSource.getNode().getExtendedStates());
                scenarioData.sourceInitialStates[sourceIndex] = iStates;
                HashMap hm = new HashMap();
                hm.put(MultivariateAnalyser.MARGINALS, myMdi);
                hm.put(MultivariateAnalyser.INITIALSTATES, iStates);
                scenarioData.sourcesDetails.put(nbpairSource.getNode(), hm);
                if (nbpairSource.getNode() instanceof ContinuousEN && !(nbpairSource.getNode() instanceof RankedEN)) {
                    deriveSourceNodeSummaryStatisticsFromDataSet((ContinuousEN) nbpairSource.getNode(), scenarioData.sourcesDataSet[sourceIndex], scenarioData);
                }
            }

            try {
                if (Logger.isDebugMode()) {
                    connModel.save(this.pathWorking);
                    Files.copy(new File(this.pathWorking).toPath(), new File(Config.getDirectoryHomeAgenaRisk() + "MultivariateAnalysis.cmp").toPath(), StandardCopyOption.REPLACE_EXISTING);
                }
            } catch (Exception e) {
                e.printStackTrace(Logger.err());
            }

            master.updateCurrentProgress(10);

            for (int i = 0; i < observationsOnSources.size(); i++) {
                if (observationsOnSources.get(i) != null) {
                    Observation obs = (Observation) observationsOnSources.get(i);
                    scenario.addObservation(obs, false);
                }
            }

            String observationType = "ObservationValueProgramatic";
            boolean programatic = true;
            if (targetNode instanceof RankedEN || targetNode instanceof BooleanEN || targetNode instanceof LabelledEN) {
                observationType = "Observation";
                programatic = false;
            }

            double perTargetIntitialState = (80.0 / (scenarioData.targetInitialStates.size() * worklength));
            if (!master.terminateProgressableTask) {
                for (int i = 0; i < scenarioData.targetInitialStates.size(); i++) {
                    if (!master.terminateProgressableTask) {
                        ExtendedState es = (ExtendedState) scenarioData.targetInitialStates.get(i);
                        String esdesc = null;
                        if (targetNode instanceof IntegerIntervalEN) {
                            esdesc = (String) targetes.get(i);
                        } else {
                            esdesc = es.getName().getShortDescription();
                        }

                        DataPoint dp = targetDataset.getDataPointAtOrderPosition(i);
                        if (dp.getValue() <= 1e-12) {
                            master.updateCurrentProgress(perTargetIntitialState);
                            continue;
                        }

                        if (programatic) {
                            Model.generateObservation(observationType, "" + es.getNumericalValue(), scenario, targetNode, targetBN, this.connModel);
                        } else {
                            Model.generateObservation(observationType, es.getName().getShortDescription(), scenario, targetNode, targetBN, this.connModel);
                        }

                        if (!master.terminateProgressableTask) {
                            connModel.propagateDDAlgorithm(Arrays.asList(scenario), Arrays.asList(target.getBN()), Model.PropagationFlag.KEEP_TAILS_ZERO_REGIONS);

                            if (targetBN.inconsistentEvidenceWarningGiven) {
                            }

                            for (int j = 0; j < sourceNodeBNPairs.size(); j++) {
                                NodeBNPair currentSrcNBPair = sourceNodeBNPairs.get(j);
                                ExtendedNode srcnode = currentSrcNBPair.getNode();

                                MarginalDataItem subjectiveSourceMDI = getMarginals(this.connModel, currentSrcNBPair.getBN(), srcnode, scenario);
                                DataSet subjectiveSourceDataset = subjectiveSourceMDI.getDataset();
                                List subjectSourceDataPoints = subjectiveSourceDataset.getDataPoints();

                                this.pearson = 0.0;
                                this.spearman = 0.0;

                                if (this.increment == 0) {
                                    CoreBNNode node0 = this.getnodefromlist(connModel.Mul_nlst, query[0]);
                                    CoreBNNode node1 = this.getnodefromlist(connModel.Mul_nlst, query[1]);

                                    int tablerow = subjectSourceDataPoints.size() * scenarioData.targetInitialStates.size();
                                    this.BiData = new String[tablerow][3];
                                    this.rightnodelength = subjectSourceDataPoints.size();
                                    this.leftnodeid = node1.getAltId();
                                    this.rightnodeid = node0.getAltId();

                                    if (subjectSourceDataPoints.size() == 1 && scenarioData.targetInitialStates.size() == 1) {
                                        this.pearson = Double.POSITIVE_INFINITY;
                                        this.spearman = Double.POSITIVE_INFINITY;
                                        this.BiData[0][0] = "illegal";
                                        this.BiData[0][1] = "illegal";
                                        this.BiData[0][2] = "1";
                                        this.rankstates = new int[1][3];
                                        this.rankstates[0][0] = this.rankstates[0][1] = 1;
                                        this.rankstates[0][2] = 1;
                                        onetooneflag = true;
                                        break;
                                    }
                                }

                                for (int k = 0; k < subjectSourceDataPoints.size(); k++) {
                                    DataPoint cdp = (DataPoint) subjectSourceDataPoints.get(k);

                                    if (srcnode instanceof ContinuousEN && !(srcnode instanceof RankedEN)) {
                                        String str = cdp.getLabel();
                                        if (!str.contains(" - ")) {
                                            str = str + " - " + str;
                                            cdp.setLabel(str);
                                        }
                                        if (!esdesc.contains(" - ")) {
                                            esdesc = esdesc + " - " + esdesc;
                                        }
                                    }

                                    double value = cdp.getValue() * dp.getValue();

                                    if (value <= 1e-16) {
                                        master.updateCurrentProgress(perTargetIntitialState);
                                        continue;
                                    }

                                    this.BiData[this.increment][0] = esdesc;
                                    this.BiData[this.increment][1] = cdp.getLabel();
                                    this.BiData[this.increment][2] = String.valueOf(value);
                                    this.increment++;
                                }

                                if (srcnode instanceof ContinuousEN) {
                                }
                            }
                        }
                        Model.generateObservation("ClearObservation", es.getName().getShortDescription(), scenario, targetNode, targetBN, this.connModel);
                        master.updateCurrentProgress(perTargetIntitialState);
                    }
                }
            }

            if (ExtendedNode.isRealContinuous(targetNode)) {
                ContinuousEN cen = (ContinuousEN) targetNode;
                cen.setExtendedStates(originalStates);
                if (wasSimulationNode) {
                    cen.setSimulationNode(true);
                }
            }

            for (int anodesi = 0; anodesi < allnodes.size(); anodesi++) {
                ExtendedNode mynode = (ExtendedNode) (allnodes.get(anodesi));
                List states = null;
                if (nodesUsed.containsKey(mynode)) {
                    states = (List) nodesUsed.get(mynode);
                } else if (nodesUsedForDD.containsKey(mynode)) {
                    states = (List) nodesUsedForDD.get(mynode);
                }
                if (states != null) {
                    mynode.setExtendedStates(states);
                }
                if (wasSimulation.contains(mynode)) {
                    ContinuousEN cenmynode = (ContinuousEN) mynode;
                    cenmynode.setSimulationNode(true);
                }
            }

            master.updateCurrentProgress(5);
            this.connModel.getScenarioList().removeScenario(scenario);
        }

        for (int i = 0; i < allScenariosAvailableInModel.size(); i++) {
            this.connModel.addScenario((Scenario) allScenariosAvailableInModel.get(i), true, targetBN);
        }

        if (this.BiData == null) {
            result.skip = true;
            return result;
        }
        resizeBiData();

        this.increment = 0;
        if (!onetooneflag) {
            setMulrankstates(this.BiData, this.rightnodelength);
        }

        if (!onetooneflag) {
            setcorrelationstats(this.BiData);
        }

        result.biData = this.BiData;
        result.rankstates = this.rankstates;
        result.pearson = this.pearson;
        result.spearman = this.spearman;
        result.onetooneflag = onetooneflag;

        SensitivityAnalysisScenarioData sd = this.scenarioData.isEmpty() ? null : this.scenarioData.get(0);
        if (sd != null) {
            result.targetNodeConnId = this.target.getNode().getConnNodeId();
            result.targetMean = sd.baselineMean;
            result.targetVar = sd.baselineVariance;
            result.targetMedian = sd.baselineMedian;
            if (!sourceNodeBNPairs.isEmpty()) {
                result.sourceNodeConnId = sourceNodeBNPairs.get(0).getNode().getConnNodeId();
                result.sourceMean = sd.MA_sourceNodeMean;
                result.sourceVar = sd.MA_sourceNodeVariance;
                result.sourceMedian = sd.MA_sourceNodeMedian;
            }
        }
        return result;
    }

    public synchronized void updateCurrentProgress(double inc) {
        this.progress += inc;
    }

    public int getCurrentProgress() {
        return (int) progress;
    }

    public int getLengthOfProgressableTask() {
        lengthOfProgressableTask = scenarios.size() * 100;
        return lengthOfProgressableTask;
    }

    public boolean isProgressableTaskDone() {
        return progressableTaskDone;
    }

    public void resetProgressableTask() {
        this.progress = 0;
        this.lengthOfProgressableTask = -1;
        this.progressableTaskDone = false;
        this.terminateProgressableTask = false;
    }

    public void terminateProgressableTask() {
        this.terminateProgressableTask = true;
        progress = lengthOfProgressableTask;
    }

    protected int lengthOfProgressableTask = -1;
    protected double progress = 0;
    protected boolean progressableTaskDone = false;
    public boolean terminateProgressableTask = false;

    public static ArrayList MultivariateSimulationSettings = new ArrayList(5);

    public void setSimulationSettings(Model m) {
        m.setSimulationEntropyConvergenceTolerance((Double) MultivariateSimulationSettings.get(0));
        m.setSimulationEvidenceTolerancePercent((Double) MultivariateSimulationSettings.get(1));
        m.setSimulationLogging((Boolean) MultivariateSimulationSettings.get(2));
        m.setSimulationNoOfIterations((Integer) MultivariateSimulationSettings.get(3));
        m.setRankedSampleSize((Integer) MultivariateSimulationSettings.get(4));
    }
}
