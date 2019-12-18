package com.agenarisk.api.tools;

import com.agenarisk.api.exception.SensitivityAnalyserException;
import com.agenarisk.api.exception.AdapterException;
import com.agenarisk.api.exception.CalculationException;
import com.agenarisk.api.exception.InconsistentEvidenceException;
import com.agenarisk.api.exception.ModelException;
import com.agenarisk.api.exception.NodeException;
import com.agenarisk.api.io.JSONAdapter;
import com.agenarisk.api.model.CalculationResult;
import com.agenarisk.api.model.DataSet;
import com.agenarisk.api.model.Model;
import com.agenarisk.api.model.Network;
import com.agenarisk.api.model.Node;
import com.agenarisk.api.model.ResultValue;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import uk.co.agena.minerva.model.extendedbn.ExtendedState;

/**
 *
 * @author Eugene Dementiev
 */
public class SensitivityAnalyser {
	private Model model;
	private Node targetNode;
	private final LinkedHashSet<Node> sensitivityNodes = new LinkedHashSet<>();
	private DataSet dataSet;
	
	private final JSONObject jsonConfig;
	
	private boolean sumsMean = false;
	private boolean sumsMedian = false;
	private boolean sumsVariance = false;
	private boolean sumsStDev = false;
	private boolean sumsLowerPercentile = false;
	private boolean sumsUpperPercentile = false;
	
	private double sumsLowerPercentileValue = 25d;
	private double sumsUpperPercentileValue = 75d;
	
	private double sensLowerPercentileValue = 0d;
	private double sensUpperPercentileValue = 100d;
	
	private boolean viewTable = false;
	private boolean viewResponseCurve = false;
	private boolean viewTornadoGraph = false;
	
	public SensitivityAnalyser(Model model, JSONObject sensitivityAnalyserConfiguration) throws SensitivityAnalyserException {
		JSONObject json = sensitivityAnalyserConfiguration;
		this.jsonConfig = json;
		
		if (model == null){
			throw new SensitivityAnalyserException("Model not provided");
		}
		
		// Create a copy of the original model
		try {
			model = Model.createModel(model.export(Model.ExportFlags.KEEP_OBSERVATIONS));
		}
		catch (AdapterException | JSONException | ModelException ex){
			throw new SensitivityAnalyserException("Initialization failed", ex);
		}
		
		// Factorise
		try {
			model.factorize();
		}
		catch (Exception ex){
			throw new SensitivityAnalyserException("Factorization failed", ex);
		}
		
		this.model = model;
		
		// Get model settings
		model.getSettings().fromJson(json.optJSONObject("modelSettings"));
		
		// Get report settings
		JSONObject jsonReportSettings = json.optJSONObject("reportSettings");
		
		if (jsonReportSettings != null){
			sumsMean = jsonReportSettings.optBoolean("sumsMean", false);
			sumsMedian = jsonReportSettings.optBoolean("sumsMedian", false);
			sumsVariance = jsonReportSettings.optBoolean("sumsVariance", false);
			sumsStDev = jsonReportSettings.optBoolean("sumsStDev", false);
			sumsLowerPercentile = jsonReportSettings.optBoolean("sumsLowerPercentile", false);
			sumsUpperPercentile = jsonReportSettings.optBoolean("sumsUpperPercentile", false);

			sumsLowerPercentileValue = jsonReportSettings.optDouble("sumsLowerPercentileValue", 25d);
			sumsUpperPercentileValue = jsonReportSettings.optDouble("sumsUpperPercentileValue", 75d);

			sensLowerPercentileValue = jsonReportSettings.optDouble("sensLowerPercentileValue", 0d);
			sensUpperPercentileValue = jsonReportSettings.optDouble("sensUpperPercentileValue", 100d);

			viewTable = jsonReportSettings.optBoolean("viewTable", false);
			viewResponseCurve = jsonReportSettings.optBoolean("viewResponseCurve", false);
			viewTornadoGraph = jsonReportSettings.optBoolean("viewTornadoGraph", false);
		}
		
		// Get DataSet
		if (json.has("dataSet")){
			dataSet = model.getDataSet(json.optString("dataSet", ""));
		}
		else {
			dataSet = model.getDataSetList().get(0);
		}
		model.getDataSetList().forEach(ds -> {
			if (!ds.equals(dataSet)){
				this.model.removeDataSet(ds);
			}
		});
		
		// Get target Node
		Network network;
		JSONObject jsonTarget = json.getJSONObject("target");
		if (jsonTarget.has("network")){
			network = model.getNetwork(jsonTarget.getString("network"));
		}
		else {
			network = model.getNetworkList().get(0);
		}
		targetNode = network.getNode(jsonTarget.optString("node", ""));
		
		if (targetNode == null){
			throw new SensitivityAnalyserException("Target node not specified");
		}
		
		// Get sensitivity nodes
		JSONArray sensitivityNodes = json.optJSONArray("sensitivityNodes");
		if (sensitivityNodes != null){
			sensitivityNodes.forEach(o -> {
				this.sensitivityNodes.add(network.getNode(String.valueOf(o)));
			});
		}
		if (this.sensitivityNodes.isEmpty()){
			throw new SensitivityAnalyserException("No sensitivity nodes specified");
		}
		
		// Convert to static
		// Precalculate if required
		if (!model.isCalculated()){
			try {
				model.calculate();
			}
			catch (CalculationException ex){
				throw new SensitivityAnalyserException("Failed to precalculate the model during initialization", ex);
			}
		}
		try {
			model.convertToStatic(dataSet);
		}
		catch (NodeException ex){
			throw new SensitivityAnalyserException("Static conversion failed", ex);
		}
		
	}
	
	public void analyse() throws SensitivityAnalyserException {
		
		Map<Node, CalculationResult> resultsOriginal = dataSet.getCalculationResults(targetNode.getNetwork());
		
		CalculationResult tarCalcOri = resultsOriginal.get(targetNode);
		ArrayList<ResultValue> tarResValOri = new ArrayList<>(tarCalcOri.getResultValues());
		
		for (int indexTarResVal = 0; indexTarResVal < targetNode.getLogicNode().getExtendedStates().size(); indexTarResVal++) {
			ExtendedState es = (ExtendedState) targetNode.getLogicNode().getExtendedStates().get(indexTarResVal);
			String observedValue = es.getName().getShortDescription();
			
			if (Arrays.asList(Node.Type.ContinuousInterval, Node.Type.IntegerInterval).contains(targetNode.getType())){
				observedValue = "" + es.getNumericalValue();
			}
			
			dataSet.setObservation(targetNode, observedValue);
			try {
				model.calculate();
			}
			catch (InconsistentEvidenceException ex){
				// Inconsistent evidence means we just skip this state
				// For other calculation failures we exit analysis altogether
				continue;
			}
			catch (CalculationException ex){
				throw new SensitivityAnalyserException("Calculation failure", ex);
			}
			
			Map<Node, CalculationResult> resultsSubjective = dataSet.getCalculationResults(targetNode.getNetwork());
			
			ResultValue tvO = tarResValOri.get(indexTarResVal);
			
			CalculationResult tarCalcSub = resultsSubjective.get(targetNode);
			ArrayList<ResultValue> tarResValSub = new ArrayList<>(tarCalcSub.getResultValues());
			ResultValue tvS = tarResValSub.get(indexTarResVal);
			
			if (tarResValSub.size() != targetNode.getLogicNode().getExtendedStates().size()){
				throw new SensitivityAnalyserException("Calculation result size does not match for target node");
			}
			
			for(Node sensitivityNode: sensitivityNodes){
				
				CalculationResult sensCalcOri = resultsOriginal.get(sensitivityNode);
				CalculationResult sensCalcSub = resultsSubjective.get(sensitivityNode);
				
				ArrayList<ResultValue> sensResValOri = new ArrayList<>(sensCalcOri.getResultValues());
				ArrayList<ResultValue> sensResValSub = new ArrayList<>(sensCalcSub.getResultValues());
				
				if (sensResValOri.size() != sensResValSub.size()){
					throw new SensitivityAnalyserException("Calculation result size does not match for node " + sensitivityNode.toStringExtra());
				}
				
				for (int indexSensResVal = 0; indexSensResVal < sensResValOri.size(); indexSensResVal++) {
					ResultValue rvO = sensResValOri.get(indexSensResVal);
					ResultValue rvS = sensResValSub.get(indexSensResVal);
					
					double value = rvS.getValue() * tvO.getValue();
					double reverse = value / rvO.getValue();
				}
			}
		}
		dataSet.clearObservation(targetNode);
	}
}
