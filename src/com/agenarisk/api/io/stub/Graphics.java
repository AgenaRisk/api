package com.agenarisk.api.io.stub;

/**
 * This is a stub class that only contains field values for input/output to XML and JSON format.
 * 
 * @author Eugene Dementiev
 */
public class Graphics {
	
	/**
	 * This is set of fields for input/output to XML and JSON format
	 */
	public static enum Field {
		graphics,
		
	}
	
	/**
	 * This is set of fields for input/output to XML and JSON format
	 */
	public static enum ModelGraphicsFormatting {
		formatting,
		decimalPlaces,
		xAxisAsPercentages,
		minimumProbabilityDisplayed
	}
	
	/**
	 * This is set of fields for input/output to XML and JSON format
	 */
	public static enum ModelGraphicsPaneSettings {
		paneSettings,
		leftPaneExpanded,
		rightPaneExpanded,
		selectedRiskObject
	}
	
	/**
	 * This is set of fields for input/output to XML and JSON format
	 */
	public static enum ModelGraphics {
		viewSettings,
		objectDefaults,
		openMonitors
	}
	
}
