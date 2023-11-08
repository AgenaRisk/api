package com.agenarisk.api.io;

import com.agenarisk.api.exception.AdapterException;
import com.agenarisk.api.exception.AgenaRiskRuntimeException;
import com.agenarisk.api.io.stub.Meta;
import com.agenarisk.api.io.stub.NodeGraphics;
import com.agenarisk.api.model.NodeConfiguration;
import com.agenarisk.api.io.stub.RiskTable;
import com.agenarisk.api.io.stub.SummaryStatistic;
import com.agenarisk.api.model.Settings;
import com.agenarisk.api.model.CalculationResult;
import com.agenarisk.api.model.CrossNetworkLink;
import com.agenarisk.api.model.Link;
import com.agenarisk.api.model.Network;
import com.agenarisk.api.model.Node;
import com.agenarisk.api.model.State;
import com.agenarisk.api.model.ResultValue;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
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
import uk.co.agena.minerva.model.questionnaire.Answer;
import uk.co.agena.minerva.model.questionnaire.Question;
import uk.co.agena.minerva.model.questionnaire.Questionnaire;
import uk.co.agena.minerva.model.scenario.Observation;
import uk.co.agena.minerva.model.scenario.Scenario;
import uk.co.agena.minerva.util.Logger;
import uk.co.agena.minerva.util.model.DataPoint;
import uk.co.agena.minerva.util.model.DataSet;
import uk.co.agena.minerva.util.model.IntervalDataPoint;
import uk.co.agena.minerva.util.model.ModificationLog;
import uk.co.agena.minerva.util.model.Note;
import uk.co.agena.minerva.util.model.Variable;

/**
 * JSONAdapter enables encoding of an API1 model as JSON.
 * 
 * @author Eugene Dementiev
 */
public class JSONAdapter {
	
	public static final boolean CACHE_NPTS = true;
	
	public static JSONObject toJSONObject(Model model) throws JSONException, AdapterException {
		JSONObject json = new JSONObject();
		
		// Model
		JSONObject jsonModel = new JSONObject();
		json.put(com.agenarisk.api.model.Model.Field.model.toString(), jsonModel);
		
		// Networks
		jsonModel.put(Network.Field.networks.toString(), modelNetworksToJSON(model));
		
		// Cross network links
		jsonModel.put(Link.Field.links.toString(), modelLinksToJSON(model));
		
		// Settings
		jsonModel.put(Settings.Field.settings.toString(), Settings.toJson(model));

		// Scenarios
		jsonModel.put(com.agenarisk.api.model.DataSet.Field.dataSets.toString(), modelScenariosToJSON(model));
		
		// Meta
		if (!model.getNotes().getNotes().isEmpty()){
			jsonModel.put(Meta.Field.meta.toString(), modelMetaToJSON(model));
		}
		
		// Risk Table
		jsonModel.put(RiskTable.Field.riskTable.toString(), modelRiskTableToJSON(model));
		
		// Texts
		
		// Pictures
		
		// Graphics
		
		return json;
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
				Logger.printThrowableIfDebug(ex);
			}
		}
		jsonDataSet.put(com.agenarisk.api.model.Observation.Field.observations.toString(), jsonObservations);
		
		jsonDataSet.put(com.agenarisk.api.model.DataSet.Field.active.toString(), scenario.isReportable());
		jsonDataSet.put(com.agenarisk.api.model.DataSet.Field.displayable.toString(), scenario.isDisplayOnRiskGraphs());
		
		// Results
		JSONArray jsonResults = resultsToJSON(model, scenario);
		jsonDataSet.put(com.agenarisk.api.model.CalculationResult.Field.results.toString(), jsonResults);
		
		JSONObject logPe = new JSONObject(scenario.getLogPeMap());
		jsonDataSet.put(com.agenarisk.api.model.DataSet.Field.logPe.toString(), logPe);
		
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
				
				MarginalDataItem mdi;
				DataSet ds;
				try {
					mdi = model.getMarginalDataStore().getMarginalDataItemListForNode(ebn, en).getMarginalDataItemAtIndex(scenarioIndex);
					ds = mdi.getDataset();
				}
				catch(NullPointerException ex){
					Logger.logIfDebug("No MDI or DataSet for `" + ebn.getConnID() + "`.`" + en.getConnNodeId() + "` during API1 to API2 conversion", 5);
					continue;
				}
				
				if (en instanceof NumericalEN){
					try {
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
					catch (JSONException ex){
						if (ex.getMessage().contains("JSON does not allow non-finite numbers")){
							throw new AgenaRiskRuntimeException("Result data is corrupt, please recalculate the model", ex);
						}
						
						throw ex;
					}
				}
				
				JSONArray jsonResultValues = new JSONArray();
				for(DataPoint dp: (List<DataPoint>) ds.getDataPoints()){
					
					JSONObject jsonResultValue = new JSONObject();
					
					String label = dp.getLabel();
					if (dp.getConnObjectId() >= 0){
						try {
							ExtendedState es = en.getExtendedState(dp.getConnObjectId());
							label = State.computeLabel(en, es);
						}
						catch(ExtendedStateNotFoundException ex) {
							// Ignore, will use data point label
						}
					}
					
					if (dp instanceof IntervalDataPoint && !(en instanceof RankedEN)){
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
		
		boolean nodeVariableObservation = false;
		if (!observation.getExpressionVariableName().isEmpty()){
			jsonObservation.put(com.agenarisk.api.model.Observation.Field.constantName.toString(), observation.getExpressionVariableName());
			nodeVariableObservation = true;
		}
		
		JSONArray jsonEntries = new JSONArray();
		
		if (!nodeVariableObservation && (en instanceof RankedEN || en instanceof LabelledEN || en instanceof DiscreteRealEN)){
			// Hard / Soft observation with arc dataset
			// Can be linked to a specific state
			DataSet ds = observation.getDataSet();
			for(DataPoint dp: (List<DataPoint>) ds.getDataPoints()){
				JSONObject jsonEntry = new JSONObject();
				
				String value = observation.getUserEnteredAnswer();
				ExtendedState es = null;
				try {
					es = en.getExtendedState(dp.getConnObjectId());
				}
				catch (ExtendedStateNotFoundException ex){
				}
				if (es != null){
					value = es.getName().getShortDescription();
				}
				
				jsonEntry.put(com.agenarisk.api.model.Observation.Field.value.toString(), value);
				jsonEntry.put(com.agenarisk.api.model.Observation.Field.weight.toString(), dp.getValue());
				jsonEntries.put(jsonEntry);
			}
		}
		else {
			// Hard observation with specific value or node variable observation
			// Not linked to a specific state
			JSONObject jsonEntry = new JSONObject();
			String observationAnswer = observation.getUserEnteredAnswer();
			
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
					Logger.printThrowableIfDebug(ex);
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
	
	public static JSONObject toJSONObject(ExtendedBN ebn) throws JSONException, ExtendedBNException {
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
	
	protected static JSONArray modelRiskTableToJSON(Model model) throws JSONException, AdapterException {
		JSONArray jsonRiskTable = new JSONArray();
		for (Questionnaire qstr: (List<Questionnaire>) model.getQuestionnaireList().getQuestionnaires()){
			JSONObject jsonQstr = new JSONObject();
			
			jsonQstr.put(RiskTable.Questionnaire.name.toString(), qstr.getName().getShortDescription());
			jsonQstr.put(RiskTable.Questionnaire.description.toString(), qstr.getName().getLongDescription());
			
			JSONArray jsonQstns = new JSONArray();
			for(Question qstn: (List<Question>) qstr.getQuestions()){
				JSONObject jsonQstn = new JSONObject();
				
				jsonQstn.put(RiskTable.Question.name.toString(), qstn.getName().getShortDescription());
				jsonQstn.put(RiskTable.Question.description.toString(), qstn.getName().getLongDescription());
				
				ExtendedBN ebn = null;
				ExtendedNode en = null;
				
				try {
					ebn = model.getExtendedBN(qstn.getConnExtendedBNId());
					en = ebn.getExtendedNode(qstn.getConnExtendedNodeId());
				}
				catch (ExtendedBNNotFoundException | ExtendedNodeNotFoundException ex){
					Logger.logIfDebug("Questionnaire Network or node not found: " + qstn.getName(), 10);
					Logger.printThrowableIfDebug(ex, 10);
					continue;
				}
				
				jsonQstn.put(RiskTable.Question.network.toString(), ebn.getConnID());
				jsonQstn.put(RiskTable.Question.node.toString(), en.getConnNodeId());
				
				String questionMode = "";
				String questionType = RiskTable.QuestionType.observation.toString();
				
				switch(qstn.getRecommendedAnsweringMode()){
					case Question.ANSWER_BY_SELECTION:
						questionMode = RiskTable.QuestionMode.selection.toString();
						break;
					case Question.ANSWER_BY_UNANSWERABLE:
						questionMode = RiskTable.QuestionMode.unanswerable.toString();
						break;
					case Question.ANSWER_NUMERICALLY:
						questionMode = RiskTable.QuestionMode.numerical.toString();
						break;
					case Question.ANSWER_AS_EXPRESSION_VARIABLE:
						questionMode = RiskTable.QuestionMode.numerical.toString();
						questionType = RiskTable.QuestionType.constant.toString();
						break;
					default: throw new AdapterException("Invalid questionnaire mode: " + qstn.getRecommendedAnsweringMode());
				}

				if (qstn.getRecommendedAnsweringMode() == Question.ANSWER_AS_EXPRESSION_VARIABLE){
					jsonQstn.put(RiskTable.Question.constantName.toString(), qstn.getExpressionVariableName());
				}
				
				jsonQstn.put(RiskTable.Question.mode.toString(), questionMode);
				jsonQstn.put(RiskTable.Question.type.toString(), questionType);
				
				jsonQstn.put(RiskTable.Question.visible.toString(), qstn.getVisible());
				jsonQstn.put(RiskTable.Question.syncName.toString(), qstn.isSyncToConnectedNodeName());
				
				JSONArray jsonAnsws = new JSONArray();
				
				// Check that all answer mappings are valid
				// Reset answers if not
				for(Answer answ: (List<Answer>) qstn.getAnswers()){
					try {
						int stateId = answ.getConnExtendedStateId();
						ExtendedState correspondingState = en.getExtendedState(stateId);
					}
					catch(ExtendedStateNotFoundException ex){
						// Use default answers
						Logger.logIfDebug("Resetting answers to state mapping " + en.getConnNodeId() + " [" + en.getName().getShortDescription() + "]", 10);
						Logger.logIfDebug("Broken answer was: " + answ.getName() + " ["+answ.getConnExtendedStateId()+"]", 10);
//						Environment.printThrowableIfDebug(ex);
						Question tempQstn = uk.co.agena.minerva.model.Model.generateQuestionFromNode(ebn, en);
						qstn.setAnswers(tempQstn.getAnswers());
						break;
					}
				}
				
				for(Answer answ: (List<Answer>) qstn.getAnswers()){
					JSONObject jsonAnsw = new JSONObject();
					
					jsonAnsw.put(RiskTable.Answer.name.toString(), answ.getName().getShortDescription());
					
					try {
						int stateId = answ.getConnExtendedStateId();
						ExtendedState correspondingState = en.getExtendedState(stateId);
						jsonAnsw.put(RiskTable.Answer.state.toString(), State.computeLabel(en, correspondingState).trim());
					}
					catch(ExtendedStateNotFoundException ex){
						throw new AdapterException("Questionnaire answer state not found in node " + en.getConnNodeId() + "[" + en.getName().getShortDescription() + "]", ex);
					}
					
					jsonAnsws.put(jsonAnsw);
				}
				jsonQstn.put(RiskTable.Answer.answers.toString(), jsonAnsws);
				jsonQstns.put(jsonQstn);
				
			}
			jsonQstr.put(RiskTable.Question.questions.toString(), jsonQstns);
			jsonRiskTable.put(jsonQstr);
		}
		
		return jsonRiskTable;
	}
	
	public static JSONObject toJSONObject(ExtendedNode en) throws JSONException {
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
		JSONObject jsonGraphics = nodeGraphicsToJSON(en);
		if (!jsonGraphics.isEmpty()){
			json.put(NodeGraphics.Field.graphics.toString(), jsonGraphics);
		}
		
		return json;
	}
	
	protected static JSONObject nodeConfigToJSON(ExtendedNode en) throws JSONException {
		JSONObject jsonConfig = new JSONObject();
		
		// Type
		Node.Type nodeType = NodeConfiguration.resolveNodeType(en);
		jsonConfig.put(NodeConfiguration.Field.type.toString(), nodeType.toString());
		
		if (en.isConnectableInputNode()){
			jsonConfig.put(NodeConfiguration.Field.input.toString(), en.isConnectableInputNode());
		}
		
		if (en.isConnectableOutputNode()){
			jsonConfig.put(NodeConfiguration.Field.output.toString(), en.isConnectableOutputNode());
		}
		
		// Simulated
		boolean simulated = false;
		if (en instanceof ContinuousEN && !(en instanceof RankedEN)){
			ContinuousEN cen = (ContinuousEN) en;
			simulated = cen.isSimulationNode();
			jsonConfig.putOpt(NodeConfiguration.Field.simulated.toString(), simulated?true:null);
			
			if (simulated){
				if (cen.getEntropyConvergenceThreshold() >= 0){
					jsonConfig.put(NodeConfiguration.Field.simulationConvergence.toString(), cen.getEntropyConvergenceThreshold());
				}
			}
			
			List<Double> listPercentiles = cen.getPercentileSettingsOnNodeForScenario(null).subList(1, 3);
			if(Objects.equals(listPercentiles.get(0), 25d) &&  Objects.equals(listPercentiles.get(1), 75d)){
				// Default percentiles, ignore
			}
			else {
				JSONObject jPercentiles = new JSONObject();
				jPercentiles.put(NodeConfiguration.Percentiles.lowerPercentile.toString(), listPercentiles.get(0));
				jPercentiles.put(NodeConfiguration.Percentiles.upperPercentile.toString(), listPercentiles.get(1));
				jsonConfig.put(NodeConfiguration.Field.percentiles.toString(), jPercentiles);
			}
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
			jsonConfig.put(State.Field.states.toString(), jsonStates);
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
	
	protected static JSONObject nodeGraphicsToJSON(ExtendedNode en) throws JSONException {
		JSONObject jsonGraphics = new JSONObject();
		if (!en.getVisible()){
			// For now, only record this if it isn't default (true)
			jsonGraphics.put(NodeGraphics.Field.visible.toString(), en.getVisible());
		}
		return jsonGraphics;
	}
	
	public static JSONObject nodeTableToJSON(ExtendedNode en) throws JSONException {
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
		
		boolean simulated = en instanceof ContinuousEN && ((ContinuousEN)en).isSimulationNode();
		
		if (tableType.equals(NodeConfiguration.TableType.Manual) && simulated){
			// Node is simulated but somehow the table is marked as manual which isn't possible
			tableType = NodeConfiguration.TableType.Expression;
		}
		
		jsonTable.put(NodeConfiguration.Table.type.toString(), tableType.toString());
		
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
			String expressionString = "";
			try {
				expressionString = enf.getName().replaceAll(" ", "");
			}
			catch (NullPointerException ex){
				throw new AgenaRiskRuntimeException("Node `" + en.getName().getShortDescription() + " (" + en.getConnNodeId() + ")`" + " is expected to have expressions, but has none", ex);
			}
			expressionString += "(" + enf.getParameters().stream().collect(Collectors.joining(",")) + ")";
			jsonExpressions.put(expressionString);
		}
		
		if (!enfs.isEmpty()){
			jsonTable.put(NodeConfiguration.Table.expressions.toString(), jsonExpressions);
		}
		
		// Probabilities
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
				for(float p: row){
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

}
