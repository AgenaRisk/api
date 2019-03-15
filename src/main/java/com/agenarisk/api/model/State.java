package com.agenarisk.api.model;

import com.agenarisk.api.exception.AgenaRiskRuntimeException;
import com.agenarisk.api.exception.StateException;
import uk.co.agena.minerva.model.extendedbn.ContinuousIntervalEN;
import uk.co.agena.minerva.model.extendedbn.DiscreteRealEN;
import uk.co.agena.minerva.model.extendedbn.ExtendedNode;
import uk.co.agena.minerva.model.extendedbn.ExtendedState;
import uk.co.agena.minerva.model.extendedbn.IntegerIntervalEN;
import uk.co.agena.minerva.model.extendedbn.LabelledEN;
import uk.co.agena.minerva.model.extendedbn.RankedEN;
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
	 * This is set of fields for input/output to XML and JSON format
	 */
	public static enum Field {
		states,
		state
	}
	
	/**
	 * The State's Node
	 */
	private final Node node;
	
	/**
	 * Corresponding logic state
	 */
	private ExtendedState logicState;
	
	/**
	 * Constructor for State class.
	 * <br>
	 * Does not create underlying logic state.
	 * 
	 * @param node State's Node
	 * @param label State's label (or string representation of corresponding range in format :lower - upper)
	 */
	private State(Node node, ExtendedState logicState) {
		this.node = node;
		this.logicState = logicState;
	}
	
	/**
	 * Creates a State instance for the given Node with the given Label.
	 * <br>
	 * Will not create an underlying logic state if one already exists with this label for the associated logic node.
	 * 
	 * @param node State's Node
	 * @param label State's label (or string representation of corresponding range in format :lower - upper)
	 * 
	 * @return State instance
	 * @throws StateException if numeric values in the label could not be parsed or range's upper bound is smaller than its lower bound
	 */
	protected static State createState(Node node, String label) throws StateException {
		
		ExtendedNode en = node.getLogicNode();
		
		ExtendedState es = en.getExtendedStateWithName(label);
		if (es == null) {
			try {
				es = createLogicState(en, label);
			}
			catch (NumberFormatException ex){
				throw new StateException("Can't parse numeric state values", ex);
			}
		}
		
		State state = new State(node, es);
		
		return state;
	}
	
	/**
	 * Creates a logic state ExtendedState for the given logic node ExtendedNode with given label
	 * <br>
	 * If logic node is a numeric interval, then will try to parse label as a number or a range.
	 * 
	 * @param en State's Node
	 * @param label State's label (or string representation of corresponding range in format :lower - upper)
	 * 
	 * @return created ExtendedState instance
	 * @throws StateException if range's upper bound is smaller than its lower bound
	 */
	private static ExtendedState createLogicState(ExtendedNode en, String label) throws StateException {
		ExtendedState es = new ExtendedState();
			
		es.setName(new NameDescription(label, label));

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
					es.setRange(r);
					es.setNumericalValue(r.midPoint());
				}
				catch (MinervaRangeException ex){
					throw new StateException("State represents an invalid range", ex);
				}
			}
			else {
				es.setNumericalValue(Double.valueOf(label));
			}
		}

		en.addExtendedState(es, true);
		
		return es;
	}
	
	/**
	 * Find a state by given label in the given Node's underlying logic node.
	 * 
	 * @param node Node to get the state from
	 * @param label label of the required state
	 * 
	 * @return state with given label or null if such state does not exist
	 */
	protected static State getState(Node node, String label) {
		throw new UnsupportedOperationException("Not implemented");
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
	public Node getNode() {
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
	
	/**
	 * Computes a label for a logical state.
	 * <br/>
	 * Returns a range if the node is not Ranked and the state has an associated range. The range is in format: <b>lower - upper</b>.
	 * <br>
	 * Otherwise returns state short name.
	 * 
	 * @param en node containing the state
	 * @param es logical state
	 * 
	 * @return computed node label
	 */
	public static String computeLabel(ExtendedNode en, ExtendedState es){
		String label;
		if (en instanceof RankedEN || en instanceof LabelledEN || en instanceof DiscreteRealEN || es.getRange() == null){
			label = es.getName().getShortDescription();
		}
		else {
			label = es.getRange().getLowerBound() + " - " + es.getRange().getUpperBound();
		}

		return label;
	}

	/**
	 * Links this State to an underlying Minerva State object. Should only be used while wrapping a new Model around the Minerva Model.
	 * 
	 * @param logicState the logical state
	 */
	protected void setLogicState(ExtendedState logicState) {
		ExtendedNode en = getNode().getLogicNode();
		String labelThis = computeLabel(en, getLogicState());
		String labelThat = computeLabel(en, logicState);
		
		if (!labelThis.equals(labelThat)){
			throw new AgenaRiskRuntimeException("Logic state mismatch: " + labelThis + "," + labelThat);
		}
		
		this.logicState = logicState;
	}

}
