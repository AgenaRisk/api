package com.agenarisk.api.tools.sensitivity_legacy;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;

import uk.co.agena.minerva.model.*;
import uk.co.agena.minerva.model.corebn.CoreBNException;
import uk.co.agena.minerva.model.extendedbn.*;
import uk.co.agena.minerva.model.questionnaire.AnswerNotFoundException;
import uk.co.agena.minerva.model.scenario.Observation;
import uk.co.agena.minerva.model.scenario.ObservationNotFoundException;
import uk.co.agena.minerva.model.scenario.Scenario;
import uk.co.agena.minerva.model.scenario.ScenarioNotFoundException;
import uk.co.agena.minerva.util.Config;
import uk.co.agena.minerva.util.Environment;
import uk.co.agena.minerva.util.Logger;
import uk.co.agena.minerva.util.helpers.MathsHelper;
import uk.co.agena.minerva.util.io.FileHandler;
import uk.co.agena.minerva.util.io.FileHandlingException;
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

import static uk.co.agena.minerva.model.Model.PropagationFlag;

public class SensitivityAnalyser implements Progressable {

    public final static String APPLICATION_DIRECTORY = System.getProperty("user.home") + System.getProperty("file.separator")
            + "AgenaRisk";
    public final static String MARGINALS = "MARGINALS";
    public final static String IMAGES_DIR = "Images" + Environment.FILE_SEPARATOR;
    public final static String INITIALSTATES = "INITIALSTATES";
    public final static char SEPARATOR = (char) 0;
    public final static String MEAN = "@MEAN";
    public final static String UPPER_PERCENTILE = "@UPPERP";
    public final static String LOWER_PERCENTILE = "@LOWERP";
    public final static String MEDIAN = "@MEDIAN";
    public final static String VARIANCE = "@VARIANCE";
    public final static String STANDARD_DEVIATION = "@STDEV";
    public final static String WITH_ZERO = "@WITHZERO";
    final public static String TORNADOW = "@TORNADOW";
    final public static String TORNADOH = "@TORNADOH";
    final public static String ROCW = "@ROCW";
    final public static String ROCH = "@ROCH";
    public static int DEFAULT_WIDTH = 800;
    public static int DEFAULT_HEIGHT = 250;
    private Model originalModel;
    private Model workingModel;
    private String pathOriginal;
    private String pathWorking;
    private NodeBNPair target;
    private List<NodeBNPair> sourceNodeBNPairs;
    private List<Scenario> scenarios;
    private List<SensitivityAnalysisScenarioData> scenarioData;
    public SensitivityAnalysisSettings settings = new SensitivityAnalysisSettings();
    public static int sourceCount = 0;
    public static boolean simulationSettingsChanged = false;

    public List<NodeBNPair> getSources() {
        return sourceNodeBNPairs;
    }

    public NodeBNPair getTarget() {
        return target;
    }

    public int getSourceCount() {
        sourceCount = sourceNodeBNPairs.size();
        return sourceCount;
    }

    public List<SensitivityAnalysisScenarioData> getScenarioData() {
        return scenarioData;
    }

    public Model getConnectedModel() {
        return workingModel;
    }

    public SensitivityAnalyser(Model m) throws FileHandlingException {

        originalModel = m;
        pathOriginal = m.getFilePathAbsolute();

        String temp_dir = FileHandler.generateRandomTempPath(true);
        String temp_file = FileHandler.generateRandomFileName(temp_dir, "sensitivity_", FileHandler.CMP_FILE_EXTENSION, true, true);
        String path_temp = temp_dir + temp_file;
        try {
            workingModel = Model.deepCopyInMemory(m);
        } catch (Exception e) {
            throw new FileHandlingException("Failed to create in-memory working copy for sensitivity analysis", e);
        }
        pathWorking = path_temp;

        this.scenarios = new ArrayList();
        this.scenarioData = new ArrayList<SensitivityAnalysisScenarioData>();
    }

    public void reset() {
        this.scenarios = new ArrayList();
        this.scenarioData = new ArrayList<SensitivityAnalysisScenarioData>();
        this.target = null;
        this.sourceNodeBNPairs = null;
    }

    public void setTarget(NodeBNPair target) {
        this.target = target;
    }

    public void setSources(List sources) {
        this.sourceNodeBNPairs = sources;
        getSourceCount();
    }

    public List getScenarios() {
        return this.scenarios;
    }

    public void addScenario(Scenario scenario) {
        this.scenarios.add(scenario);
        this.scenarioData.add(new SensitivityAnalysisScenarioData(scenario));
    }

    public boolean analyse() throws InconsistentEvidenceException, MessagePassingLinkException, PropagationException, PropagationTerminatedException, MinervaIndexException, MinervaRangeException, ExtendedBNException, CoreBNException, ScenarioNotFoundException, AnswerNotFoundException {
        Model.SMA = true;
        try {
            com.agenarisk.api.model.Model japiModel = com.agenarisk.api.model.Model.createModel(workingModel);

            japiModel.factorize();

            com.agenarisk.api.model.DataSet ds = japiModel.getDataSet(scenarios.get(0).getName().getShortDescription());

            japiModel.calculate(
                    Arrays.asList(japiModel.getNetwork(target.getBN().getConnID())),
                    Arrays.asList(ds),
                    com.agenarisk.api.model.Model.CalculationFlag.WITH_ANCESTORS,
                    com.agenarisk.api.model.Model.CalculationFlag.KEEP_TAILS_ZERO_REGIONS
            );

            japiModel.convertToStatic(
                    ds,
                    com.agenarisk.api.model.Model.ConversionFlag.IgnoreErrors,
                    com.agenarisk.api.model.Model.ConversionFlag.SkipTableRegeneration
            );

            japiModel = com.agenarisk.api.model.Model.createModel(
                    japiModel.export(
                            com.agenarisk.api.model.Model.ExportFlag.KEEP_OBSERVATIONS,
                            com.agenarisk.api.model.Model.ExportFlag.KEEP_META
                    )
            );
            workingModel = japiModel.getLogicModel();
        } catch (Exception ex) {
            Model.SMA = false;
            throw new PropagationException(ex);
        }

        target = new NodeBNPair(workingModel.findCorrespondingBN(target.getBN()), workingModel.findCorrespondingNode(target.getBN(), target.getNode()));

        List<NodeBNPair> sourcesBin = new ArrayList<>();
        for (NodeBNPair sbnp : sourceNodeBNPairs) {
            sourcesBin.add(new NodeBNPair(target.getBN(), workingModel.findCorrespondingNode(sbnp.getBN(), sbnp.getNode())));
        }
        sourceNodeBNPairs = sourcesBin;

        List<Scenario> scsBin = new ArrayList<>();
        for (int i = 0; i < this.scenarioData.size(); i++) {
            scsBin.add(workingModel.getScenarioWithName(scenarioData.get(i).scenario.getName().getShortDescription()));
        }

        this.scenarios.clear();
        this.scenarioData = new ArrayList<SensitivityAnalysisScenarioData>();

        for (Scenario sc : scsBin) {
            this.scenarios.add(sc);
            this.scenarioData.add(new SensitivityAnalysisScenarioData(sc));
        }

        Scenario scn = (Scenario) this.getScenarios().get(0);

        ExtendedBN targetBN = target.getBN();
        ExtendedNode targetNode = target.getNode();

        List<Scenario> allScenariosAvailableInModel = new ArrayList(workingModel.getScenarioList().getScenarios());

        workingModel.getScenarioList().removeAllScenarios();

        int noOfSources = sourceNodeBNPairs.size();

        if (!terminateProgressableTask) {
            SensitivityAnalysisScenarioData scenarioData = this.scenarioData.get(0);
            Scenario scenario = scenarioData.scenario;

            workingModel.addScenario(scenario, true, targetBN);
            List<Observation> observationsOnSources = new ArrayList<Observation>();
            if (!terminateProgressableTask) {
                clearObservations(scenario, targetNode, targetBN, observationsOnSources);
            }
            if (!terminateProgressableTask) {
                workingModel.propagateDDAlgorithm(Arrays.asList(scenario), Arrays.asList(target.getBN()), PropagationFlag.WITH_ANCESTORS, PropagationFlag.KEEP_TAILS_ZERO_REGIONS);
                if (targetBN.inconsistentEvidenceWarningGiven) {
                    return false;
                }
            }

            ensureInputNodesHaveReceivedMarginals(workingModel, this.getTarget().getBN(), scn.getId());

            try {
                if (Logger.isDebugMode()) {
                    workingModel.save(pathWorking);
                    Logger.logIfDebug("pathWorking " + pathWorking);
                    Files.copy(new File(pathWorking).toPath(), new File(Config.getDirectoryHomeAgenaRisk() + "SensitivityAnalysis_init.cmp").toPath(), StandardCopyOption.REPLACE_EXISTING);
                }
            } catch (Exception ex) {
                ex.printStackTrace(Logger.err());
            }

            updateCurrentProgress(5);
            initScenarioTargetDataSet(scenarioData, targetBN, targetNode, scenario);
            DataSet targetDataset = scenarioData.targetDataSet;
            List<ExtendedState> originalStates = null;
            boolean wasSimulationNode = false;
            if (ExtendedNode.isRealContinuous(targetNode)) {
                ContinuousEN cen = (ContinuousEN) targetNode;
                originalStates = new ArrayList<ExtendedState>(cen.getExtendedStates());
                wasSimulationNode = convertToNonSimulationNode(cen, targetDataset, wasSimulationNode, targetBN, scenario);
            }
            scenarioData.targetInitialStates = new ArrayList<ExtendedState>(targetNode.getExtendedStates());
            scenarioData.sourcesDataSet = new DataSet[noOfSources];
            scenarioData.sourcesDataSetForTornado = new DataSet[noOfSources];
            scenarioData.sourceInitialStates = new List[noOfSources];

            HashMap nodesUsed = new HashMap();
            nodesUsed.put(targetNode, originalStates);
            HashMap nodesUsedForDD = new HashMap();
            HashSet wasSimulation = new HashSet();

            for (int sourceIndex = 0; sourceIndex < noOfSources; sourceIndex++) {
                NodeBNPair nbpairSource = sourceNodeBNPairs.get(sourceIndex);
                MarginalDataItem myMdi = getMarginals(workingModel, nbpairSource.getBN(), nbpairSource.getNode(), scenario);
                scenarioData.sourcesDataSet[sourceIndex] = myMdi.getDataset();

                List cenOriginalStates = new ArrayList(nbpairSource.getNode().getExtendedStates());
                nodesUsed.put(nbpairSource.getNode(), cenOriginalStates);
                if (ExtendedNode.isRealContinuous(nbpairSource.getNode())) {
                    ContinuousEN cen = (ContinuousEN) nbpairSource.getNode();
                    boolean isSimulationNode = cen.isSimulationNode();
                    if (isSimulationNode) {
                        wasSimulation.add(cen);
                        ContinuousEN.ConvertToNonSimulation(cen, myMdi.getDataset(), nbpairSource.getBN(), scenario);
                    }
                }

                List iStates = new ArrayList(nbpairSource.getNode().getExtendedStates());
                scenarioData.sourceInitialStates[sourceIndex] = iStates;
                HashMap hm = new HashMap();
                hm.put(SensitivityAnalyser.MARGINALS, myMdi);
                hm.put(SensitivityAnalyser.INITIALSTATES, iStates);
                scenarioData.sourcesDetails.put(nbpairSource.getNode(), hm);
            }

            List<ExtendedNode> allnodes = targetBN.getExtendedNodes();
            for (int extendedNodeIndex = 0; extendedNodeIndex < allnodes.size(); extendedNodeIndex++) {
                ExtendedNode mynode = allnodes.get(extendedNodeIndex);
                if (nodesUsed.containsKey(mynode)) {
                    continue;
                } else {
                    try {
                        scenario.getObservation(targetBN.getId(), mynode.getId());
                        continue;
                    } catch (ObservationNotFoundException ex) {
                        nodesUsedForDD.put(mynode, new ArrayList(mynode.getExtendedStates()));
                    }
                }
                if (ExtendedNode.isRealContinuous(mynode)) {
                    MarginalDataItem myMdi = getMarginals(workingModel, targetBN, mynode, scenario);
                    ContinuousEN cenmynode = (ContinuousEN) mynode;
                    if (cenmynode.isSimulationNode()) {
                        wasSimulation.add(cenmynode);
                        ContinuousEN.ConvertToNonSimulation(cenmynode, myMdi.getDataset(), targetBN, scenario);
                    }
                }
            }

            try {
                if ((wasSimulationNode || wasSimulation.size() > 0) && (!terminateProgressableTask)) {
                    workingModel.getExtendedBNList().regenerateNPTforEveryExtendedNode(false);
                }
            } catch (NPTGeneratorInsufficientStateRangeException ex) {
            } catch (NPTGeneratorException ex) {
            }

            workingModel.propagateDDAlgorithm(Arrays.asList(scenario), Arrays.asList(target.getBN()), PropagationFlag.WITH_ANCESTORS, PropagationFlag.KEEP_TAILS_ZERO_REGIONS);

            try {
                if (Logger.isDebugMode()) {
                    workingModel.save(pathWorking);
                }
                if (Logger.isDebugMode()) {
                    Files.copy(new File(pathWorking).toPath(), new File(Config.getDirectoryHomeAgenaRisk() + "SensitivityAnalysis_static.cmp").toPath(), StandardCopyOption.REPLACE_EXISTING);
                }
            } catch (Exception ex) {
                ex.printStackTrace(Logger.err());
            }

            initScenarioTargetDataSet(scenarioData, targetBN, targetNode, scenario);
            if (ExtendedNode.isRealContinuous(targetNode)) {
                ContinuousEN cen = (ContinuousEN) targetNode;
                originalStates = new ArrayList<ExtendedState>(cen.getExtendedStates());
                if (!terminateProgressableTask) {
                    deriveBaseLineSummaryStatisticsFromDataSet(cen, targetDataset, scenarioData);
                }
            }
            scenarioData.targetInitialStates = new ArrayList<ExtendedState>(targetNode.getExtendedStates());
            scenarioData.sourcesDataSet = new DataSet[noOfSources];
            scenarioData.sourcesDataSetForTornado = new DataSet[noOfSources];
            scenarioData.sourceInitialStates = new List[noOfSources];
            for (int sourceIndex = 0; sourceIndex < noOfSources; sourceIndex++) {
                NodeBNPair nbpairSource = sourceNodeBNPairs.get(sourceIndex);
                MarginalDataItem myMdi = getMarginals(workingModel, nbpairSource.getBN(), nbpairSource.getNode(), scenario);
                scenarioData.sourcesDataSet[sourceIndex] = myMdi.getDataset();

                List cenOriginalStates = new ArrayList(nbpairSource.getNode().getExtendedStates());
                nodesUsed.put(nbpairSource.getNode(), cenOriginalStates);
                List iStates = new ArrayList(nbpairSource.getNode().getExtendedStates());
                scenarioData.sourceInitialStates[sourceIndex] = iStates;
                HashMap hm = new HashMap();
                hm.put(SensitivityAnalyser.MARGINALS, myMdi);
                hm.put(SensitivityAnalyser.INITIALSTATES, iStates);
                scenarioData.sourcesDetails.put(nbpairSource.getNode(), hm);
                scenarioData.sourcesDetailsOverride.put(nbpairSource.getNode(), new HashMap());
            }

            targetDataset = scenarioData.targetDataSet;

            updateCurrentProgress(10);

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

            final double perTargetIntitialState = 80.0 / scenarioData.targetInitialStates.size();
            if (!terminateProgressableTask) {
                final Model snapshotForStates;
                try {
                    snapshotForStates = Model.deepCopyInMemory(workingModel);
                } catch (Exception ex) {
                    throw new PropagationException("Failed to snapshot model for parallel sensitivity analysis", ex);
                }
                int nSAThreads = uk.co.agena.minerva.model.corebn.CoreBNJunctionTree.resolveThreadCount();
                java.util.concurrent.ExecutorService stateExec = java.util.concurrent.Executors.newFixedThreadPool(nSAThreads);
                List<java.util.concurrent.Future<?>> stateFutures = new ArrayList<>();

                final SensitivityAnalysisScenarioData fScnData = scenarioData;
                final int fTargetBNId = targetBN.getId();
                final int fTargetNodeId = targetNode.getId();
                final DataSet fTargetDataset = targetDataset;
                final String fScenarioName = scenario.getName().getShortDescription();
                final String fObsType = observationType;
                final boolean fProg = programatic;
                final List<NodeBNPair> fSources = new ArrayList<>(sourceNodeBNPairs);
                final ExtendedNode fTargetNode = targetNode;
                final SensitivityAnalyser fSA = this;

                for (int i = 0; i < scenarioData.targetInitialStates.size(); i++) {
                    final ExtendedState es = (ExtendedState) scenarioData.targetInitialStates.get(i);
                    final DataPoint dp = fTargetDataset.getDataPointAtOrderPosition(i);
                    stateFutures.add(stateExec.submit(new java.util.concurrent.Callable<Void>() {
                        public Void call() throws Exception {
                            if (fSA.terminateProgressableTask) return null;
                            if (dp.getValue() <= 1e-12) {
                                fSA.updateCurrentProgress(perTargetIntitialState);
                                return null;
                            }
                            Model tm = Model.deepCopyInMemory(snapshotForStates);
                            ExtendedBN tBN = tm.getExtendedBN(fTargetBNId);
                            ExtendedNode tNode = tBN.getExtendedNode(fTargetNodeId);
                            Scenario tScn = tm.getScenarioWithName(fScenarioName);
                            if (fProg) {
                                Model.generateObservation(fObsType, "" + es.getNumericalValue(), tScn, tNode, tBN, tm);
                            } else {
                                Model.generateObservation(fObsType, es.getName().getShortDescription(), tScn, tNode, tBN, tm);
                            }
                            tm.propagateDDAlgorithm(Arrays.asList(tScn), Arrays.asList(tBN), PropagationFlag.WITH_ANCESTORS, PropagationFlag.KEEP_TAILS_ZERO_REGIONS);
                            if (!tBN.inconsistentEvidenceWarningGiven) {
                                for (int j = 0; j < fSources.size(); j++) {
                                    NodeBNPair origSrcNBPair = fSources.get(j);
                                    ExtendedNode origSrcNode = origSrcNBPair.getNode();
                                    ExtendedNode tSrcNode = tBN.getExtendedNode(origSrcNode.getId());
                                    DataSet srcOriginalDataSet = fScnData.sourcesDataSet[j];
                                    HashMap currentSrcDetails = (HashMap) fScnData.sourcesDetails.get(origSrcNode);
                                    HashMap currentSrcDetailsOverride = fScnData.sourcesDetailsOverride.get(origSrcNode);
                                    List srcInitialStates = fScnData.sourceInitialStates[j];
                                    MarginalDataItem subjectiveSourceMDI = getMarginals(tm, tBN, tSrcNode, tScn);
                                    DataSet subjectiveSourceDataset = subjectiveSourceMDI.getDataset();
                                    List subjectSourceDataPoints = subjectiveSourceDataset.getDataPoints();
                                    List originalSourceDataPoints = srcOriginalDataSet.getDataPoints();
                                    for (int k = 0; k < subjectSourceDataPoints.size(); k++) {
                                        DataPoint pOfSource = (DataPoint) originalSourceDataPoints.get(k);
                                        ExtendedState srcCurrentState = (ExtendedState) srcInitialStates.get(k);
                                        DataPoint cdp = (DataPoint) subjectSourceDataPoints.get(k);
                                        double value = cdp.getValue() * dp.getValue();
                                        double reverseActual = value / pOfSource.getValue();
                                        double reverse = Double.isNaN(reverseActual) ? 0 : reverseActual;
                                        synchronized (currentSrcDetailsOverride) {
                                            fSA.setValueOf(currentSrcDetailsOverride, origSrcNode, srcCurrentState, es, cdp.getValue(), false);
                                            fSA.setValueOf(currentSrcDetailsOverride, fTargetNode, es, srcCurrentState, reverseActual, false);
                                        }
                                        if (value > 1e-16) {
                                            synchronized (currentSrcDetails) {
                                                fSA.setValueOf(currentSrcDetails, origSrcNode, srcCurrentState, es, cdp.getValue(), false);
                                                fSA.setValueOf(currentSrcDetails, fTargetNode, es, srcCurrentState, reverse, false);
                                            }
                                        }
                                    }
                                }
                            }
                            fSA.updateCurrentProgress(perTargetIntitialState);
                            return null;
                        }
                    }));
                }
                stateExec.shutdown();
                try {
                    stateExec.awaitTermination(Long.MAX_VALUE, java.util.concurrent.TimeUnit.SECONDS);
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                }
                for (java.util.concurrent.Future<?> f : stateFutures) {
                    try { f.get(); }
                    catch (java.util.concurrent.ExecutionException ex) { ex.getCause().printStackTrace(Logger.err()); }
                    catch (InterruptedException ex) { Thread.currentThread().interrupt(); }
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

            updateCurrentProgress(5);
            this.workingModel.getScenarioList().removeScenario(scenario);
        }

        for (int i = 0; i < allScenariosAvailableInModel.size(); i++) {
            this.workingModel.addScenario((Scenario) allScenariosAvailableInModel.get(i), true, targetBN);
        }

        if (terminateProgressableTask) {
            return false;
        }

        calculateSummaryStatistics();

        Model.SMA = false;

        return true;
    }

    public void resetsourcetarget(List source, NodeBNPair target2) {
        target = target2;
        sourceNodeBNPairs.clear();
        sourceNodeBNPairs = source;
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
                        if (mplinks.getIterationMessagePassingLink() instanceof ConstantMessagePassingLink) {
                            continue;
                        }
                        int listenerExtBnId = mplinks.getIterationChildExtendedBNId();
                        if (listenerExtBnId != extendedBN.getId()) {
                            continue;
                        }
                        int listenerExtNodeId = mplinks.getIterationChildExtendedNodeId();
                        if (listenerExtNodeId != inputNodeX.getId()) {
                            continue;
                        }
                        int senderExtBnId = mplinks.getIterationParentExtendedBNId();
                        ExtendedBN sourceBN = model.getExtendedBNList().getExtendedBN(senderExtBnId);
                        int senderExtendedNodeId = mplinks.getIterationParentExtendedNodeId();
                        ExtendedNode sourceNode = sourceBN.getExtendedNode(senderExtendedNodeId);
                        outputNode = sourceNode;
                        outputBN = sourceBN;
                        break;
                    }
                    if (outputNode != null) {
                        break;
                    }
                }
                boolean printWarningMessage = false;
                if (outputNode != null) {
                    try {
                        MarginalDataItemList mdil = model.getMarginalDataStore().getMarginalDataItemListForNode(outputBN, outputNode);
                        MarginalDataItem mdi = (MarginalDataItem) mdil.getMarginalDataItems().get(scenarioCounter);
                        DataSet ds = mdi.getDataset();
                        if (!ds.getDataPoints().isEmpty()) {
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

    private void clearObservations(Scenario scenario, ExtendedNode targetNode, ExtendedBN targetBN, List<Observation> observationsOnSources) throws ExtendedStateNotFoundException, AnswerNotFoundException {
        Model.clearObservation(scenario, targetNode, targetBN, this.workingModel);
        clearObservationsOnModel(scenario, observationsOnSources);
    }

    private boolean convertToNonSimulationNode(ContinuousEN cen, DataSet targetDataset, boolean wasSimulationNode, ExtendedBN ebn, Scenario scenario) throws ExtendedStateException, ExtendedStateNumberingException {
        if (cen.isSimulationNode()) {
            wasSimulationNode = true;
            ContinuousEN.ConvertToNonSimulation(cen, targetDataset, ebn, scenario);
        }
        return wasSimulationNode;
    }

    private void initScenarioTargetDataSet(SensitivityAnalysisScenarioData scenarioData, ExtendedBN targetBN, ExtendedNode targetNode, Scenario scenario) {
        scenarioData.targetDataSet = (SensitivityAnalyser.getMarginals(this.workingModel, targetBN, targetNode, scenario)).getDataset();
    }

    private void clearObservationsOnModel(Scenario scenario, List<Observation> observationsOnSources) throws AnswerNotFoundException, ExtendedStateNotFoundException {
        for (int i = 0; i < sourceNodeBNPairs.size(); i++) {
            NodeBNPair nbpair = sourceNodeBNPairs.get(i);
            try {
                Observation obs = scenario.getObservation(nbpair.getBN().getId(), nbpair.getNode().getId());
                observationsOnSources.add(obs);
                Model.clearObservation(scenario, nbpair.getNode(), nbpair.getBN(), workingModel);
            } catch (ObservationNotFoundException e) {
            }
        }
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
        for (int i = 0; i < scenarioData.size(); i++) {
            SensitivityAnalysisScenarioData scnData = (SensitivityAnalysisScenarioData) scenarioData.get(i);
            deriveSummaryStatistics(scnData.targetInitialStates, scnData);
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
            DataPoint dp = scnData.targetDataSet.getDataPointAtOrderPosition(cexi);
            if (scnData.targetDataSet.getDataPointAtOrderPosition(cexi).getValue() == 0) {
                continue;
            }
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
    }

    public static double getValueOf(HashMap sourcesDetails, HashMap sourcesDetailsOverride, ExtendedNode a, ExtendedState a1, ExtendedState b1, boolean searchZero) {
        double value = 0;
        String key = a.getName().getShortDescription() + SEPARATOR + a1.getName().getShortDescription() + SEPARATOR + b1.getName().getShortDescription() + (searchZero ? WITH_ZERO : "");
        String keyZero = (String) sourcesDetails.get(key);

        if (keyZero == null && sourcesDetailsOverride != null) {
            String keyZeroOverride = (String) sourcesDetailsOverride.get(key);
            if (keyZeroOverride != null) {
                keyZero = keyZeroOverride;
            }
        }

        if (keyZero == null) {
            Logger.logIfDebug("zero for target state " + key);
            return 0;
        }

        value = Double.parseDouble(keyZero);
        return value;
    }

    private void setValueOf(HashMap sourcesDetails, ExtendedNode a, ExtendedState a1, ExtendedState b1, double value, boolean searchZero) {
        sourcesDetails.put(a.getName().getShortDescription() + SEPARATOR + a1.getName().getShortDescription() + SEPARATOR + b1.getName().getShortDescription() + (searchZero ? WITH_ZERO : ""), "" + value);
    }

    private void deriveSummaryStatistics(List targetInitialStates, SensitivityAnalysisScenarioData scnData) throws MinervaRangeException, MinervaIndexException {
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
            HashMap srcDetailsOverride = scnData.sourcesDetailsOverride.get(enode);

            if (!(target.getNode() instanceof ContinuousEN)) {
                // do nothing here
            } else {
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

                        if (scnData.targetDataSet.getDataPointAtOrderPosition(targetInitialStatesIter).getValue() == 0) {
                            continue;
                        }

                        double dbl = getValueOf(srcDetails, srcDetailsOverride, target.getNode(), trState, srcState, false);
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
    }

    private void setValueOf(HashMap srcDetails, String summaryStatistic, ExtendedState stName, double value, boolean withZero) {
        String extra = (withZero ? WITH_ZERO : "");
        srcDetails.put(summaryStatistic + SEPARATOR + stName.getName().getShortDescription() + extra, "" + value);
    }

    public static double getValueOf(HashMap srcDetails, String summaryStatistic, ExtendedState stName, boolean withZero) {
        double value = 0;
        String extra = (withZero ? WITH_ZERO : "");
        String key = summaryStatistic + SEPARATOR + stName.getName().getShortDescription() + extra;
        value = Double.parseDouble((String) srcDetails.get(key));
        return value;
    }

    public static MarginalDataItem getMarginals(Model model, ExtendedBN ebn, ExtendedNode enode, Scenario scn) {
        MarginalDataItemList mdil = model.getMarginalDataStore().getMarginalDataItemListForNode(ebn, enode);
        MarginalDataItem mdi = null;
        int sci = 0;
        for (int i = 0; i < model.getScenarioList().getScenarios().size(); i++) {
            if (model.getScenarioAtIndex(i).getName().getShortDescription().equals(scn.getName().getShortDescription())) {
                sci = i;
                break;
            }
        }
        mdi = mdil.getMarginalDataItemAtIndex(sci);
        return mdi;
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

    public void generateSourceStatistics(String type, SensitivityAnalysisScenarioData scnData, boolean useZero, List orderedEN) {
        List sources = getSources();

        double baseline = 0;
        if (type.equals(SensitivityAnalyser.MEAN))
            baseline = scnData.baselineMean;
        else if (type.equals(SensitivityAnalyser.MEDIAN))
            baseline = scnData.baselineMedian;
        else if (type.equals(SensitivityAnalyser.VARIANCE))
            baseline = scnData.baselineVariance;
        else if (type.equals(SensitivityAnalyser.STANDARD_DEVIATION))
            baseline = scnData.baselineSD;
        else if (type.equals(SensitivityAnalyser.UPPER_PERCENTILE))
            baseline = scnData.baselineUP;
        else if (type.equals(SensitivityAnalyser.LOWER_PERCENTILE))
            baseline = scnData.baselineLP;

        List listHighs = new ArrayList();
        List listLows = new ArrayList();
        List listDiffs = new ArrayList();
        List enodes = new ArrayList();

        for (int i = 0; i < sources.size(); i++) {
            double high = Double.NaN, low = Double.NaN;
            NodeBNPair nbpair = (NodeBNPair) sources.get(i);
            ExtendedNode enode = nbpair.getNode();
            enodes.add(enode);

            HashMap srcDetail = (HashMap) scnData.sourcesDetails.get(enode);
            List extendedStates = scnData.sourceInitialStates[i];
            boolean set = false;
            for (int j = 0; j < extendedStates.size(); j++) {
                ExtendedState estate = (ExtendedState) extendedStates.get(j);
                double dpVal = SensitivityAnalyser.getValueOf(srcDetail, type, estate, useZero);

                if (Double.isNaN(dpVal))
                    continue;

                if (dpVal == 0) {
                    Logger.out().println(estate.getName().getShortDescription());
                }

                if (!set) {
                    set = true;
                    high = dpVal;
                    low = dpVal;
                }

                if (Double.isNaN(dpVal))
                    continue;

                if (dpVal > high)
                    high = dpVal;
                if (dpVal < low)
                    low = dpVal;
            }

            double diff = high - low;
            listHighs.add(new Double(high));
            listLows.add(new Double(low));
            listDiffs.add(new Double(diff));
        }

        double[] highs = new double[listHighs.size()];
        double[] lows = new double[listLows.size()];
        String[] cats = new String[highs.length];
        int counter = 0;

        while (enodes.size() > 0) {
            double biggest = 0;
            int biggestIndex = 0;
            for (int i = 0; i < enodes.size(); i++) {
                double cValue = ((Double) listDiffs.get(i)).doubleValue();
                if (i == 0)
                    biggest = cValue;
                if (cValue > biggest) {
                    biggest = cValue;
                    biggestIndex = i;
                }
            }

            ExtendedNode enode = (ExtendedNode) enodes.get(biggestIndex);
            highs[counter] = ((Double) listHighs.get(biggestIndex)).doubleValue();
            lows[counter] = ((Double) listLows.get(biggestIndex)).doubleValue();
            cats[counter] = enode.getName().getShortDescription();
            orderedEN.add(new Object[]{enode, "" + highs[counter], "" + lows[counter]});
            counter++;

            enodes.remove(biggestIndex);
            listDiffs.remove(biggestIndex);
            listHighs.remove(biggestIndex);
            listLows.remove(biggestIndex);
        }
    }

    public static ArrayList SensitivitySimulationSettings = new ArrayList(5);

    public void setSimulationSettings(Model m) {
        m.setSimulationEntropyConvergenceTolerance((Double) SensitivitySimulationSettings.get(0));
        m.setSimulationEvidenceTolerancePercent((Double) SensitivitySimulationSettings.get(1));
        m.setSimulationLogging((Boolean) SensitivitySimulationSettings.get(2));
        m.setSimulationNoOfIterations((Integer) SensitivitySimulationSettings.get(3));
        m.setRankedSampleSize((Integer) SensitivitySimulationSettings.get(4));
    }
}
