package com.agenarisk.api.model;

import com.agenarisk.api.exception.ObservationException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.json.JSONArray;
import org.json.JSONObject;
import uk.co.agena.minerva.model.extendedbn.DiscreteRealEN;
import uk.co.agena.minerva.model.extendedbn.ExtendedNode;
import uk.co.agena.minerva.model.extendedbn.ExtendedState;
import uk.co.agena.minerva.model.extendedbn.ExtendedStateNotFoundException;
import uk.co.agena.minerva.model.extendedbn.LabelledEN;
import uk.co.agena.minerva.model.extendedbn.RankedEN;
import uk.co.agena.minerva.util.Logger;
import uk.co.agena.minerva.util.model.DataPoint;

/**
 * Observation class is a view of an observation, valid at the time of resolution and not maintained.
 * 
 * @author Eugene Dementiev
 */
public class Observation {
	
	/**
	 * This is set of fields for input/output to XML and JSON format
	 */
	public static enum Field {
		observations,
		observation,
		network,
		node,
		constantName,
		entries,
		entry,
		value,
		weight
	}

	/**
	 * DataSet that contains this Observation
	 */
	private final DataSet dataSet;
	
	/**
	 * Observed Node
	 */
	private final Node node;
	
	/**
	 * Map of observation entries
	 * <br>
	 * Hard observations will only contain one value
	 */
	private final Map<Object, Double> entries;
	
	/**
	 * Logic observation associated with this Observation
	 */
	private final uk.co.agena.minerva.model.scenario.Observation logicObservation;

	/**
	 * Constructor for the observation.
	 * 
	 * @param logicObservation Logic observation associated with this Observation
	 * @param dataSet DataSet that contains this Observation
	 * @param node observed Node
	 * 
	 * @throws ObservationException entries are empty or not provided
	 */
	protected Observation(uk.co.agena.minerva.model.scenario.Observation logicObservation, DataSet dataSet, Node node) throws ObservationException {
		this.node = node;
		this.dataSet = dataSet;
		this.logicObservation = logicObservation;
		this.entries = compileEntriesMap();
		
		if (entries == null || entries.isEmpty()){
			throw new ObservationException("Empty observation is not allowed");
		}
	}
	
	/**
	 * Returns a map of observation entries. Changes to the map are not tracked and have no effect on the model or dataset.
	 * 
	 * @return map of observation entries
	 */
	public Map<Object, Double> getEntries() {
		return entries;
	}
	
	/**
	 * Returns DataSet that contains this Observation.
	 * 
	 * @return DataSet that contains this Observation
	 */
	public DataSet getDataSet() {
		return dataSet;
	}

	/**
	 * Returns the observed Node.
	 * 
	 * @return the observed Node
	 */
	public Node getNode() {
		return node;
	}

	/**
	 * Getter for the logic observation associated with this Observation.
	 * 
	 * @return the logic observation associated with this Observation
	 */
	public uk.co.agena.minerva.model.scenario.Observation getLogicObservation() {
		return logicObservation;
	}
	
	private Map<Object, Double> compileEntriesMap(){
		Map<Object, Double> entries = new LinkedHashMap<>();

		switch(node.getType()){
			case ContinuousInterval:
				entries.put(Double.valueOf(logicObservation.getUserEnteredAnswer()), 1d);
				break;

			case IntegerInterval:
				entries.put(Double.valueOf(logicObservation.getUserEnteredAnswer()).intValue(), 1d);
				break;

			case DiscreteReal:
			case Boolean:
			case Labelled:
			case Ranked:
			default:
				List<DataPoint> dps = logicObservation.getDataSet().getDataPoints();
				for(DataPoint dp: dps){
					String key = logicObservation.getUserEnteredAnswer();
					ExtendedState es = null;
					try {
						es = node.getLogicNode().getExtendedState(dp.getConnObjectId());
					}
					catch (ExtendedStateNotFoundException ex){
						Logger.printThrowableIfDebug(ex);
					}
					if (es != null){
						key = es.getName().getShortDescription();
					}
					entries.put(key, dp.getValue());
				}
				break;
		}
		
		return entries;
	}
	
	/**
	 * Returns a JSON String representing this Observation.
	 * 
	 * @return JSON String representing this Observation
	 */
	@Override
	public String toString(){
		return toJson().toString();
	}
	
	/**
	 * Creates a JSONObject representation of this Observation
	 * 
	 * @return JSONObject equivalent of this Observation
	 */
	public JSONObject toJson(){
		JSONObject jsonObservation = new JSONObject();
		jsonObservation.put(com.agenarisk.api.model.Observation.Field.network.toString(), node.getNetwork().getId());
		jsonObservation.put(com.agenarisk.api.model.Observation.Field.node.toString(), node.getId());
		
		if (!logicObservation.getExpressionVariableName().isEmpty()){
			jsonObservation.put(com.agenarisk.api.model.Observation.Field.constantName.toString(), logicObservation.getExpressionVariableName());
		}
		
		JSONArray jsonEntries = new JSONArray();
		
		ExtendedNode en = node.getLogicNode();
		
		if (en instanceof RankedEN || en instanceof LabelledEN || en instanceof DiscreteRealEN){
			// Hard / Soft observation with arc dataset
			// Can be linked to a specific state
			for(DataPoint dp: (List<DataPoint>) logicObservation.getDataSet().getDataPoints()){
				JSONObject jsonEntry = new JSONObject();
				
				String value = logicObservation.getUserEnteredAnswer();
				ExtendedState es = null;
				try {
					es = en.getExtendedState(dp.getConnObjectId());
				}
				catch (ExtendedStateNotFoundException ex){
					// Ignore this error and use user answer as value instead
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
			// Hard observation with specific value
			// Not linked to a specific state
			JSONObject jsonEntry = new JSONObject();
			String observationAnswer = logicObservation.getUserEnteredAnswer();
			
			jsonEntry.put(com.agenarisk.api.model.Observation.Field.value.toString(), observationAnswer);
			jsonEntry.put(com.agenarisk.api.model.Observation.Field.weight.toString(), 1);
			jsonEntries.put(jsonEntry);
		}
		
		jsonObservation.put(com.agenarisk.api.model.Observation.Field.entries.toString(), jsonEntries);
		return jsonObservation;
	}
}
