package com.agenarisk.api.tools.hid;

import com.singularsys.jep.JepException;
import java.io.File;
import java.io.IOException;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.ListIterator;
import java.util.TreeMap;
import java.util.stream.Collectors;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import uk.co.agena.minerva.analysis.hid.d3dt.D3Node;
import uk.co.agena.minerva.analysis.hid.d3dt.DT;
import uk.co.agena.minerva.analysis.hid.d3dt.HIDStateInstance;
import uk.co.agena.minerva.analysis.hid.d3dt.HIDStateInstanceContinuous;
import uk.co.agena.minerva.analysis.hid.d3dt.HIDStateInstanceDiscrete;
import uk.co.agena.minerva.analysis.hid.d3dt.UtilityCalculationFunction;
import uk.co.agena.minerva.model.Model;
import uk.co.agena.minerva.model.corebn.CoreBN;
import uk.co.agena.minerva.model.extendedbn.ContinuousEN;
import uk.co.agena.minerva.model.extendedbn.ContinuousIntervalEN;
import uk.co.agena.minerva.model.extendedbn.ExtendedBN;
import uk.co.agena.minerva.model.extendedbn.ExtendedBNException;
import uk.co.agena.minerva.model.extendedbn.ExtendedNode;
import uk.co.agena.minerva.model.extendedbn.ExtendedNodeFunction;
import uk.co.agena.minerva.model.extendedbn.ExtendedState;
import uk.co.agena.minerva.model.extendedbn.InconsistentEvidenceException;
import uk.co.agena.minerva.model.extendedbn.IntegerIntervalEN;
import uk.co.agena.minerva.model.scenario.Observation;
import uk.co.agena.minerva.model.scenario.Scenario;
import uk.co.agena.minerva.model.scenario.ScenarioNotFoundException;
import uk.co.agena.minerva.util.Config;
import uk.co.agena.minerva.util.Environment;
import uk.co.agena.minerva.util.ICallback;
import uk.co.agena.minerva.util.Logger;
import uk.co.agena.minerva.util.StreamInterceptor;
import uk.co.agena.minerva.util.io.FileHandlingException;
import uk.co.agena.minerva.util.model.DataPoint;
import uk.co.agena.minerva.util.model.DataSet;
import uk.co.agena.minerva.util.model.MinervaIndexException;
import uk.co.agena.minerva.util.model.MinervaRangeException;
import uk.co.agena.minerva.util.model.NameDescription;
import uk.co.agena.minerva.util.model.Progressable;
import uk.co.agena.minerva.util.model.Range;
import uk.co.agena.minerva.util.nptgenerator.NPTGeneratorException;
import uk.co.agena.minerva.util.nptgenerator.NPTGeneratorInsufficientStateRangeException;
import uk.co.agena.minerva.util.tree.decision.DTLink;
import uk.co.agena.minerva.util.tree.decision.DTType;
import uk.co.agena.minerva.util.tree.decision.usf.IUtilitySelectionFunction;
import uk.co.agena.minerva.util.tree.decision.usf.USFMaximise;
import uk.co.agena.minerva.util.tree.decision.usf.USFMinimise;
import uk.co.agena.minerva.model.Model.PropagationFlag;

/**
 * Solves a Hybrid Influence Diagram as a Decision Tree.
 * Compute-only: no Swing, no JFreeChart, no file I/O.
 * Call {@link #getResult()} after a successful {@link #solveAsDT()} to obtain
 * a {@link HidResult} for report generation via {@link HidReportWriter}.
 *
 * @author Eugene Dementiev
 */
public class HidSolver implements Progressable {

    private Model model;
    public final ExtendedBN ebn;
    public final IUtilitySelectionFunction usf;
    public final UtilityCalculationFunction ucf;
    public final List<ExtendedNode> nodes_chance_observed;
    public final List<ExtendedNode> nodes_decision;
    public final List<ExtendedNode> nodes_utility;
    public final JSONObject cached_tree;
    public final int rounding_precision;
    public final boolean full_simulation;
    public final boolean simplify_dt;
    public final boolean ignore_assigned_observations;
    public final String model_file_name;
    public final boolean highlight_optimal_decisions;

    private final HashMap<ExtendedNode, List<ExtendedState>> node_original_states = new HashMap<>();
    private final ArrayList<Integer> assigned_nodes_ids = new ArrayList<>();

    private DT dt = null;
    private DTLink dta = null;
    private ArrayList<ExtendedNode> DTNodesOrder = new ArrayList<>();
    private ListIterator<ExtendedNode> DTNodesOrderIterator = DTNodesOrder.listIterator();
    private Scenario scenario = null;
    private int scenarioIndex = 0;
    private Scenario scenarioWithObservations = null;
    private List<Scenario> scenarioOriginalList = new ArrayList<>();
    private List<ExtendedNode> forcedStaticNodes = new ArrayList<>();
    private HashMap<ExtendedNode, ArrayList<HIDStateInstance>> rememberedStateInstances = new HashMap<>();

    private static final ArrayList<IUtilitySelectionFunction> usf_list = new ArrayList<>();

    private ZonedDateTime startTime;
    private ZonedDateTime endTime;
    private int lengthOfProgressableTask = 100;
    private int progress = 0;
    private boolean progressableTaskDone;
    private boolean terminateProgressableTask;

    private int calculations = 0;
    private long durationTotal = 0;
    private StringBuffer durationLog = new StringBuffer();

    private final List<ExtendedBN> ebnsToPropagate = new ArrayList<>();
    private final TreeMap<ExtendedNode, ExtendedNodeFunction> backup_functions = new TreeMap<>();
    private final TreeMap<ExtendedNode, List<ExtendedNode>> backup_parents = new TreeMap<>();
    private final TreeMap<ExtendedNode, Integer> backup_function_modes = new TreeMap<>();

    public HidSolver(
            Model model,
            ExtendedBN ebn,
            Scenario scenario,
            String filename,
            IUtilitySelectionFunction usf,
            UtilityCalculationFunction ucf,
            List<ExtendedNode> nodes_chance_observed,
            List<ExtendedNode> nodes_decision,
            List<ExtendedNode> nodes_utility,
            int rounding_precision,
            boolean full_simulation,
            boolean simplify_dt,
            boolean ignore_assigned_observations,
            boolean highlight_optimal_decisions,
            JSONObject cached_tree
    ) {
        this.model = model;
        this.ebn = ebn;
        this.model_file_name = filename;
        this.scenarioWithObservations = scenario;
        this.usf = usf;
        this.ucf = ucf;
        this.nodes_chance_observed = nodes_chance_observed;
        this.nodes_decision = nodes_decision;
        this.nodes_utility = nodes_utility;
        this.rounding_precision = rounding_precision;
        this.full_simulation = full_simulation;
        this.simplify_dt = simplify_dt;
        this.ignore_assigned_observations = ignore_assigned_observations;
        this.highlight_optimal_decisions = highlight_optimal_decisions;
        if (cached_tree == null) {
            cached_tree = new JSONObject();
            try {
                cached_tree.put("root", JSONObject.NULL);
            } catch (JSONException e) {
                e.printStackTrace(Logger.err());
            }
        }
        this.cached_tree = cached_tree;

        nodes_chance_observed.forEach(en -> assigned_nodes_ids.add(en.getId()));
        nodes_decision.forEach(en -> assigned_nodes_ids.add(en.getId()));
        nodes_utility.forEach(en -> assigned_nodes_ids.add(en.getId()));

        ebnsToPropagate.add(ebn);
        dt = new DT(usf, rounding_precision);
    }

    public boolean solveAsDT() throws HidException {
        startTime = ZonedDateTime.now(Config.TIMEZONE);

        Object json_root = JSONObject.NULL;
        try {
            json_root = cached_tree.get("root");
        } catch (JSONException e) {
            throw new HidException("Critical JSON Error", e);
        }

        if (!JSONObject.NULL.equals(json_root)) {
            try {
                D3Node root = importNodeFromJSON((JSONObject) json_root);
                dt.setRoot(root);
            } catch (JSONException e) {
                throw new HidException("Invalid format of imported data", e);
            }
        } else {
            try {
                DTNodesOrder = buildDTNodesOrder();
            } catch (ExtendedBNException e) {
                if (Logger.isDebugMode()) e.printStackTrace(Logger.err());
                throw new HidException("Invalid nodes provided", e);
            }

            if (Logger.isDebugMode()) {
                Logger.out().println("DT nodes order: " + DTNodesOrder);
            }

            try {
                removeIncomingDecArcs();
            } catch (ExtendedBNException | JepException e) {
                if (Logger.isDebugMode()) e.printStackTrace(Logger.err());
                throw new HidException("Failed to sever links to Decision nodes", e);
            }

            model.SimulationSettingWarningMessage = false;
            Model.checkMonitorsOpen = false;
            Model.suppressMessages = "system";

            scenarioOriginalList = new ArrayList(model.getScenarioList().getScenarios());
            model.getScenarioList().removeAllScenarios();

            scenario = new Scenario(new NameDescription("TempHIDtoDTScenario", "TempHIDtoDTScenario"));
            model.addScenario(scenario, true, ebn);
            scenarioIndex = model.getScenarioList().getScenarios().indexOf(scenario);

            try {
                if (!full_simulation) {
                    simNodesStaticise();
                }
                for (ExtendedNode en : this.nodes_decision) {
                    if (en instanceof ContinuousEN) {
                        if (((ContinuousEN) en).isSimulationNode()) {
                            throw new HidException("Decision nodes are not allowed to be simulated");
                        }
                    }
                }

                calculateAndUpdateMaxProgress();
                DTNodesOrderIterator = DTNodesOrder.listIterator(0);
                ExtendedNode hid_root = DTNodesOrderIterator.next();

                ZonedDateTime t1 = ZonedDateTime.now(Config.TIMEZONE);
                buildDT(hid_root);
                ZonedDateTime t2 = ZonedDateTime.now(Config.TIMEZONE);
                durationLog.append("<p>Calculation time\t").append(t1.until(t2, ChronoUnit.MILLIS)).append("\tms</p>\n");

                for (Scenario s : scenarioOriginalList) {
                    model.addScenario(s, true, ebn);
                }
                try {
                    model.removeScenario(scenario);
                } catch (ScenarioNotFoundException e) {
                    e.printStackTrace(Logger.err());
                }

                if (terminateProgressableTask) {
                    cleanup();
                    return false;
                }

                cached_tree.put("root", dt.getRoot().toJSON());
            } catch (Throwable e) {
                if (Logger.isDebugMode()) e.printStackTrace(Logger.err());
                boolean[] progress_bar_error = new boolean[]{false};
                List<String> lines = new ArrayList<>();
                Arrays.asList(e.getStackTrace()).stream().forEach(el -> {
                    lines.add(el.toString());
                    if (el.toString().toLowerCase().contains("progressbar")) {
                        progress_bar_error[0] = true;
                    }
                });

                if (!progress_bar_error[0]) {
                    terminateProgressableTask = true;
                    cleanup();

                    if (Logger.isDebugMode()) {
                        try {
                            java.nio.file.Files.write(
                                java.nio.file.Paths.get(Config.getDirectoryTempAgenaRisk() + "hid_error.log"),
                                lines,
                                java.nio.charset.Charset.forName("UTF-8"),
                                java.nio.file.StandardOpenOption.CREATE,
                                java.nio.file.StandardOpenOption.APPEND
                            );
                        } catch (java.io.IOException e2) { /* Failed to log error */ }
                    }

                    String message = e.getMessage();
                    if (!full_simulation && scenarioWithObservations != null
                            && !scenarioWithObservations.getObservations().isEmpty()) {
                        message = "Calculation failed due to observations inconsistent with "
                                + "semi-static discretisation of the model. Please try again "
                                + "with continuous simulation option set to Full Simulation.";
                    }
                    throw new HidException(message, e);
                }
            }
        }

        try {
            dt.evaluate();
            if (simplify_dt) dt.compact();
            dt.round();

            dt.getRoot().forEachChildAndSelf(new ICallback<D3Node, D3Node>() {
                @Override
                public D3Node execute(D3Node args) {
                    args.setHighlightOptimalDecisions(highlight_optimal_decisions);
                    return args;
                }
            });
        } catch (Exception e) {
            e.printStackTrace(Logger.err());
            terminateProgressableTask = true;
            cleanup();
            throw new HidException("Failed to evaluate the DT: " + e.getMessage(), e);
        }

        endTime = ZonedDateTime.now(Config.TIMEZONE);
        durationTotal = startTime.until(endTime, ChronoUnit.MILLIS);
        cleanup();
        progress = lengthOfProgressableTask;
        return true;
    }

    /**
     * Returns a {@link HidResult} capturing all data needed to generate the HTML report.
     * Call after a successful {@link #solveAsDT()}.
     */
    public HidResult getResult() {
        String scenarioName = scenarioWithObservations != null
                ? scenarioWithObservations.getName().getShortDescription()
                : null;
        return new HidResult(dt, ebn, model_file_name, scenarioName, durationTotal, durationLog.toString());
    }

    private D3Node importNodeFromJSON(JSONObject json) throws JSONException {
        D3Node node = new D3Node(new DTType(json.getInt("type")), json.getInt("id"));
        dt.nodes.add(node);
        node.setLabel(json.getString("label"));
        if (json.has("value")) node.setValue(json.getDouble("value"));
        node.setDepthOriginal(json.getInt("depth_original"));
        node.setContinuous(json.getBoolean("continuous"));
        node.setShortName(json.getString("short_name"));

        JSONArray links = json.getJSONArray("links_out");
        for (int i = 0; i < links.length(); i++) {
            JSONObject link_json = links.getJSONObject(i);
            D3Node child = importNodeFromJSON(link_json.getJSONObject("to"));
            DTLink link = new DTLink(node, child, link_json.getString("label"));
            link.setValue(link_json.getDouble("value"));
        }
        if (links.length() == 0) dt.leaves.add(node);
        return node;
    }

    public JSONObject getFullExportJSON() {
        JSONObject json = new JSONObject();
        try {
            json.put("cached_tree", cached_tree);
            JSONObject config = new JSONObject();
            config.put("ebn", ebn.getConnID());
            if (scenarioWithObservations == null) {
                config.put("scenario", JSONObject.NULL);
            } else {
                config.put("scenario", scenarioWithObservations.getName().getShortDescription());
            }
            config.put("usf", usf.getName());
            config.put("ucf", ucf.getFormula());
            config.put("rounding_precision", rounding_precision);
            config.put("simplify_dt", simplify_dt);
            config.put("highlight_optimal", highlight_optimal_decisions);
            config.put("simulation_convergence", model.getSimulationEntropyConvergenceTolerance());
            config.put("simulation_iterations", model.getSimulationNoOfIterations());
            config.put("simulation_tolerance", model.getSimulationEvidenceTolerancePercent());
            config.put("ranked_sample_size", model.getRankedSampleSize());
            config.put("full_simulation", full_simulation);
            config.put("ignore_assigned_observations", ignore_assigned_observations);

            JSONArray nco = new JSONArray();
            for (ExtendedNode en : nodes_chance_observed) nco.put(en.getConnNodeId());
            JSONArray nd = new JSONArray();
            for (ExtendedNode en : nodes_decision) nd.put(en.getConnNodeId());
            JSONArray nu = new JSONArray();
            for (ExtendedNode en : nodes_utility) nu.put(en.getConnNodeId());

            config.put("nodes_chance_observed", nco);
            config.put("nodes_decision", nd);
            config.put("nodes_utility", nu);
            json.put("config", config);
        } catch (JSONException e) {
            e.printStackTrace(Logger.err());
        }
        return json;
    }

    public ArrayList<ExtendedNode> buildDTNodesOrder() throws ExtendedBNException {
        ArrayList<ExtendedNode> observed_chance_nodes = new ArrayList<>();
        ArrayList<ExtendedNode> nodes_order = new ArrayList<>();
        for (ExtendedNode en : (List<ExtendedNode>) ebn.getExtendedNodes()) {
            if (nodes_decision.contains(en)) {
                nodes_order.add(en);
            } else if (nodes_chance_observed.contains(en)) {
                observed_chance_nodes.add(en);
            }
        }

        Collections.sort(nodes_order, new Comparator<ExtendedNode>() {
            @Override
            public int compare(ExtendedNode o1, ExtendedNode o2) {
                try {
                    return ebn.nodeGetAncestorsCount(o1) - ebn.nodeGetAncestorsCount(o2);
                } catch (ExtendedBNException ex) {
                    return 0;
                }
            }
        });

        for (ExtendedNode en : nodes_order.toArray(new ExtendedNode[0])) {
            ArrayList<ExtendedNode> en_parents = new ArrayList<>();
            for (ExtendedNode en_parent : (List<ExtendedNode>) ebn.getParentNodes(en)) {
                if (nodes_chance_observed.contains(en_parent) && !nodes_order.contains(en_parent)) {
                    en_parents.add(en_parent);
                }
            }
            Collections.sort(en_parents, Comparator.comparing(ExtendedNode::getConnNodeId));
            nodes_order.addAll(nodes_order.indexOf(en), en_parents);

            ArrayList<ExtendedNode> en_relatives = new ArrayList<>();
            for (ExtendedNode en_child : (List<ExtendedNode>) ebn.getChildNodes(en)) {
                if (nodes_decision.contains(en_child)) continue;
                for (ExtendedNode en_child_parent : (List<ExtendedNode>) ebn.getParentNodes(en_child)) {
                    if (nodes_chance_observed.contains(en_child_parent)
                            && !nodes_order.contains(en_child_parent)
                            && !ebn.getParentConnectedNodeIds(en_child_parent).contains(en.getConnNodeId())) {
                        en_relatives.add(en_child_parent);
                    }
                }
            }
            Collections.sort(en_relatives, Comparator.comparing(ExtendedNode::getConnNodeId));
            nodes_order.addAll(nodes_order.indexOf(en), en_relatives);

            for (ExtendedNode en_child : (List<ExtendedNode>) ebn.getChildNodes(en)) {
                if (nodes_chance_observed.contains(en_child)) {
                    nodes_order.remove(en_child);
                    nodes_order.add(nodes_order.indexOf(en) + 1, en_child);
                }
            }
        }

        ArrayList<ExtendedNode> remaining_nodes = new ArrayList<>();
        for (ExtendedNode en : observed_chance_nodes) {
            if (!nodes_order.contains(en)) remaining_nodes.add(en);
        }
        Collections.sort(remaining_nodes, Comparator.comparing(ExtendedNode::getConnNodeId));
        nodes_order.addAll(remaining_nodes);

        for (ExtendedNode en : (List<ExtendedNode>) ebn.getExtendedNodes()) {
            if (nodes_utility.contains(en) && ebn.getChildNodes(en).isEmpty()) {
                nodes_order.add(en);
            }
        }

        Collections.sort(nodes_order, new Comparator<ExtendedNode>() {
            @Override
            public int compare(ExtendedNode o1, ExtendedNode o2) {
                if (nodes_chance_observed.contains(o1) && nodes_chance_observed.contains(o2)) {
                    if (o1 instanceof ContinuousEN && ((ContinuousEN) o1).isSimulationNode()) return 1;
                    if (o2 instanceof ContinuousEN && ((ContinuousEN) o2).isSimulationNode()) return -1;
                }
                return 0;
            }
        });

        return nodes_order;
    }

    public static List<IUtilitySelectionFunction> getUSFList() {
        if (usf_list.isEmpty()) {
            usf_list.add(new USFMaximise());
            usf_list.add(new USFMinimise());
        }
        return usf_list;
    }

    private void buildDT(ExtendedNode en) throws HidException, MinervaIndexException, MinervaRangeException {
        if (terminateProgressableTask) {
            StreamInterceptor.output_release();
            return;
        }
        updateProgress();

        if (progress < 3) calculateAndUpdateMaxProgress();

        D3Node node = buildD3Node(en, dt.nodes.size() + 1);
        dt.nodes.add(node);
        if (dt.getRoot() == null) dt.setRoot(node);

        applyImportedObservations();
        StreamInterceptor.output_capture();
        ZonedDateTime t1 = null, t2 = null;
        try {
            t1 = ZonedDateTime.now(Config.TIMEZONE);
            model.propagateDDAlgorithm(null, ebnsToPropagate, PropagationFlag.WITH_ANCESTORS);
            t2 = ZonedDateTime.now(Config.TIMEZONE);
            calculations++;
        } catch (Exception e) {
            e.printStackTrace(Logger.err());
        }
        String out = StreamInterceptor.output_release();

        if (model.isLastPropagationSuccessful()) {
            long duration = t1.until(t2, ChronoUnit.MILLIS);
            durationLog.append("<p>Model calculation\t").append(duration).append("\tms</p>\n");
        } else {
            if (Logger.isDebugMode()) {
                try {
                    String debug_path = Config.getDirectoryTempAgenaRisk()
                            + "hid_model_calc_failed_" + (new java.util.Date().getTime()) + ".cmp";
                    model.save(debug_path);
                } catch (FileHandlingException e) {
                    e.printStackTrace(Logger.err());
                }
            }
        }

        if (Logger.isDebugMode()) {
            List log;
            if (!model.isLastPropagationSuccessful()) {
                log = Arrays.asList(out.split("\r?\n|\r"));
            } else {
                log = Arrays.asList(new String[]{"Model calculated"});
            }
            try {
                java.nio.file.Files.write(
                    java.nio.file.Paths.get(Config.getDirectoryTempAgenaRisk() + "hid_error.log"),
                    log,
                    java.nio.charset.Charset.forName("UTF-8"),
                    java.nio.file.StandardOpenOption.CREATE,
                    java.nio.file.StandardOpenOption.APPEND
                );
            } catch (java.io.IOException e) { /* Failed to log error */ }
        }

        if (!model.isLastPropagationSuccessful()) {
            if (out.contains("Expression errors")) {
                throw new HidException(out.replaceFirst("Error or advisory message: Title Expression errors Message ", ""));
            }
            if (out.contains("Too many Functions were specified")) {
                throw new HidException(out);
            }
            return;
        }

        if (nodes_chance_observed.contains(en)) {
            if (en instanceof ContinuousEN && ((ContinuousEN) en).isSimulationNode()) {
                forcedStaticNodes.add(en);
                node.setContinuous(true);
            }
            if (node_original_states.containsKey(en)) node.setContinuous(true);
        }

        if (dta != null) {
            dta.setTo(node);
            node.linksIn.add(dta);
        }

        if (nodes_utility.contains(en)) {
            double value = 0;
            try {
                value = ucf.calculateUtility(model.getMarginalDataStore()
                        .getMarginalDataItemListForNode(ebn, en)
                        .getMarginalDataItemAtIndex(scenarioIndex));
            } catch (Exception e) {
                String message = "Invalid utility calculation formula";
                if (e.getMessage() != null) message += ": " + e.getMessage();
                throw new HidException(message, e);
            }
            node.setValue(value);
            dt.leaves.add(node);
            return;
        }

        ArrayList<HIDStateInstance> state_instances = new ArrayList<>();
        rememberedStateInstances.put(en, state_instances);

        DataSet ds = model.getMarginalDataStore()
                .getMarginalDataItemListForNode(ebn, en)
                .getMarginalDataItemAtIndex(scenarioIndex).getDataset();

        if (forcedStaticNodes.contains(en)) {
            for (int i_marg = 0; i_marg < ds.size(); i_marg++) {
                DataPoint dp = ds.getDataPointAtOrderPosition(i_marg);
                String[] parts = dp.getLabel().split(" - ");
                double bound_lower = Double.parseDouble(parts[0]);
                double bound_upper = Double.parseDouble(parts[1]);
                state_instances.add(new HIDStateInstanceContinuous(dp.getLabel(), dp.getValue(),
                        new Range(bound_lower, bound_upper)));
            }
        } else {
            for (int i_state = 0; i_state < en.getExtendedStates().size(); i_state++) {
                ExtendedState es = ((List<ExtendedState>) en.getExtendedStates()).get(i_state);
                state_instances.add(new HIDStateInstanceDiscrete(
                        es.getName().getShortDescription(),
                        ds.getDataPointAtOrderPosition(i_state).getValue(),
                        es));
            }
        }

        for (HIDStateInstance hidsi : state_instances) {
            if (hidsi.value == 0) continue;

            DTLink link = new DTLink(node, null);
            link.setLabel(hidsi.label);
            if (!nodes_decision.contains(en)) link.setValue(hidsi.value);
            dta = link;

            if (forcedStaticNodes.contains(en)) {
                Range range = ((HIDStateInstanceContinuous) hidsi).range;
                if (en instanceof IntegerIntervalEN) {
                    scenario.addIntegerObservation(ebn.getId(), en.getId(), (int) (range.midPoint() + 0.5d));
                } else {
                    scenario.addRealObservation(ebn.getId(), en.getId(), range.midPoint());
                }
            } else {
                if (en instanceof ContinuousIntervalEN) {
                    scenario.addRealObservation(ebn.getId(), en.getId(),
                            ((HIDStateInstanceDiscrete) hidsi).state_logical.getNumericalValue());
                } else if (en instanceof IntegerIntervalEN) {
                    scenario.addIntegerObservation(ebn.getId(), en.getId(),
                            (int) ((HIDStateInstanceDiscrete) hidsi).state_logical.getNumericalValue());
                } else {
                    scenario.addHardEvidenceObservation(ebn.getId(), en.getId(),
                            ((HIDStateInstanceDiscrete) hidsi).state_logical.getId());
                }
            }

            buildDT(DTNodesOrderIterator.next());
            if (dta.getTo() == null) node.linksOut.remove(dta);
            DTNodesOrderIterator.previous();
            scenario.removeObservationsForNode(ebn.getId(), en.getId(), new int[0], false);
        }

        if (forcedStaticNodes.contains(en)) forcedStaticNodes.remove(en);
    }

    public int getDTType(ExtendedNode en) throws HidException {
        if (nodes_decision.contains(en)) return DTType.TYPE_DECISION;
        if (nodes_chance_observed.contains(en)) return DTType.TYPE_CHANCE;
        if (nodes_utility.contains(en)) return DTType.TYPE_UTILITY;
        throw new HidException("Node type unspecified for "
                + en.getName().getShortDescription() + "[" + en.getConnNodeId() + "]");
    }

    public D3Node buildD3Node(ExtendedNode en, int new_node_id) throws HidException {
        D3Node node = new D3Node(new DTType(getDTType(en)), new_node_id);
        node.setDepthOriginal(DTNodesOrderIterator.nextIndex() - 1);
        node.setLabel(en.getName().getShortDescription());
        node.setShortName(en.getConnNodeId());
        node.setNodeLogical(en);
        return node;
    }

    @Override
    public int getCurrentProgress() {
        return Math.max(1, (int) (100.0 * progress / lengthOfProgressableTask));
    }

    @Override
    public int getLengthOfProgressableTask() {
        return lengthOfProgressableTask;
    }

    @Override
    public void terminateProgressableTask() {
        terminateProgressableTask = true;
        progress = lengthOfProgressableTask;
    }

    @Override
    public boolean isProgressableTaskDone() {
        return progressableTaskDone;
    }

    @Override
    public void resetProgressableTask() {
        while (terminateProgressableTask && !progressableTaskDone) {
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                break;
            }
        }
        progress = 0;
        progressableTaskDone = false;
        terminateProgressableTask = false;
    }

    private void updateProgress() { progress++; }

    private void calculateAndUpdateMaxProgress() {
        lengthOfProgressableTask = 1;
        for (int i = 0; i < DTNodesOrder.size() - 1; i++) {
            ExtendedNode en = DTNodesOrder.get(i);
            if (en instanceof ContinuousEN && ((ContinuousEN) en).isSimulationNode()) {
                int states_per_sim_node = 16;
                if (rememberedStateInstances.containsKey(en)) {
                    states_per_sim_node = rememberedStateInstances.get(en).size();
                } else if (progress > 1) {
                    states_per_sim_node = model.getMarginalDataStore()
                            .getMarginalDataItemListForNode(ebn, en)
                            .getMarginalDataItemAtIndex(scenarioIndex).getDataset().size();
                }
                lengthOfProgressableTask *= states_per_sim_node;
            } else {
                lengthOfProgressableTask *= en.getExtendedStates().size();
            }
        }
        if (Logger.isDebugMode()) {
            Logger.out().println("Worst case iterations:\t" + lengthOfProgressableTask);
        }
        if (!full_simulation) {
            for (int i = 0; i < model.getExtendedBNList().getExtendedBNs().size(); i++) {
                ExtendedBN bn = (ExtendedBN) model.getExtendedBNList().getExtendedBNs().get(i);
                lengthOfProgressableTask += bn.getExtendedNodes().size() * 2;
            }
        }
    }

    private void simNodesStaticise() throws HidException {
        StreamInterceptor.output_capture();
        try {
            if (!model.isLastPropagationSuccessful()) {
                model.propagateDDAlgorithm(null, ebnsToPropagate,
                        PropagationFlag.WITH_ANCESTORS, PropagationFlag.KEEP_TAILS_ZERO_REGIONS);
            }
        } catch (Exception e) {
            e.printStackTrace(Logger.err());
        }
        String out = StreamInterceptor.output_release();
        if (!model.isLastPropagationSuccessful()) {
            out = out.replaceAll("Error or advisory message: Title .* Message ", "");
            throw new HidException("Failed to use semi-static simulation: " + out);
        }

        for (int i = 0; i < ebn.getExtendedNodes().size(); i++) {
            ExtendedNode en = ((List<ExtendedNode>) ebn.getExtendedNodes()).get(i);
            if (en instanceof ContinuousEN && ((ContinuousEN) en).isSimulationNode()) {
                ContinuousEN cen = (ContinuousEN) en;
                DataSet ds = model.getMarginalDataStore()
                        .getMarginalDataItemListForNode(ebn, en)
                        .getMarginalDataItemAtIndex(scenarioIndex).getDataset();
                node_original_states.put(en, new ArrayList<>(en.getExtendedStates()));
                try {
                    ContinuousEN.ConvertToNonSimulation(cen, ds, ebn, scenario);
                } catch (Exception e) {
                    throw new HidException("Failed to use semi-static simulation: " + e.getMessage(), e);
                }
            }
        }

        try {
            regenerateNPTs();
        } catch (Exception e) {
            throw new HidException("Failed to use semi-static simulation: " + e.getMessage(), e);
        }
    }

    private void simNodesReset() throws HidException {
        CoreBN cbn = ebn.getConnBN();
        try {
            for (ExtendedNode en : node_original_states.keySet()) {
                ((ContinuousEN) en).setSimulationNode(true);
                en.setExtendedStates(node_original_states.get(en));
                cbn.initialiseStates(cbn.getNodeWithAltId(en.getConnNodeId()),
                        en.getExtendedStateShortNames());
                en.setInputNodeReceivedMarginals(false);
            }
            regenerateNPTs();
        } catch (Exception e) {
            throw new HidException("Failed to recover after using semi-static simulation: " + e.getMessage(), e);
        }
    }

    private void regenerateNPTs() throws HidException, ExtendedBNException, MinervaRangeException,
            InconsistentEvidenceException, NPTGeneratorInsufficientStateRangeException, NPTGeneratorException {
        durationLog.append("<p>Regenerating");
        ZonedDateTime t1 = ZonedDateTime.now(Config.TIMEZONE);
        for (int i = 0; i < model.getExtendedBNList().getExtendedBNs().size(); i++) {
            if (terminateProgressableTask) return;
            ExtendedBN bn = (ExtendedBN) model.getExtendedBNList().getExtendedBNs().get(i);
            List extendedNodes = bn.getExtendedNodes();
            for (int j = 0; j < extendedNodes.size(); j++) {
                ExtendedNode node = (ExtendedNode) extendedNodes.get(j);
                boolean ddNode = false;
                if (node instanceof ContinuousEN && ((ContinuousEN) node).isDynamicallyDiscretisable()) {
                    ddNode = true;
                }
                if (!node.isConnectableInputNode() && !ddNode) {
                    bn.regenerateNPT(node, false, false, false);
                }
                updateProgress();
            }
        }
        ZonedDateTime t2 = ZonedDateTime.now(Config.TIMEZONE);
        durationLog.append("\t").append(t1.until(t2, ChronoUnit.MILLIS)).append("\tms</p>\n");
    }

    private void applyImportedObservations() {
        if (scenarioWithObservations != null) {
            for (Observation obs : (List<Observation>) scenarioWithObservations.getObservations()) {
                if (ignore_assigned_observations && assigned_nodes_ids.contains(obs.getConnExtendedNodeId())) {
                    // skip observation at assigned node
                } else {
                    scenario.addObservation(obs, false);
                }
            }
        }
    }

    private void removeIncomingDecArcs() throws ExtendedBNException, JepException {
        for (ExtendedNode node : nodes_decision) {
            final List<ExtendedNode> parents = ebn.getParentNodes(node);
            for (ExtendedNode parent : parents) {
                boolean remove = false;
                if (nodes_decision.contains(parent)) continue;
                if (node.getCurrentPartitionedModelNodeFunctions().size() > 1) continue;
                if (node.getCurrentNodeFunction() != null
                        && node.getCurrentNodeFunction().toString().contains("uniform_distribution")) {
                    remove = true;
                }
                if (!remove && node.getFunctionMode() == ExtendedNode.EDITABLE_NPT) {
                    boolean uniform = true;
                    Float val = node.getNPT()[0][0];
                    npt_loop:
                    for (int i = 0; i < node.getNPT().length; i++) {
                        for (int j = 0; j < node.getNPT()[0].length; j++) {
                            if (!val.equals(node.getNPT()[i][j])) {
                                uniform = false;
                                break npt_loop;
                            }
                        }
                    }
                    if (uniform) remove = true;
                }
                if (remove) {
                    backup_functions.put(node, node.getCurrentNodeFunction());
                    backup_parents.put(node, parents);
                    backup_function_modes.put(node, node.getFunctionMode());
                    ebn.removeRelationship(node, parent);
                    node.setCurrentNodeFunction(null);
                    node.setFunctionMode(ExtendedNode.EDITABLE_NPT);
                    node.setNptReCalcRequired(true);
                }
            }
        }
    }

    private void restoreIncomingDecArcs() throws ExtendedBNException {
        for (ExtendedNode node : backup_functions.keySet()) {
            node.setCurrentNodeFunction(backup_functions.get(node));
            node.setFunctionMode(backup_function_modes.get(node));
            final List<ExtendedNode> parents = ebn.getParentNodes(node);
            for (ExtendedNode parent : backup_parents.get(node)) {
                if (!parents.contains(parent)) node.addParent(parent);
            }
            node.setNptReCalcRequired(true);
        }
    }

    private void cleanup() throws HidException {
        if (!full_simulation) simNodesReset();
        progressableTaskDone = true;
        try {
            restoreIncomingDecArcs();
        } catch (ExtendedBNException e) {
            if (Logger.isDebugMode()) e.printStackTrace(Logger.err());
            throw new HidException("Failed to restore severed Decision node arcs", e);
        }
    }
}
