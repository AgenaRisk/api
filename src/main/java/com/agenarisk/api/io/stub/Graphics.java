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
		viewSettings,
		objectDefaults,
		openMonitors,
	}
	
	/**
	 * This is set of fields for input/output to XML and JSON format
	 */
	public static enum PaneSettings {
		paneSettings,
		leftPaneExpanded,
		rightPaneExpanded,
		scenarioPaneExpanded,
		selectedRiskObject
	}
	
	/**
	 * This is set of fields for input/output to XML and JSON format
	 */
	public static enum Images {
		images,
		image,
		filename,
		data
	}
	
	/**
	 * This is set of fields for input/output to XML and JSON format
	 */
	public static enum CanvasData {
		canvasData,
		canvas
	}
	
	/**
	 * This is set of fields for input/output to XML and JSON format
	 */
	public static enum WindowSettings {
		windowSettings,
		preferredScreen,
		screenId,
		size,
		width,
		height,
		preferredFrame,
		maximised,
		position,
		x,
		y
	}
}
