package com.agenarisk.api.model;

import com.agenarisk.api.exception.ObservationException;
import org.json.JSONObject;

/**
 * VariableObservation class is a view of a node variable observation, valid at the time of resolution and not maintained.
 * 
 * @author Eugene Dementiev
 */
public class VariableObservation extends Observation {
	
	private final String variableName;

	/**
	 * Constructor for VariableObservation.
	 * 
	 * @param logicObservation Logic observation associated with this VariableObservation
	 * @param dataSet DataSet that contains this VariableObservation
	 * @param node observed Node
	 * 
	 * @throws ObservationException entries are empty or not provided or if the logicObservation is not for a variable
	 */
	protected VariableObservation(uk.co.agena.minerva.model.scenario.Observation logicObservation, DataSet dataSet, Node node) throws ObservationException {
		super(logicObservation, dataSet, node);
		variableName = logicObservation.getExpressionVariableName();
		if (variableName == null || variableName.isEmpty()){
			throw new ObservationException("Observation is not a VariableObservation");
		}
	}

	/**
	 * Returns the name of the Variable associated with this Observation.
	 * 
	 * @return variable name
	 */
	public String getVariableName() {
		return variableName;
	}
	
	/**
	 * Creates a JSONObject representation of this Observation.
	 * 
	 * @return JSONObject equivalent of this Observation
	 */
	@Override
	public JSONObject toJson(){
		JSONObject json = super.toJson();
		json.put(Observation.Field.constantName.toString(), getVariableName());
		return json;
	}

}
