package com.agenarisk.api.tools;

import com.agenarisk.api.exception.SensitivityAnalyserException;
import com.agenarisk.api.exception.AdapterException;
import com.agenarisk.api.exception.CalculationException;
import com.agenarisk.api.exception.InconsistentEvidenceException;
import com.agenarisk.api.exception.ModelException;
import com.agenarisk.api.exception.NodeException;
import com.agenarisk.api.model.CalculationResult;
import com.agenarisk.api.model.DataSet;
import com.agenarisk.api.model.Model;
import com.agenarisk.api.model.Network;
import com.agenarisk.api.model.Node;
import com.agenarisk.api.model.ResultValue;
import com.agenarisk.api.model.State;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import uk.co.agena.minerva.util.helpers.MathsHelper;
import uk.co.agena.minerva.util.model.IntervalDataPoint;
import uk.co.agena.minerva.util.model.Range;

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

	/**
	 * Maps nodes to their original calculation results
	 */
	private Map<Node, CalculationResult> bufResultsOriginal = new HashMap<>();

	/**
	 * Maps Nodes to their respective calculated SA values
	 */
	private final Map<Node, LinkedHashMap<BufferedCalculationKey, Double>> bufSACalcs = new HashMap<>();

	/**
	 * Maps Nodes to their respective SA summary stats
	 */
	private final Map<Node, LinkedHashMap<BufferedStatisticKey, Double>> bufSAStats = new HashMap<>();

	/**
	 * Statistics limited within set percentiles. Values outside of percentiles are set to Double.NaN.
	 */
	private final Map<Node, LinkedHashMap<BufferedStatisticKey, Double>> bufferedStatsLim = new HashMap<>();

	/**
	 * Constructor for Sensitivity Analysis tool.<br>
	 * The Model will be factorised and converted to static taking into account any overriding model settings and observations pre-entered into the selected DataSet.<br>
	 * If no DataSet or Network are specified, the first one of each will be used.<br>
	 * 
	 * @param model Model to run analysis on
	 * @param sensitivityAnalyserConfiguration configuration to override defaults for analysis
	 * 
	 * @throws SensitivityAnalyserException upon failure
	 */
	public SensitivityAnalyser(Model model, JSONObject sensitivityAnalyserConfiguration) throws SensitivityAnalyserException {
		JSONObject json = sensitivityAnalyserConfiguration;
		this.jsonConfig = json;

		if (model == null) {
			throw new SensitivityAnalyserException("Model not provided");
		}

		// Create a copy of the original model
		try {
			model = Model.createModel(model.export(Model.ExportFlags.KEEP_OBSERVATIONS));
		}
		catch (AdapterException | JSONException | ModelException ex) {
			throw new SensitivityAnalyserException("Initialization failed", ex);
		}

		// Factorise
		try {
			model.factorize();
		}
		catch (Exception ex) {
			throw new SensitivityAnalyserException("Factorization failed", ex);
		}

		this.model = model;

		// Get model settings
		model.getSettings().fromJson(json.optJSONObject("modelSettings"));

		// Get report settings
		JSONObject jsonReportSettings = json.optJSONObject("reportSettings");

		if (jsonReportSettings != null) {
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
		if (json.has("dataSet")) {
			dataSet = model.getDataSet(json.optString("dataSet", ""));
		}
		else {
			dataSet = model.getDataSetList().get(0);
		}
		model.getDataSetList().forEach(ds -> {
			if (!ds.equals(dataSet)) {
				this.model.removeDataSet(ds);
			}
		});

		// Get target Node
		Network network;
		JSONObject jsonTarget = json.getJSONObject("target");
		if (jsonTarget.has("network")) {
			network = model.getNetwork(jsonTarget.getString("network"));
		}
		else {
			network = model.getNetworkList().get(0);
		}
		targetNode = network.getNode(jsonTarget.optString("node", ""));

		if (targetNode == null) {
			throw new SensitivityAnalyserException("Target node not specified");
		}

		// Get sensitivity nodes
		JSONArray sensitivityNodes = json.optJSONArray("sensitivityNodes");
		if (sensitivityNodes != null) {
			sensitivityNodes.forEach(o -> {
				this.sensitivityNodes.add(network.getNode(String.valueOf(o)));
			});
		}
		if (this.sensitivityNodes.isEmpty()) {
			throw new SensitivityAnalyserException("No sensitivity nodes specified");
		}

		// Convert to static
		// Precalculate if required
		if (!model.isCalculated()) {
			try {
				model.calculate();
			}
			catch (CalculationException ex) {
				throw new SensitivityAnalyserException("Failed to precalculate the model during initialization", ex);
			}
		}
		try {
			model.convertToStatic(dataSet);
		}
		catch (NodeException ex) {
			throw new SensitivityAnalyserException("Static conversion failed", ex);
		}

	}
	
	/**
	 * Performs sensitivity analysis.
	 * 
	 * @throws SensitivityAnalyserException upon failure
	 */
	public void analyse() throws SensitivityAnalyserException {
		calculateCombinations();
		calculateStats();
	}
	
	public JSONObject buildTables(){
		return null;
	}
	
	public JSONObject buildTornadoes(){
		return null;
	}
	
	public JSONObject buildCurves(){
		return null;
	}

	/**
	 * Explores and calculates possible combinations of observed states for target and sensitivity nodes.
	 * 
	 * @throws SensitivityAnalyserException upon failure
	 */
	private void calculateCombinations() throws SensitivityAnalyserException {
		bufResultsOriginal = dataSet.getCalculationResults(targetNode.getNetwork());

		CalculationResult tarCalcOri = bufResultsOriginal.get(targetNode);
		ArrayList<ResultValue> tarResValOri = new ArrayList<>(tarCalcOri.getResultValues());

		List<State> tarStates = targetNode.getStates();
		for (int indexTarResVal = 0; indexTarResVal < targetNode.getLogicNode().getExtendedStates().size(); indexTarResVal++) {
			State tarState = tarStates.get(indexTarResVal);

			String tarObsVal = tarState.getLabel();

			if (targetNode.isNumericInterval()) {
				tarObsVal = "" + tarState.getLogicState().getNumericalValue();
			}

			dataSet.setObservation(targetNode, tarObsVal);
			try {
				model.calculate();
			}
			catch (InconsistentEvidenceException ex) {
				// Inconsistent evidence means we just skip this state
				// For other calculation failures we exit analysis altogether
				continue;
			}
			catch (CalculationException ex) {
				throw new SensitivityAnalyserException("Calculation failure", ex);
			}

			Map<Node, CalculationResult> resultsSubjective = dataSet.getCalculationResults(targetNode.getNetwork());

			ResultValue tvO = tarResValOri.get(indexTarResVal);

			CalculationResult tarCalcSub = resultsSubjective.get(targetNode);
			ArrayList<ResultValue> tarResValSub = new ArrayList<>(tarCalcSub.getResultValues());
			ResultValue tvS = tarResValSub.get(indexTarResVal);

			if (tarResValSub.size() != targetNode.getLogicNode().getExtendedStates().size()) {
				throw new SensitivityAnalyserException("Calculation result size does not match for target node");
			}

			for (Node sensitivityNode : sensitivityNodes) {

				// Record p(T = t|e, X = x), p(T = t | e), p(X = x|e)
				if (!bufSACalcs.containsKey(sensitivityNode)) {
					bufSACalcs.put(sensitivityNode, new LinkedHashMap<>());
				}

				CalculationResult sensCalcOri = bufResultsOriginal.get(sensitivityNode);
				CalculationResult sensCalcSub = resultsSubjective.get(sensitivityNode);

				ArrayList<ResultValue> sensResValOri = new ArrayList<>(sensCalcOri.getResultValues());
				ArrayList<ResultValue> sensResValSub = new ArrayList<>(sensCalcSub.getResultValues());

				if (sensResValOri.size() != sensResValSub.size()) {
					throw new SensitivityAnalyserException("Calculation result size does not match for node " + sensitivityNode.toStringExtra());
				}

				List<State> sensStates = targetNode.getStates();
				for (int indexSensResVal = 0; indexSensResVal < sensResValOri.size(); indexSensResVal++) {
					State sensState = sensStates.get(indexSensResVal);
					ResultValue rvO = sensResValOri.get(indexSensResVal);
					ResultValue rvS = sensResValSub.get(indexSensResVal);

					double value = rvS.getValue();
					// item divided by p(S)
					double reverse = (value * tvO.getValue()) / rvO.getValue();

					bufSACalcs.get(sensitivityNode).put(
							new BufferedCalculationKey(sensitivityNode, sensState.getLabel(), tarState.getLabel()),
							value
					);

					bufSACalcs.get(sensitivityNode).put(
							new BufferedCalculationKey(targetNode, tarState.getLabel(), sensState.getLabel()),
							reverse
					);
				}
			}
		}
		dataSet.clearObservation(targetNode);
	}

	/**
	 * Calculates summary statistics and limited summary statistics.
	 * 
	 * @throws SensitivityAnalyserException upon failure
	 */
	private void calculateStats() throws SensitivityAnalyserException {
		List<State> tarStates = targetNode.getStates();
		
		for (Node sensitivityNode : sensitivityNodes) {

			if (!Arrays.asList(Node.Type.ContinuousInterval, Node.Type.IntegerInterval, Node.Type.Ranked).contains(sensitivityNode.getType())) {
				// Skip inherently labelled nodes
				continue;
			}

			uk.co.agena.minerva.util.model.DataSet targetA1DataSet = new uk.co.agena.minerva.util.model.DataSet();
			uk.co.agena.minerva.util.model.DataSet targetA1DataSetLim = new uk.co.agena.minerva.util.model.DataSet();

			uk.co.agena.minerva.util.model.DataSet tempA1ResultsOriginal = (uk.co.agena.minerva.util.model.DataSet) bufResultsOriginal.get(sensitivityNode).getLogicCalculationResult().getDataset().clone();

			if (sensitivityNode.isNumericInterval()) {
				try {
					double actUpperP = MathsHelper.percentile(sensUpperPercentileValue, tempA1ResultsOriginal);
					double actLowerP = MathsHelper.percentile(sensLowerPercentileValue, tempA1ResultsOriginal);
					for (int i = 0; i < tempA1ResultsOriginal.size(); i++) {
						IntervalDataPoint dp = (IntervalDataPoint) tempA1ResultsOriginal.getDataPointAtOrderPosition(i);
						if (dp.getIntervalUpperBound() < actLowerP || dp.getIntervalLowerBound() > actUpperP) {
							dp.setValue(Double.NaN);
						}
					}
				}
				catch (Exception ex) {
					throw new SensitivityAnalyserException("Failed to apply continuous node sensitivity percentiles", ex);
				}
			}

			if (!bufSAStats.containsKey(sensitivityNode)) {
				bufSAStats.put(sensitivityNode, new LinkedHashMap<>());
			}

			if (!bufferedStatsLim.containsKey(sensitivityNode)) {
				bufferedStatsLim.put(sensitivityNode, new LinkedHashMap<>());
			}

			List<State> sensStates = sensitivityNode.getStates();
			for (int indexSensState = 0; indexSensState < sensitivityNode.getLogicNode().getExtendedStates().size(); indexSensState++) {
				State sensState = sensStates.get(indexSensState);

				double mean = 0.0, median = 0.0, variance = 0.0;
				double meanLim = 0.0, medianLim = 0.0, varianceLim = 0.0;
				double standardDeviation = 0.0, upperPercentile = 0.0, lowerPercentile = 0.0;
				double standardDeviationLim = 0.0, upperPercentileLim = 0.0, lowerPercentileLim = 0.0;

				double[] xVals = new double[tarStates.size()];
				double[] pXs = new double[tarStates.size()];
				double[] pXsWithZero = new double[tarStates.size()];
				Range[] xIntervals = new Range[tarStates.size()];

				boolean limAllNAN = Double.isNaN(bufResultsOriginal.get(sensitivityNode).getResultValues().get(indexSensState).getValue());

				for (int indexTarState = 0; indexTarState < targetNode.getLogicNode().getExtendedStates().size(); indexTarState++) {
					State tarState = tarStates.get(indexTarState);
					Range r = tarState.getLogicState().getRange();
					try {
						r = MathsHelper.scaleInfinities(r);
					}
					catch (Exception ex) {
						throw new SensitivityAnalyserException("Failed to scale infinities for state range " + r, ex);
					}
					xIntervals[indexTarState] = r;

					if (bufResultsOriginal.get(targetNode).getResultValues().get(indexTarState).getValue() == 0) {
						continue;
					}

					double dbl = bufSACalcs.get(sensitivityNode).get(new BufferedCalculationKey(targetNode, tarState.getLabel(), sensState.getLabel()));
					double dblWithZero = Double.NaN;

					try {
						if (!Double.isNaN(tempA1ResultsOriginal.getDataPointAtOrderPosition(indexTarState).getValue())) {
							dblWithZero = dbl;
						}
					}
					catch (Exception ex) {
						throw new SensitivityAnalyserException(ex);
					}

					xVals[indexTarState] = tarState.getLogicState().getNumericalValue();
					pXs[indexTarState] = dbl;
					pXsWithZero[indexTarState] = dblWithZero;

					// want to create a dataSet to pass to new variance calcualtion routine                    
					IntervalDataPoint idp = new IntervalDataPoint();
					idp.setValue(dbl);
					idp.setIntervalLowerBound(r.getLowerBound());
					idp.setIntervalUpperBound(r.getUpperBound());
					targetA1DataSet.addDataPoint(idp);
					IntervalDataPoint idpWithZero = new IntervalDataPoint();
					idpWithZero.setValue(dblWithZero);
					idpWithZero.setIntervalLowerBound(r.getLowerBound());
					idpWithZero.setIntervalUpperBound(r.getUpperBound());
					targetA1DataSetLim.addDataPoint(idpWithZero);

					// get all p (T | Sn)
				}

				try {
					mean = MathsHelper.mean(pXs, xVals);
					meanLim = limAllNAN ? Double.NaN : MathsHelper.mean(pXsWithZero, xVals);
					variance = MathsHelper.variance(targetA1DataSet);
					varianceLim = limAllNAN ? Double.NaN : MathsHelper.variance(targetA1DataSetLim);
					standardDeviation = Math.sqrt(variance);
					standardDeviationLim = limAllNAN ? Double.NaN : Math.sqrt(varianceLim);
				}
				catch (Exception ex) {
					throw new SensitivityAnalyserException("Failed to calculate SA summary statistics", ex);
				}

				if (Double.isNaN(mean) && Double.isNaN(variance)) {
					median = Double.NaN;
					upperPercentile = Double.NaN;
					lowerPercentile = Double.NaN;
					medianLim = Double.NaN;
					upperPercentileLim = Double.NaN;
					lowerPercentileLim = Double.NaN;
				}
				else {
					try {
						median = MathsHelper.percentile(50, pXs, xIntervals);
						upperPercentile = MathsHelper.percentile(sumsUpperPercentileValue, pXs, xIntervals);
						lowerPercentile = MathsHelper.percentile(sumsLowerPercentileValue, pXs, xIntervals);
						medianLim = limAllNAN ? Double.NaN : MathsHelper.percentile(50, pXsWithZero, xIntervals);
						upperPercentileLim = limAllNAN ? Double.NaN : MathsHelper.percentile(sumsUpperPercentileValue, pXsWithZero, xIntervals);
						lowerPercentileLim = limAllNAN ? Double.NaN : MathsHelper.percentile(sumsLowerPercentileValue, pXsWithZero, xIntervals);
					}
					catch (Exception ex) {
						throw new SensitivityAnalyserException("Failed to calculate Sensitivity summary statistics", ex);
					}
				}

				bufSAStats.get(sensitivityNode).put(new BufferedStatisticKey(BufferedStatisticKey.STAT.mean, sensState.getLabel()), mean);
				bufSAStats.get(sensitivityNode).put(new BufferedStatisticKey(BufferedStatisticKey.STAT.median, sensState.getLabel()), median);
				bufSAStats.get(sensitivityNode).put(new BufferedStatisticKey(BufferedStatisticKey.STAT.variance, sensState.getLabel()), variance);
				bufSAStats.get(sensitivityNode).put(new BufferedStatisticKey(BufferedStatisticKey.STAT.standardDeviation, sensState.getLabel()), standardDeviation);
				bufSAStats.get(sensitivityNode).put(new BufferedStatisticKey(BufferedStatisticKey.STAT.upperPercentile, sensState.getLabel()), upperPercentile);
				bufSAStats.get(sensitivityNode).put(new BufferedStatisticKey(BufferedStatisticKey.STAT.lowerPercentile, sensState.getLabel()), lowerPercentile);

				bufferedStatsLim.get(sensitivityNode).put(new BufferedStatisticKey(BufferedStatisticKey.STAT.mean, sensState.getLabel()), meanLim);
				bufferedStatsLim.get(sensitivityNode).put(new BufferedStatisticKey(BufferedStatisticKey.STAT.median, sensState.getLabel()), medianLim);
				bufferedStatsLim.get(sensitivityNode).put(new BufferedStatisticKey(BufferedStatisticKey.STAT.variance, sensState.getLabel()), varianceLim);
				bufferedStatsLim.get(sensitivityNode).put(new BufferedStatisticKey(BufferedStatisticKey.STAT.standardDeviation, sensState.getLabel()), standardDeviationLim);
				bufferedStatsLim.get(sensitivityNode).put(new BufferedStatisticKey(BufferedStatisticKey.STAT.upperPercentile, sensState.getLabel()), upperPercentileLim);
				bufferedStatsLim.get(sensitivityNode).put(new BufferedStatisticKey(BufferedStatisticKey.STAT.lowerPercentile, sensState.getLabel()), lowerPercentileLim);
			}
		}
	}

	private class BufferedCalculationKey {

		final Node node;
		final String nodeState, calcState;

		public BufferedCalculationKey(Node node, String nodeState, String calcState) {
			this.node = node;
			this.nodeState = nodeState;
			this.calcState = calcState;
		}

		public Node getNode() {
			return node;
		}

		public String getNodeState() {
			return nodeState;
		}

		public String getCalcState() {
			return calcState;
		}

		@Override
		public int hashCode() {
			int hash = 3;
			hash = 67 * hash + Objects.hashCode(this.node);
			hash = 67 * hash + Objects.hashCode(this.nodeState);
			hash = 67 * hash + Objects.hashCode(this.calcState);
			return hash;
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj) {
				return true;
			}
			if (obj == null) {
				return false;
			}
			if (getClass() != obj.getClass()) {
				return false;
			}

			final BufferedCalculationKey other = (BufferedCalculationKey) obj;

			if (!Objects.equals(this.nodeState, other.nodeState)) {
				return false;
			}
			if (!Objects.equals(this.calcState, other.calcState)) {
				return false;
			}
			if (!Objects.equals(this.node, other.node)) {
				return false;
			}
			return true;
		}

	}

	private static class BufferedStatisticKey {

		enum STAT {
			mean,
			median,
			variance,
			standardDeviation,
			upperPercentile,
			lowerPercentile
		}

		private final STAT stat;
		private final String calcState;

		public BufferedStatisticKey(STAT stat, String calcState) {
			this.stat = stat;
			this.calcState = calcState;
		}

		public STAT getStat() {
			return stat;
		}

		public String getCalcState() {
			return calcState;
		}

		@Override
		public int hashCode() {
			int hash = 7;
			hash = 29 * hash + Objects.hashCode(this.stat);
			hash = 29 * hash + Objects.hashCode(this.calcState);
			return hash;
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj) {
				return true;
			}
			if (obj == null) {
				return false;
			}
			if (getClass() != obj.getClass()) {
				return false;
			}
			final BufferedStatisticKey other = (BufferedStatisticKey) obj;
			if (!Objects.equals(this.calcState, other.calcState)) {
				return false;
			}
			if (this.stat != other.stat) {
				return false;
			}
			return true;
		}

	}

}
