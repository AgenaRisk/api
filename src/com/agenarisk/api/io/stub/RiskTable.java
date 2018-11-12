package com.agenarisk.api.io.stub;

/**
 * This is a stub class that only contains field values for input/output to XML and JSON format.
 * 
 * @author Eugene Dementiev
 */
public class RiskTable {

	/**
	 * This is set of fields for input/output to XML and JSON format
	 */
	public static enum Field {
		riskTable,
		questionnaire
	}
	
	/**
	 * This is set of fields for input/output to XML and JSON format
	 */
	public static enum Questionnaire {
		name,
		description
	}
	
	/**
	 * This is set of fields for input/output to XML and JSON format
	 */
	public static enum Question {
		questions,
		question,
		name,
		description,
		network,
		node,
		type,
		mode,
		visible,
		syncName
	}
	
	/**
	 * This is set of fields for input/output to XML and JSON format
	 */
	public static enum QuestionType {
		observation,
		constant
	}
	
	/**
	 * This is set of fields for input/output to XML and JSON format
	 */
	public static enum QuestionMode {
		numerical,
		selection,
		unanswerable
	}
	
	/**
	 * This is set of fields for input/output to XML and JSON format
	 */
	public static enum Answer {
		answers,
		answer,
		dataSet,
		value
	}
}
