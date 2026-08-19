package com.agenarisk.api.tools.voi;

import java.text.NumberFormat;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import uk.co.agena.minerva.util.model.NodeBNPair;
import uk.co.agena.minerva.model.MarginalDataItem;
import uk.co.agena.minerva.model.MarginalDataItemList;
import uk.co.agena.minerva.model.MessagePassingLinkException;
import uk.co.agena.minerva.model.Model;
import uk.co.agena.minerva.model.PropagationException;
import uk.co.agena.minerva.model.PropagationTerminatedException;
import uk.co.agena.minerva.model.corebn.CoreBNException;
import uk.co.agena.minerva.model.extendedbn.ContinuousEN;
import uk.co.agena.minerva.model.extendedbn.ContinuousIntervalEN;
import uk.co.agena.minerva.model.extendedbn.ExtendedBN;
import uk.co.agena.minerva.model.extendedbn.ExtendedBNException;
import uk.co.agena.minerva.model.extendedbn.ExtendedNode;
import uk.co.agena.minerva.model.extendedbn.ExtendedState;
import uk.co.agena.minerva.model.extendedbn.ExtendedStateException;
import uk.co.agena.minerva.model.extendedbn.ExtendedStateNumberingException;
import uk.co.agena.minerva.model.extendedbn.InconsistentEvidenceException;
import uk.co.agena.minerva.model.extendedbn.IntegerIntervalEN;
import uk.co.agena.minerva.model.extendedbn.RankedEN;
import uk.co.agena.minerva.model.questionnaire.AnswerNotFoundException;
import uk.co.agena.minerva.model.scenario.ObservationNotFoundException;
import uk.co.agena.minerva.model.scenario.Scenario;
import uk.co.agena.minerva.model.scenario.ScenarioNotFoundException;
import uk.co.agena.minerva.util.io.FileHandlingException;
import uk.co.agena.minerva.util.model.DataPoint;
import uk.co.agena.minerva.util.model.MinervaIndexException;
import uk.co.agena.minerva.util.model.MinervaRangeException;
import uk.co.agena.minerva.util.model.Progressable;
import uk.co.agena.minerva.util.nptgenerator.NPTGeneratorException;
import uk.co.agena.minerva.util.nptgenerator.NPTGeneratorInsufficientStateRangeException;
import uk.co.agena.minerva.util.StreamInterceptor;
import uk.co.agena.minerva.model.Model.PropagationFlag;
import uk.co.agena.minerva.util.Config;

/**
 * Performs Value of Information Analysis.
 * Compute-only: no file I/O, no Swing.
 * Use {@link VoiReportWriter} to generate the HTML report string after analysis.
 *
 * @author Lukasz Radlinski
 */
public class VoiAnalyser implements Progressable {

    public final Model model;
    public final Scenario scenario;
    public final List<Scenario> listOfScenarios = new ArrayList<>(1);
    public final NodeBNPair decisionNode;
    public final List<NodeBNPair> uncertaintyNodes;
    public final NodeBNPair utilityNode;
    public final boolean maximiseUtility;

    private int scenarioIndex;

    public final Map<Integer, Double> emvs = new HashMap();
    public final Map<Integer, Map<Integer, Double>>[] evpis;

    public double emv;
    private final double[][] priors;
    double[][] evpiExtreme;
    public double evpi[];
    public String evpiEquation[];
    public double evppi[];
    public String evppiEquation[];

    NumberFormat numberFormat = NumberFormat.getInstance();

    private int lengthOfProgressableTask = 100;
    private int progress;
    private boolean progressableTaskDone;
    private boolean terminateProgressableTask;
    private boolean lastCalculationNotCompleted;

    public ZonedDateTime startTime;
    public ZonedDateTime endTime;

    static final int PROGRESS_START = 1;
    static final int PROGRESS_CONVERT_TO_FIXED = 2;
    static final int PROGRESS_GET_PRIORS = 3;
    static final int PROGRESS_CALCULATE_EMV = 4;
    static final int PROGRESS_CALCULATE_EVPI = 5;

    {
        Locale.setDefault(Locale.ENGLISH);
        int decPlaces = 3;
        numberFormat.setGroupingUsed(false);
        numberFormat.setMaximumFractionDigits(decPlaces);
        numberFormat.setMinimumFractionDigits(0);
    }

    public VoiAnalyser(Model model, Scenario scenario, NodeBNPair decisionNode,
                       List<NodeBNPair> uncertaintyNodes, NodeBNPair utilityNode,
                       boolean maximiseUtility) throws ExtendedBNException {
        this.model = model;
        this.scenario = scenario;
        this.decisionNode = decisionNode;
        this.uncertaintyNodes = uncertaintyNodes;
        this.utilityNode = utilityNode;
        this.maximiseUtility = maximiseUtility;
        this.priors = new double[uncertaintyNodes.size()][];
        this.evpiExtreme = new double[uncertaintyNodes.size()][];
        this.evpi = new double[uncertaintyNodes.size()];
        this.evpiEquation = new String[uncertaintyNodes.size()];
        this.evppi = new double[uncertaintyNodes.size()];
        this.evppiEquation = new String[uncertaintyNodes.size()];
        this.evpis = new HashMap[uncertaintyNodes.size()];
        for (int i = 0; i < uncertaintyNodes.size(); i++) {
            this.evpis[i] = new HashMap();
        }
        listOfScenarios.add(scenario);
        calculateMaxProgress();
    }

    public boolean analyse(boolean useSingeSimulation) throws MessagePassingLinkException,
            PropagationException, PropagationTerminatedException, MinervaIndexException,
            MinervaRangeException, ExtendedBNException, InconsistentEvidenceException,
            CoreBNException, ScenarioNotFoundException, AnswerNotFoundException,
            ExtendedStateNumberingException, ExtendedStateException, FileHandlingException,
            NPTGeneratorInsufficientStateRangeException, NPTGeneratorException, VoiAnalysisException {
        startTime = ZonedDateTime.now();
        prepareAnalysis();
        updateProgress(PROGRESS_START, 1);
        convertToFixedDiscretisation(useSingeSimulation);
        getPriorsForUncertaintyNodes();
        calculateEMV();
        for (int i = 0; i < uncertaintyNodes.size(); i++) {
            calculateEVPI(i);
            calculateEVPPI(i);
        }
        endTime = ZonedDateTime.now();
        progressableTaskDone = true;
        return !terminateProgressableTask;
    }

    private void convertToFixedDiscretisation(boolean convertToFixedDiscretisation)
            throws ExtendedStateNumberingException, ExtendedStateException,
            MessagePassingLinkException, ExtendedBNException, InconsistentEvidenceException,
            PropagationException, PropagationTerminatedException, FileHandlingException,
            MinervaRangeException, NPTGeneratorInsufficientStateRangeException, NPTGeneratorException {
        if (terminateProgressableTask) { lastCalculationNotCompleted = true; return; }

        List<NodeBNPair> convertibleNodeList = new ArrayList();
        for (NodeBNPair uncertaintyNode : uncertaintyNodes) {
            if (ExtendedNode.isRealContinuous(uncertaintyNode.getNode())
                    && ((ContinuousEN) uncertaintyNode.getNode()).isDynamicallyDiscretisable()
                    && !uncertaintyNode.getNode().isConnectableInputNode()) {
                convertibleNodeList.add(uncertaintyNode);
            }
        }
        if (ExtendedNode.isRealContinuous(decisionNode.getNode())
                && ((ContinuousEN) decisionNode.getNode()).isDynamicallyDiscretisable()
                && !decisionNode.getNode().isConnectableInputNode()) {
            convertibleNodeList.add(decisionNode);
        }

        if (!convertToFixedDiscretisation && convertibleNodeList.isEmpty()) {
            updateProgress(PROGRESS_CONVERT_TO_FIXED, 1);
            return;
        }

        String message = calculateModelSilently(model);
        if (!model.isLastPropagationSuccessful()) {
            throw new PropagationException(
                    "Error during converting simulation nodes to fixed discretisation:\n" + message);
        }

        if (terminateProgressableTask) { lastCalculationNotCompleted = true; return; }

        boolean wasSimulation = false;
        if (convertibleNodeList.isEmpty()) {
            for (ExtendedBN ebn : (List<ExtendedBN>) model.getExtendedBNList().getExtendedBNs()) {
                ((List<ExtendedNode>) ebn.getExtendedNodes())
                        .stream()
                        .map(node -> new NodeBNPair(ebn, node))
                        .map(convertibleNodeList::add);
            }
        }

        for (NodeBNPair nodeBNPair : convertibleNodeList) {
            if (observationExistsForNode(nodeBNPair)) continue;
            if (ExtendedNode.isRealContinuous(nodeBNPair.getNode())
                    && ((ContinuousEN) nodeBNPair.getNode()).isDynamicallyDiscretisable()
                    && !nodeBNPair.getNode().isConnectableInputNode()) {
                MarginalDataItem myMdi = getMarginals(model, nodeBNPair.getBN(), nodeBNPair.getNode(), scenario);
                ContinuousEN cen = (ContinuousEN) nodeBNPair.getNode();
                if (cen.isSimulationNode()) {
                    ContinuousEN.ConvertToNonSimulation(cen, myMdi.getDataset(), nodeBNPair.getBN(), scenario);
                    wasSimulation = true;
                }
            }
        }

        if (wasSimulation && !terminateProgressableTask) {
            model.getExtendedBNList().regenerateNPTforEveryExtendedNode(false);
        }

        model.save(Config.getDirectoryHomeAgenaRisk() + System.getProperty("file.separator") + "VoIModel.cmp");
        updateProgress(PROGRESS_CONVERT_TO_FIXED, 1);
    }

    private void getPriorsForUncertaintyNodes() throws ExtendedBNException,
            MessagePassingLinkException, InconsistentEvidenceException,
            PropagationException, PropagationTerminatedException {
        if (terminateProgressableTask) { lastCalculationNotCompleted = true; return; }

        String message = calculateModelSilently(model);
        if (!model.isLastPropagationSuccessful()) {
            throw new PropagationException(
                    "Error during calculating prior probabilities for uncertainty node(s):\n" + message);
        }

        for (int uncertaintyNodeNumber = 0; uncertaintyNodeNumber < uncertaintyNodes.size(); uncertaintyNodeNumber++) {
            NodeBNPair uncertaintyNode = uncertaintyNodes.get(uncertaintyNodeNumber);
            List marginals = model.getMarginalDataStore()
                    .getMarginalDataItemListForNode(uncertaintyNode.getBN(), uncertaintyNode.getNode())
                    .getMarginalDataItemAtIndex(scenarioIndex)
                    .getDataset()
                    .getDataPoints();
            priors[uncertaintyNodeNumber] = new double[marginals.size()];
            for (int i = 0; i < marginals.size(); i++) {
                priors[uncertaintyNodeNumber][i] = ((DataPoint) marginals.get(i)).getValue();
            }
        }
        updateProgress(PROGRESS_GET_PRIORS, 1);
    }

    private void calculateEMV() throws PropagationException, MessagePassingLinkException,
            PropagationTerminatedException, ExtendedBNException {
        if (terminateProgressableTask) { lastCalculationNotCompleted = true; return; }

        int length = decisionNode.getNode().getExtendedStates().size();
        for (int i = 0; i < length; i++) {
            clearObservations();
            ExtendedState state = decisionNode.getNode().getExtendedStateAtIndex(i);
            if (decisionNode.getNode() instanceof ContinuousIntervalEN) {
                scenario.addRealObservation(decisionNode.getBN().getId(), decisionNode.getNode().getId(), state.getNumericalValue());
            } else if (decisionNode.getNode() instanceof IntegerIntervalEN) {
                scenario.addIntegerObservation(decisionNode.getBN().getId(), decisionNode.getNode().getId(), (int) state.getNumericalValue());
            } else {
                scenario.addHardEvidenceObservation(decisionNode.getBN().getId(), decisionNode.getNode().getId(), state.getId());
            }

            String message = calculateModelSilently(model);
            if (!model.isLastPropagationSuccessful()) {
                throw new PropagationException("Error during calculating model for EMV:\n" + message);
            }
            emvs.put(state.getId(), getMean(utilityNode));

            if (terminateProgressableTask) { lastCalculationNotCompleted = true; return; }
            updateProgress(PROGRESS_CALCULATE_EMV, (i + 1) * 100d / length);
        }
        emv = getExtremeValue(emvs);
    }

    private void calculateEVPI(int uncertaintyNodeNumber) throws ExtendedBNException,
            MessagePassingLinkException, InconsistentEvidenceException,
            PropagationException, PropagationTerminatedException {
        NodeBNPair uncertaintyNode = uncertaintyNodes.get(uncertaintyNodeNumber);
        if (terminateProgressableTask) { lastCalculationNotCompleted = true; return; }

        evpiExtreme[uncertaintyNodeNumber] = new double[uncertaintyNode.getNode().getExtendedStates().size()];
        int length = uncertaintyNode.getNode().getExtendedStates().size()
                * decisionNode.getNode().getExtendedStates().size();
        int localProgress = 0;

        for (int i = 0; i < uncertaintyNode.getNode().getExtendedStates().size(); i++) {
            clearObservations();
            ExtendedState uncertaintyState = uncertaintyNode.getNode().getExtendedStateAtIndex(i);
            if (uncertaintyNode.getNode() instanceof ContinuousIntervalEN) {
                scenario.addRealObservation(uncertaintyNode.getBN().getId(), uncertaintyNode.getNode().getId(), uncertaintyState.getNumericalValue());
            } else if (uncertaintyNode.getNode() instanceof IntegerIntervalEN) {
                scenario.addIntegerObservation(uncertaintyNode.getBN().getId(), uncertaintyNode.getNode().getId(), (int) uncertaintyState.getNumericalValue());
            } else {
                scenario.addHardEvidenceObservation(uncertaintyNode.getBN().getId(), uncertaintyNode.getNode().getId(), uncertaintyState.getId());
            }

            evpis[uncertaintyNodeNumber].put(uncertaintyState.getId(), new HashMap());

            for (int j = 0; j < decisionNode.getNode().getExtendedStates().size(); j++) {
                ExtendedState decisionState = decisionNode.getNode().getExtendedStateAtIndex(j);
                if (decisionNode.getNode() instanceof ContinuousIntervalEN) {
                    scenario.addRealObservation(decisionNode.getBN().getId(), decisionNode.getNode().getId(), decisionState.getNumericalValue());
                } else if (decisionNode.getNode() instanceof IntegerIntervalEN) {
                    scenario.addIntegerObservation(decisionNode.getBN().getId(), decisionNode.getNode().getId(), (int) decisionState.getNumericalValue());
                } else {
                    scenario.addHardEvidenceObservation(decisionNode.getBN().getId(), decisionNode.getNode().getId(), decisionState.getId());
                }

                String message = calculateModelSilently(model);
                if (!model.isLastPropagationSuccessful()) {
                    evpis[uncertaintyNodeNumber].get(uncertaintyState.getId()).put(decisionState.getId(),
                            maximiseUtility ? Double.NEGATIVE_INFINITY : Double.POSITIVE_INFINITY);
                } else {
                    evpis[uncertaintyNodeNumber].get(uncertaintyState.getId()).put(decisionState.getId(), getMean(utilityNode));
                }

                if (terminateProgressableTask) { lastCalculationNotCompleted = true; return; }
                localProgress++;
                updateProgress(PROGRESS_CALCULATE_EVPI,
                        ((localProgress * 100d / length) + uncertaintyNodeNumber * 100) / uncertaintyNodes.size());
            }

            evpiExtreme[uncertaintyNodeNumber][i] = getExtremeValue(evpis[uncertaintyNodeNumber].get(uncertaintyState.getId()));
        }

        evpi[uncertaintyNodeNumber] = 0;
        StringBuffer evpiBuffer = new StringBuffer("EV|PI =");
        for (int i = 0; i < priors[uncertaintyNodeNumber].length; i++) {
            if (priors[uncertaintyNodeNumber][i] == 0d
                    && (evpiExtreme[uncertaintyNodeNumber][i] == Double.NEGATIVE_INFINITY
                        || evpiExtreme[uncertaintyNodeNumber][i] == Double.POSITIVE_INFINITY)) {
                continue;
            }
            evpi[uncertaintyNodeNumber] += priors[uncertaintyNodeNumber][i] * evpiExtreme[uncertaintyNodeNumber][i];
            evpiBuffer.append(" ");
            if (i != 0) evpiBuffer.append("+ ");
            evpiBuffer.append(numberFormat.format(priors[uncertaintyNodeNumber][i]))
                    .append(" * ")
                    .append(numberFormat.format(evpiExtreme[uncertaintyNodeNumber][i]));
        }
        evpiBuffer.append(" = ").append(numberFormat.format(evpi[uncertaintyNodeNumber]));
        evpiEquation[uncertaintyNodeNumber] = evpiBuffer.toString();
    }

    private void calculateEVPPI(int uncertaintyNodeNumber) {
        if (terminateProgressableTask) { lastCalculationNotCompleted = true; return; }
        evppi[uncertaintyNodeNumber] = evpi[uncertaintyNodeNumber] - emv;
        StringBuffer evppiBuffer = new StringBuffer("EV(P)PI = ");
        evppiBuffer.append(numberFormat.format(evpi[uncertaintyNodeNumber]))
                .append(" - ")
                .append(numberFormat.format(emv));
        evppiEquation[uncertaintyNodeNumber] = evppiBuffer.toString();
    }

    @Override public int getCurrentProgress() { return progress; }
    @Override public int getLengthOfProgressableTask() { return lengthOfProgressableTask; }

    @Override
    public void terminateProgressableTask() {
        terminateProgressableTask = true;
        lastCalculationNotCompleted = true;
        progress = lengthOfProgressableTask;
    }

    @Override public boolean isProgressableTaskDone() { return progressableTaskDone; }

    @Override
    public void resetProgressableTask() {
        while (terminateProgressableTask && !progressableTaskDone) {
            try { Thread.sleep(100); } catch (InterruptedException e) {}
        }
        progress = 0;
        calculateMaxProgress();
        progressableTaskDone = false;
        terminateProgressableTask = false;
    }

    private void prepareAnalysis() throws ExtendedBNException, VoiAnalysisException {
        checkNodes();
        removeUnnecessaryScenarios();
        scenarioIndex = model.getScenarioList().getScenarios().indexOf(scenario);
        clearObservations();
        Model.checkMonitorsOpen = false;
    }

    private void checkNodes() throws ExtendedBNException, VoiAnalysisException {
        if (decisionNode == null) throw new VoiAnalysisException("Decision node has not been provided.");
        if (uncertaintyNodes.isEmpty()) throw new VoiAnalysisException("Uncertainty node has not been provided.");
        for (NodeBNPair uncertaintyNode : uncertaintyNodes) {
            if (uncertaintyNode == null) throw new VoiAnalysisException("Uncertainty node has not been provided.");
        }
        if (utilityNode == null) throw new VoiAnalysisException("Utility node has not been provided.");
        for (NodeBNPair uncertaintyNode : uncertaintyNodes) {
            if (decisionNode.getNode() == uncertaintyNode.getNode())
                throw new VoiAnalysisException("The node \"" + decisionNode + "\" cannot be both uncertainty node and decision node.");
            if (utilityNode.getNode() == uncertaintyNode.getNode())
                throw new VoiAnalysisException("The node \"" + utilityNode + "\" cannot be both uncertainty node and utility node.");
        }
        if (decisionNode.getNode() == utilityNode.getNode())
            throw new VoiAnalysisException("The node \"" + decisionNode + "\" cannot be both utility node and decision node.");
        if (!(utilityNode.getNode() instanceof ContinuousIntervalEN
                || utilityNode.getNode() instanceof IntegerIntervalEN
                || utilityNode.getNode() instanceof RankedEN)) {
            throw new VoiAnalysisException("Utility node may only be of type Continuous Interval, Integer Interval or Ranked.");
        }
    }

    private void removeUnnecessaryScenarios() {
        model.getScenarioList().removeAllScenarios();
        model.getScenarioList().addScenario(scenario);
    }

    private void clearObservations() {
        scenario.clearObservationsForNode(utilityNode.getBN().getId(), utilityNode.getNode().getId());
        scenario.clearObservationsForNode(decisionNode.getBN().getId(), decisionNode.getNode().getId());
        for (NodeBNPair uncertaintyNode : uncertaintyNodes) {
            scenario.clearObservationsForNode(uncertaintyNode.getBN().getId(), uncertaintyNode.getNode().getId());
        }
    }

    private double getMean(NodeBNPair nodeBNPair) {
        return model.getMarginalDataStore()
                .getMarginalDataItemListForNode(nodeBNPair.getBN(), nodeBNPair.getNode())
                .getMarginalDataItemAtIndex(scenarioIndex)
                .getMeanValue();
    }

    private void calculateMaxProgress() { lengthOfProgressableTask = 100; }

    private void updateProgress(int stage, double percentageProgressInStage) {
        switch (stage) {
            case PROGRESS_START:             progress = 1;  break;
            case PROGRESS_CONVERT_TO_FIXED:  progress = 5;  break;
            case PROGRESS_GET_PRIORS:        progress = 10; break;
            case PROGRESS_CALCULATE_EMV:     progress = (int)(10 + percentageProgressInStage * 0.1); break;
            case PROGRESS_CALCULATE_EVPI:    progress = (int)(20 + percentageProgressInStage * 0.8); break;
        }
    }

    public NodeBNPair getDecisionNode() { return decisionNode; }
    public List<NodeBNPair> getUncertaintyNodes() { return uncertaintyNodes; }
    public NodeBNPair getUtilityNode() { return utilityNode; }
    public boolean isMaximiseUtility() { return maximiseUtility; }

    private double getExtremeValue(Map<Integer, Double> emvs) {
        return emvs.values().stream()
                .collect(Collectors.reducing(maximiseUtility ? Double::max : Double::min)).get();
    }

    private boolean observationExistsForNode(NodeBNPair nodeBNPair) {
        try {
            scenario.getObservation(nodeBNPair.getBN().getId(), nodeBNPair.getNode().getId());
            return true;
        } catch (ObservationNotFoundException e) {
            return false;
        }
    }

    private String calculateModelSilently(Model model) {
        String returnMessage = "";
        String originalSuppressMessages = Model.suppressMessages;
        Model.suppressMessages = "system";
        StreamInterceptor.output_capture();
        try {
            List<ExtendedBN> bnList = new ArrayList(Arrays.asList(new ExtendedBN[]{
                this.decisionNode.getBN(), this.utilityNode.getBN()
            }));
            bnList.addAll((List<ExtendedBN>) uncertaintyNodes.stream()
                    .map(e -> e.getBN()).distinct().collect(Collectors.toList()));
            model.propagateDDAlgorithm(null, bnList, PropagationFlag.WITH_ANCESTORS, PropagationFlag.KEEP_TAILS_ZERO_REGIONS);
        } catch (Exception e) {
            returnMessage = e.getMessage();
        }
        String out = StreamInterceptor.output_release();
        if (!model.isLastPropagationSuccessful()) returnMessage = out.trim();
        Model.suppressMessages = originalSuppressMessages;
        return returnMessage;
    }

    public static MarginalDataItem getMarginals(Model model, ExtendedBN ebn, ExtendedNode enode, Scenario scn) {
        MarginalDataItemList mdil = model.getMarginalDataStore().getMarginalDataItemListForNode(ebn, enode);
        int sci = 0;
        for (int i = 0; i < model.getScenarioList().getScenarios().size(); i++) {
            if (model.getScenarioAtIndex(i).getName().getShortDescription().equals(scn.getName().getShortDescription())) {
                sci = i;
                break;
            }
        }
        return mdil.getMarginalDataItemAtIndex(sci);
    }
}
