package com.agenarisk.api.model;

import com.agenarisk.api.exception.StateException;
import uk.co.agena.minerva.model.extendedbn.ContinuousIntervalEN;
import uk.co.agena.minerva.model.extendedbn.ExtendedNode;
import uk.co.agena.minerva.model.extendedbn.ExtendedState;
import uk.co.agena.minerva.model.extendedbn.IntegerIntervalEN;
import uk.co.agena.minerva.util.model.MinervaRangeException;
import uk.co.agena.minerva.util.model.NameDescription;
import uk.co.agena.minerva.util.model.Range;

/**
 * Node class corresponds to a State in AgenaRisk Desktop or ExtendedState in AgenaRisk Java API v1.
 * 
 * @author Eugene Dementiev
 */
public class State {
	
	/**
	 * The State's Node
	 */
	private final Node node;
	
	/**
	 * Corresponding logic state
	 */
	private final ExtendedState logicState;
	
	/**
	 * Constructor for State class.
	 * <br>
	 * If Node's underlying logic node is a numeric interval, then will try to parse label as a number or a range.
	 * <br>
	 * Will not create an underlying logic state if one already exists with this label.
	 * 
	 * @param node State's Node
	 * @param label State's label (or string representation of corresponding range in format :lower - upper)
	 * 
	 * @throws StateException range's upper bound is smaller than its lower bound
	 */
	private State(Node node, String label) throws StateException{
		this.node = node;
		
		ExtendedNode en = node.getLogicNode();
		ExtendedState es = en.getExtendedStateWithName(label);
		if (es != null){
			logicState = es;
		}
		else {
			logicState = new ExtendedState();
			
			logicState.setName(new NameDescription(label, label));
			
			if (en instanceof ContinuousIntervalEN || en instanceof IntegerIntervalEN) {
				
				boolean rangeState = false;
				String[] rangeParts = null;

				if(label.contains(" - ")){
					rangeState = true;
					rangeParts = label.split(" - ");
				}

				if (rangeState){
					try {
						Range r = new Range(Double.valueOf(rangeParts[0]), Double.valueOf(rangeParts[1]));
						logicState.setRange(r);
						logicState.setNumericalValue(r.midPoint());
					}
					catch (MinervaRangeException ex){
						throw new StateException("State represents an invalid range", ex);
					}
				}
				else {
					logicState.setNumericalValue(Double.valueOf(label));
				}
			}
			
			en.addExtendedState(logicState, true);

		}
	}
	
	/**
	 * Creates a State object for the given Node with the given Label.
	 * 
	 * @param node State's Node
	 * @param label State's label (or string representation of corresponding range in format :lower - upper)
	 * 
	 * @return State instance
	 * @throws StateException if numeric values in the label could not be parsed or range's upper bound is smaller than its lower bound
	 */
	protected static State createState(Node node, String label) throws StateException{
		State state;
		try {
			state = new State(node, label);
		}
		catch (NumberFormatException ex){
			throw new StateException("Can't parse numeric state values", ex);
		}
		
		return state;
	}

	/**
	 * Gets the state's label.
	 * 
	 * @return state's label
	 */
	public String getLabel() {
		return logicState.getName().getShortDescription();
	}

	/**
	 * Gets the state's Node.
	 * 
	 * @return state's Node
	 */
	protected Node getNode() {
		return node;
	}

	/**
	 * Gets the underlying logic state.
	 * 
	 * @return underlying logic state
	 */
	protected ExtendedState getLogicState() {
		return logicState;
	}

}
