package com.agenarisk.api.model;

import com.agenarisk.api.exception.NodeException;

/**
 *
 * @author Eugene Dementiev
 */
public class Variable {
	
	private final Node node;
	private final uk.co.agena.minerva.util.model.Variable logicVariable;
	
	/**
	 * Creates an instance of Variable for the provided Node, linked to the provided logic Variable.
	 * 
	 * @param node Node for the Variable
	 * @param logicVariable Logic variable that this Variable represents
	 */
	protected Variable(Node node, uk.co.agena.minerva.util.model.Variable logicVariable){
		this.node = node;
		this.logicVariable = logicVariable;
	}
	
	/**
	 * Get the name of this Variable.
	 * 
	 * @return name of this Variable
	 */
	public String getName(){
		return logicVariable.getName();
	}
	
	/**
	 * Change the name of the Variable to the provided new name.<br>
	 * Will also update formulas, expressions etc.
	 * 
	 * @param newName new Variable name
	 */
	public void setName(String newName){
		try {
			node.getLogicNode().updateExpressionVariable(logicVariable, newName, logicVariable.getDefaultValue());
		}
		catch(Exception ex){
			// Should not have happened,as we must have validated before this point
			throw new NodeException("Failed to update Node Variable", ex);
		}
	}
	
	/**
	 * Get the value of this Variable.
	 * @return value of this Variable
	 */
	public double getValue(){
		return logicVariable.getDefaultValue();
	}
	
	/**
	 * Change the default value of the Variable to the provided new value.
	 * 
	 * @param newValue new default value
	 */
	public void setValue(double newValue){
		try {
			node.getLogicNode().updateExpressionVariable(logicVariable, logicVariable.getName(), newValue);
		}
		catch(Exception ex){
			// Should not have happened,as we must have validated before this point
			throw new NodeException("Failed to update Node Variable", ex);
		}
	}
}
