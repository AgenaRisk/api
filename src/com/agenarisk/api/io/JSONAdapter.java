package com.agenarisk.api.io;

import com.agenarisk.api.exception.AdapterException;
import com.agenarisk.api.exception.AgenaRiskRuntimeException;
import com.agenarisk.api.io.stub.Graphics;
import com.agenarisk.api.io.stub.Meta;
import com.agenarisk.api.io.stub.NodeConfiguration;
import com.agenarisk.api.io.stub.SummaryStatistic;
import com.agenarisk.api.model.Settings;
import com.agenarisk.api.model.CalculationResult;
import com.agenarisk.api.model.CrossNetworkLink;
import com.agenarisk.api.model.Link;
import com.agenarisk.api.model.Network;
import com.agenarisk.api.model.Node;
import com.agenarisk.api.model.State;
import com.agenarisk.api.model.dataset.ResultValue;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import org.apache.sling.commons.json.JSONArray;
import org.apache.sling.commons.json.JSONException;
import org.apache.sling.commons.json.JSONObject;
import uk.co.agena.minerva.model.ConstantStateMessagePassingLink;
import uk.co.agena.minerva.model.ConstantSummaryMessagePassingLink;
import uk.co.agena.minerva.model.MarginalDataItem;
import uk.co.agena.minerva.model.MessagePassingLink;
import uk.co.agena.minerva.model.MessagePassingLinks;
import uk.co.agena.minerva.model.Model;
import uk.co.agena.minerva.model.extendedbn.ContinuousEN;
import uk.co.agena.minerva.model.extendedbn.DiscreteRealEN;
import uk.co.agena.minerva.model.extendedbn.ExtendedBN;
import uk.co.agena.minerva.model.extendedbn.ExtendedBNException;
import uk.co.agena.minerva.model.extendedbn.ExtendedBNNotFoundException;
import uk.co.agena.minerva.model.extendedbn.ExtendedNode;
import uk.co.agena.minerva.model.extendedbn.ExtendedNodeFunction;
import uk.co.agena.minerva.model.extendedbn.ExtendedNodeNotFoundException;
import uk.co.agena.minerva.model.extendedbn.ExtendedState;
import uk.co.agena.minerva.model.extendedbn.ExtendedStateNotFoundException;
import uk.co.agena.minerva.model.extendedbn.LabelledEN;
import uk.co.agena.minerva.model.extendedbn.NumericalEN;
import uk.co.agena.minerva.model.extendedbn.RankedEN;
import uk.co.agena.minerva.model.scenario.Observation;
import uk.co.agena.minerva.model.scenario.Scenario;
import uk.co.agena.minerva.util.Environment;
import uk.co.agena.minerva.util.helpers.MathsHelper;
import uk.co.agena.minerva.util.model.DataPoint;
import uk.co.agena.minerva.util.model.DataSet;
import uk.co.agena.minerva.util.model.IntervalDataPoint;
import uk.co.agena.minerva.util.model.ModificationLog;
import uk.co.agena.minerva.util.model.Note;
import uk.co.agena.minerva.util.model.Variable;

/**
 * JSONAdapter enables encoding of a model as JSON.
 * 
 * @author Eugene Dementiev
 */
public class JSONAdapter {
	
	public static final boolean CACHE_NPTS = true;
	
	public static JSONObject toJSONObject(Model model) throws JSONException {
		JSONObject json = new JSONObject();
		
		// Model
		JSONObject jsonModel = new JSONObject();
		json.put(com.agenarisk.api.model.Model.Field.model.toString(), jsonModel);
		
		// Networks
		jsonModel.put(Network.Field.networks.toString(), modelNetworksToJSON(model));
		
		// Cross network links
		jsonModel.put(Link.Field.links.toString(), modelLinksToJSON(model));
		
		// Settings
		jsonModel.put(Settings.Field.settings.toString(), modelSettingsToJSON(model));

		// Scenarios
		jsonModel.put(com.agenarisk.api.model.DataSet.Field.dataSets.toString(), modelScenariosToJSON(model));
		
		// Meta
		if (!model.getNotes().getNotes().isEmpty()){
			jsonModel.put(Meta.Field.meta.toString(), modelMetaToJSON(model));
		}
		
		// Texts
		
		// Pictures
		
		// Graphics
		
		return json;
	}
	
	protected static JSONObject modelSettingsToJSON(Model model) throws JSONException {
		JSONObject jsonSettings = new JSONObject();
		jsonSettings.put(Settings.Field.iterations.toString(), model.getSimulationNoOfIterations());
		jsonSettings.put(Settings.Field.convergence.toString(), model.getSimulationEntropyConvergenceTolerance());
		jsonSettings.put(Settings.Field.tolerance.toString(), model.getSimulationEvidenceTolerancePercent());
		jsonSettings.put(Settings.Field.sampleSize.toString(), model.getSampleSize());
		jsonSettings.put(Settings.Field.sampleSizeRanked.toString(), model.getRankedSampleSize());
		jsonSettings.put(Settings.Field.discreteTails.toString(), model.isSimulationTails());
		jsonSettings.put(Settings.Field.simulationLogging.toString(), model.isSimulationLogging());
		jsonSettings.put(Settings.Field.parameterLearningLogging.toString(), model.isEMLogging());
		return jsonSettings;
	}
	
	protected static JSONArray modelScenariosToJSON(Model model) throws JSONException {
		JSONArray jsonDataSets = new JSONArray();
		
		int scenarioCount = model.getScenarioList().getScenarios().size();
		for (int i = 0; i < scenarioCount; i++) {
			JSONObject jsonDataSet = scenarioToJSON(model, model.getScenarioAtIndex(i));
			jsonDataSets.put(jsonDataSet);
		}
		
		return jsonDataSets;
	}
	
	protected static JSONObject scenarioToJSON(Model model, Scenario scenario) throws JSONException{
		JSONObject jsonDataSet = new JSONObject();
		
		// ID
		jsonDataSet.put(com.agenarisk.api.model.DataSet.Field.id.toString(), scenario.getName().getShortDescription());
		
		// Observations
		JSONArray jsonObservations = new JSONArray();
		for(Observation observation: (List<Observation>)scenario.getObservations()){
			try {
				jsonObservations.put(observationToJSON(model, observation));
			}
			catch (ExtendedBNNotFoundException | ExtendedNodeNotFoundException | ExtendedStateNotFoundException ex){
				// Ignore, should not happen
				Environment.printThrowableIfDebug(ex);
			}
		}
		jsonDataSet.put(com.agenarisk.api.model.Observation.Field.observations.toString(), jsonObservations);
		
		
		// Results
		JSONArray jsonResults = resultsToJSON(model, scenario);
		jsonDataSet.put(com.agenarisk.api.model.CalculationResult.Field.results.toString(), jsonResults);
		
		// Graphics
		
		return jsonDataSet;
	}
	
	protected static JSONArray resultsToJSON(Model model, Scenario scenario) throws JSONException{
		JSONArray jsonResults = new JSONArray();
		
		int scenarioIndex = -1;
		for (int i = 0; i < model.getScenarioList().getScenarios().size(); i++) {
			if (model.getScenarioAtIndex(i).equals(scenario)){
				scenarioIndex = i;
				break;
			}
		}
		
		if (scenarioIndex < 0){
			throw new AgenaRiskRuntimeException("Scenario `" + scenario.getName().getShortDescription() + "` not found in model");
		}
		
		for(ExtendedBN ebn: (List<ExtendedBN>) model.getExtendedBNList().getExtendedBNs()){
			
			for(ExtendedNode en: (List<ExtendedNode>) ebn.getExtendedNodes()){
				JSONObject jsonResult = new JSONObject();
				
				if (model.getMarginalDataStore().getMarginalDataItemListForNode(ebn, en).getMarginalDataItems().size() <= scenarioIndex){
					// No result data for node, skip it
					continue;
				}
				
				jsonResult.put(CalculationResult.Field.network.toString(), ebn.getConnID());
				jsonResult.put(CalculationResult.Field.node.toString(), en.getConnNodeId());
				
				MarginalDataItem mdi = model.getMarginalDataStore().getMarginalDataItemListForNode(ebn, en).getMarginalDataItemAtIndex(scenarioIndex);
				DataSet ds = mdi.getDataset();
				
				if (en instanceof NumericalEN){
					JSONObject jsonSS = new JSONObject();
					jsonSS.put(SummaryStatistic.Field.confidenceInterval.toString(), mdi.getConfidenceInterval());
					jsonSS.put(SummaryStatistic.Field.mean.toString(), mdi.getMeanValue());
					jsonSS.put(SummaryStatistic.Field.median.toString(), mdi.getMedianValue());
					jsonSS.put(SummaryStatistic.Field.standardDeviation.toString(), mdi.getStandardDeviationValue());
					jsonSS.put(SummaryStatistic.Field.variance.toString(), mdi.getVarianceValue());
					jsonSS.put(SummaryStatistic.Field.entropy.toString(), mdi.getEntropyValue());
					jsonSS.put(SummaryStatistic.Field.percentile.toString(), mdi.getPercentileValue());
					jsonSS.put(SummaryStatistic.Field.lowerPercentile.toString(), mdi.getLowerPercentile());
					jsonSS.put(SummaryStatistic.Field.upperPercentile.toString(), mdi.getUpperPercentile());
					jsonResult.put(SummaryStatistic.Field.summaryStatistics.toString(), jsonSS);
				}
				
				JSONArray jsonResultValues = new JSONArray();
				for(DataPoint dp: (List<DataPoint>) ds.getDataPoints()){
					
					JSONObject jsonResultValue = new JSONObject();
					
					String label = dp.getLabel();
					if (dp instanceof IntervalDataPoint){
						IntervalDataPoint idp = (IntervalDataPoint) dp;
						label = idp.getIntervalLowerBound() + " - " + idp.getIntervalUpperBound();
					}
					jsonResultValue.put(ResultValue.Field.label.toString(), label);
					jsonResultValue.put(ResultValue.Field.value.toString(), dp.getValue());
					
					jsonResultValues.put(jsonResultValue);
				}
				
				jsonResult.put(ResultValue.Field.resultValues.toString(), jsonResultValues);
				
				jsonResults.put(jsonResult);
			}
		}
		
		return jsonResults;
	}
	
	private static JSONObject observationToJSON(Model model, Observation observation) throws ExtendedBNNotFoundException, ExtendedNodeNotFoundException, JSONException, ExtendedStateNotFoundException{
		JSONObject jsonObservation = new JSONObject();
		
		ExtendedBN ebn = model.getExtendedBN(observation.getConnExtendedBNId());
		jsonObservation.put(com.agenarisk.api.model.Observation.Field.network.toString(), ebn.getConnID());
		
		ExtendedNode en = ebn.getExtendedNode(observation.getConnExtendedNodeId());
		jsonObservation.put(com.agenarisk.api.model.Observation.Field.node.toString(), en.getConnNodeId());
		
		JSONArray jsonEntries = new JSONArray();
		
		if (en instanceof RankedEN || en instanceof LabelledEN || en instanceof DiscreteRealEN){
			// Hard / Soft observation with arc dataset
			DataSet ds = observation.getDataSet();
			for(DataPoint dp: (List<DataPoint>) ds.getDataPoints()){
				JSONObject jsonEntry = new JSONObject();
				ExtendedState es = en.getExtendedState(dp.getConnObjectId());
				jsonEntry.put(com.agenarisk.api.model.Observation.Field.value.toString(), State.computeLabel(en, es).trim());
				jsonEntry.put(com.agenarisk.api.model.Observation.Field.weight.toString(), dp.getValue());
				jsonEntries.put(jsonEntry);
			}
		}
		else {
			// Hard observation with specific value
			JSONObject jsonEntry = new JSONObject();
			String observationAnswer = observation.getUserEnteredAnswer();
			if (en instanceof DiscreteRealEN || en instanceof ContinuousEN){
				observationAnswer = Double.valueOf(observationAnswer)+"";
			}
			jsonEntry.put(com.agenarisk.api.model.Observation.Field.value.toString(), observationAnswer);
			jsonEntry.put(com.agenarisk.api.model.Observation.Field.weight.toString(), 1);
			jsonEntries.put(jsonEntry);
		}
		
		jsonObservation.put(com.agenarisk.api.model.Observation.Field.entries.toString(), jsonEntries);
		return jsonObservation;
	}
	
	protected static JSONArray modelLinksToJSON(Model model) throws JSONException {
		JSONArray jsonLinks = new JSONArray();
		
		for (MessagePassingLinks mpls: (List<MessagePassingLinks>) model.getMessagePassingLinks()){
			for(MessagePassingLink mpl: mpls.getLinks()){
				try {
					JSONObject jsonLink = cnLinkToJSON(model, mpl);
					jsonLinks.put(jsonLink);
				}
				catch (ExtendedBNNotFoundException | ExtendedNodeNotFoundException | ExtendedStateNotFoundException | JSONException ex){
					// Ignore this error and keep going
					// Should not happen for valid models
					Environment.printThrowableIfDebug(ex);
				}
			}
		}
		
		return jsonLinks;
	}
	
	private static JSONObject cnLinkToJSON(Model model, MessagePassingLink mpl) throws ExtendedBNNotFoundException, ExtendedNodeNotFoundException, ExtendedStateNotFoundException, JSONException{
		JSONObject jsonLink = new JSONObject();
		
		ExtendedBN ebn1 = model.getExtendedBN(mpl.getParentExtendedBNId());
		ExtendedNode en1 = ebn1.getExtendedNode(mpl.getParentExtendedNodeId());

		ExtendedBN ebn2 = model.getExtendedBN(mpl.getChildExtendedBNId());
		ExtendedNode en2 = ebn2.getExtendedNode(mpl.getChildExtendedNodeId());

		jsonLink.put(CrossNetworkLink.Field.sourceNetwork.toString(), ebn1.getConnID());
		jsonLink.put(CrossNetworkLink.Field.sourceNode.toString(), en1.getConnNodeId());
		jsonLink.put(CrossNetworkLink.Field.targetNetwork.toString(), ebn2.getConnID());
		jsonLink.put(CrossNetworkLink.Field.targetNode.toString(), en2.getConnNodeId());
		
		CrossNetworkLink.Type linkType;
		if (mpl instanceof ConstantSummaryMessagePassingLink){
			ConstantSummaryMessagePassingLink csmpl = (ConstantSummaryMessagePassingLink)mpl;
			switch(csmpl.getSummaryStatistic()){
				case MEAN:
					linkType = CrossNetworkLink.Type.Mean;
					break;
				case MEDIAN:
					linkType = CrossNetworkLink.Type.Median;
					break;
				case VARIANCE:
					linkType = CrossNetworkLink.Type.Variance;
					break;
				case STANDARD_DEVIATION:
					linkType = CrossNetworkLink.Type.StandardDeviation;
					break;
				case LOWER_PERCENTILE:
					linkType = CrossNetworkLink.Type.LowerPercentile;
					break;
				case UPPER_PERCENTILE:
					linkType = CrossNetworkLink.Type.UpperPercentile;
					break;
				default:
					throw new AgenaRiskRuntimeException("Invalid link summary statistic: " + csmpl.getSummaryStatistic().name());
			}
		}
		else if (mpl instanceof ConstantStateMessagePassingLink){
			linkType = CrossNetworkLink.Type.State;
			
			ConstantStateMessagePassingLink csmpl = (ConstantStateMessagePassingLink)mpl;
			ExtendedState es = en1.getExtendedState(csmpl.getParentNodeStateId());
			jsonLink.put(CrossNetworkLink.Field.passState.toString(), State.computeLabel(en1, es));
		}
		else {
			linkType = CrossNetworkLink.Type.Marginals;
		}
		
		jsonLink.put(CrossNetworkLink.Field.type.toString(), linkType.toString());
		
		return jsonLink;
	}
	
	protected static JSONObject modelMetaToJSON(Model model) throws JSONException {
		JSONObject jsonMeta = new JSONObject();
		
		JSONArray jsonNotes = new JSONArray();
		for(Note note: (List<Note>)model.getNotes().getNotes()){
			JSONObject jsonNote = new JSONObject();
			jsonNote.put(Meta.Field.name.toString(), note.getNd().getShortDescription());
			jsonNote.put(Meta.Field.text.toString(), note.getNd().getLongDescription());
			jsonNotes.put(jsonNote);
		}
		jsonMeta.put(Meta.Field.notes.toString(), jsonNotes);
		
		return jsonMeta;
	}
	
	protected static JSONArray modelNetworksToJSON(Model model) throws JSONException {
		JSONArray jsonNetworks = new JSONArray();
		for(ExtendedBN ebn: (List<ExtendedBN>) model.getExtendedBNList().getExtendedBNs()){
			try {
				jsonNetworks.put(toJSONObject(ebn));
			}
			catch(ExtendedBNException ex){
				throw new AgenaRiskRuntimeException("Failed to encode a Model to JSON", ex);
			}
		}
		return jsonNetworks;
	}
	
	protected static JSONObject toJSONObject(ExtendedBN ebn) throws JSONException, ExtendedBNException {
		JSONObject jsonNetwork = new JSONObject();
		
		jsonNetwork.put(Network.Field.id.toString(), ebn.getConnID());
		jsonNetwork.put(Network.Field.name.toString(), ebn.getName().getShortDescription());
		
		if (ebn.getName().getLongDescription().trim().length() > 0){
			jsonNetwork.put(Network.Field.description.toString(), ebn.getName().getLongDescription());
		}
		
		// Nodes
		JSONArray jsonNodes = new JSONArray();
		for(ExtendedNode en: (List<ExtendedNode>) ebn.getExtendedNodes()){
			jsonNodes.put(toJSONObject(en));
		}
		jsonNetwork.put(Node.Field.nodes.toString(), jsonNodes);
		
		// Links
		JSONArray jsonLinks = new JSONArray();
		for(ExtendedNode en: (List<ExtendedNode>) ebn.getExtendedNodes()){
			for (ExtendedNode enParent: (List<ExtendedNode>)ebn.getParentNodes(en)){
				JSONObject jsonLink = new JSONObject();
				jsonLink.put(Link.Field.parent.toString(), enParent.getConnNodeId());
				jsonLink.put(Link.Field.child.toString(), en.getConnNodeId());
				jsonLinks.put(jsonLink);
			}
			
		}
		jsonNetwork.put(Link.Field.links.toString(), jsonLinks);
		
		// Graphics
		
		// Risk Table
		
		// Texts
		
		// Pictures
		
		// Modification log
		if (ebn.getModificationLog() != null && ebn.getModificationLog().getModificationItems() != null){
			JSONArray modificationLog = new JSONArray();
			for(ModificationLog.ModificationLogItem mli: (List<ModificationLog.ModificationLogItem>) ebn.getModificationLog().getModificationItems()){
				JSONObject entry = new JSONObject();
				entry.put(Network.ModificationLog.action.toString(), mli.getDescription().getShortDescription());
				entry.put(Network.ModificationLog.description.toString(), mli.getDescription().getLongDescription());
				modificationLog.put(entry);
			}
			jsonNetwork.put(Network.ModificationLog.modificationLog.toString(), modificationLog);
		}
		
		
		return jsonNetwork;
	}
	
	protected static JSONObject toJSONObject(ExtendedNode en) throws JSONException {
		JSONObject json = new JSONObject();
		
		// Fields
		json.put(Node.Field.id.toString(), en.getConnNodeId());
		json.put(Node.Field.name.toString(), en.getName().getShortDescription());
		
		if (en.getName().getLongDescription().trim().length() > 0){
			json.put(Node.Field.description.toString(), en.getName().getLongDescription());
		}
		
		// Configuration
		JSONObject jsonConfiguration = nodeConfigToJSON(en);
		json.put(NodeConfiguration.Field.configuration.toString(), jsonConfiguration);
		
		// Meta
		if (!en.getNotes().getNotes().isEmpty()){
			json.put(Meta.Field.meta.toString(), nodeMetaToJSON(en));
		}
		
		// Graphics
		JSONObject jsonGraphics = new JSONObject();
		json.put(Graphics.Field.graphics.toString(), jsonGraphics);
		
		return json;
	}
	
	protected static JSONObject nodeConfigToJSON(ExtendedNode en) throws JSONException {
		JSONObject jsonConfig = new JSONObject();
		
		// Type
		Node.Type nodeType = Node.resolveNodeType(en);
		jsonConfig.put(NodeConfiguration.Field.type.toString(), nodeType);
		
		// Simulated
		boolean simulated = false;
		if (en instanceof ContinuousEN){
			ContinuousEN cen = (ContinuousEN) en;
			simulated = cen.isSimulationNode();
			jsonConfig.putOpt(NodeConfiguration.Field.simulated.toString(), simulated?true:null);
		}
		
		// Table
		JSONObject jsonTable = nodeTableToJSON(en);
		jsonConfig.put(NodeConfiguration.Table.table.toString(), jsonTable);
		
		// States
		if (!simulated){
			JSONArray jsonStates = new JSONArray();
			for(ExtendedState es: (List<ExtendedState>)en.getExtendedStates()){
				jsonStates.put(State.computeLabel(en, es).trim());
			}
			jsonConfig.put(NodeConfiguration.States.states.toString(), jsonStates);
		}
		
		// Variables
		if (!en.getExpressionVariables().getVariables().isEmpty()){
			JSONArray jsonVariables = new JSONArray();
			for(Variable variable: (List<Variable>)en.getExpressionVariables().getVariables()){
				if (!variable.isEditable()){
					continue;
				}
				JSONObject jsonVariable = new JSONObject();
				jsonVariable.put(NodeConfiguration.Variables.name.toString(), variable.getName());
				jsonVariable.put(NodeConfiguration.Variables.value.toString(), variable.getDefaultValue());
				jsonVariables.put(jsonVariable);
			}
			jsonConfig.put(NodeConfiguration.Variables.variables.toString(), jsonVariables);
		}
		
		return jsonConfig;
	}
	
	protected static JSONObject nodeTableToJSON(ExtendedNode en) throws JSONException {
		JSONObject jsonTable = new JSONObject();
		
		// Type
		NodeConfiguration.TableType tableType;
		switch(en.getFunctionMode()){
			case ExtendedNode.EDITABLE_NPT:
				tableType = NodeConfiguration.TableType.Manual;
				break;
				
			case ExtendedNode.EDITABLE_NODE_FUNCTION:
				tableType = NodeConfiguration.TableType.Expression;
				break;
			
			case ExtendedNode.EDITABLE_PARENT_STATE_FUNCTIONS:
				tableType = NodeConfiguration.TableType.Partitioned;
				break;
				
			default:
				throw new AgenaRiskRuntimeException("Invalid node table type `" + en.getFunctionMode() + "` for node `" + en.getConnNodeId() + "`");
		}
		jsonTable.put(NodeConfiguration.Table.type.toString(), tableType);
		
		// Partitions
		if (tableType.equals(NodeConfiguration.TableType.Partitioned)){
			JSONArray jsonPartitions = new JSONArray();
			for(ExtendedNode partitionParent: (List<ExtendedNode>)en.getPartitionedExpressionModelNodes()){
				jsonPartitions.put(partitionParent.getConnNodeId());
			}
			jsonTable.put(NodeConfiguration.Table.partitions.toString(), jsonPartitions);
		}
		
		// Expressions
		JSONArray jsonExpressions = new JSONArray();
		List<ExtendedNodeFunction> enfs = new ArrayList<>();
		
		if (tableType.equals(NodeConfiguration.TableType.Expression)){
			enfs.add(en.getCurrentNodeFunction());
		}
		else if (tableType.equals(NodeConfiguration.TableType.Partitioned)){
			enfs.addAll(en.getCurrentPartitionedModelNodeFunctions());
		}
		
		for(ExtendedNodeFunction enf: enfs){
			String expressionString = enf.getName().replaceAll(" ", "");
			expressionString += "(" + enf.getParameters().stream().collect(Collectors.joining(",")) + ")";
			jsonExpressions.put(expressionString);
		}
		
		if (!enfs.isEmpty()){
			jsonTable.put(NodeConfiguration.Table.expressions.toString(), jsonExpressions);
		}
		
		// Probabilities
		boolean simulated = en instanceof ContinuousEN && ((ContinuousEN)en).isSimulationNode();
		boolean manual = tableType.equals(NodeConfiguration.TableType.Manual);
		if (!simulated && (manual || CACHE_NPTS)){
			float[][] npt;
			try {
				npt = en.getNPT();
			}
			catch (ExtendedBNException ex){
				throw new AgenaRiskRuntimeException("Failed to retrieve NPT for node `" + en.getConnNodeId() + "`", ex);
			}
			
			JSONArray jsonRows = new JSONArray();
			
			for(float[] row: npt){
				JSONArray jsonRow = new JSONArray();
				for(double p: row){
					//jsonRow.put(MathsHelper.roundDouble(p, 7));
					jsonRow.put(p);
				}
				jsonRows.put(jsonRow);
			}
			jsonTable.put(NodeConfiguration.Table.probabilities.toString(), jsonRows);
		}
		
		// NPT status
		jsonTable.put(NodeConfiguration.Table.nptCompiled.toString(), !en.isNptReCalcRequired());
		
		return jsonTable;
	}
	
	protected static JSONObject nodeMetaToJSON(ExtendedNode en) throws JSONException {
		JSONObject jsonMeta = new JSONObject();
		
		JSONArray jsonNotes = new JSONArray();
		for(Note note: (List<Note>)en.getNotes().getNotes()){
			JSONObject jsonNote = new JSONObject();
			jsonNote.put(Meta.Field.name.toString(), note.getNd().getShortDescription());
			jsonNote.put(Meta.Field.text.toString(), note.getNd().getLongDescription());
			jsonNotes.put(jsonNote);
		}
		jsonMeta.put(Meta.Field.notes.toString(), jsonNotes);
		
		return jsonMeta;
	}
	
	protected static JSONObject nodeConfigurationToJSON(ExtendedNode en) throws JSONException {
		return null;
	}
}
