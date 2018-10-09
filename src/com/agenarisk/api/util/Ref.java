package com.agenarisk.api.util;

/**
 * This class is a reference dictionary for XML, JSON and relevant values for objects in AgenaRisk API v2
 * The notes below map these terms with the corresponding features of AgenaRisk Desktop
 * 
 * @author Eugene Dementiev
 */
public final class Ref {
	
	
	
	/**
	 * Represents uk.​co.​agena.​minerva.​model.Model
	 */
	public static final String MODEL = "model";
	
	/**
	 * Represents uk.​co.​agena.​minerva.​model.Model.extendedBnList.extendedBns
	 */
	public static final String NETWORKS = "networks";
	
	/**
	 * Represents uk.​co.​agena.​minerva.​model.ExtendedBN
	 */
	public static final String NETWORK = "network";
	
	/**
	 * Represents uk.​co.​agena.​minerva.​model.ExtendedBN.extendedNodes
	 */
	public static final String NODES = "nodes";
	
	/**
	 * Represents uk.co.agena.minerva.model.extendedbn.ExtendedNode
	 */
	public static final String NODE = "node";
	
	/**
	 * Represents either uk.​co.​agena.​minerva.​model.ExtendedBN.connID
	 * or uk.co.agena.minerva.model.extendedbn.ExtendedNode.connNodeId
	 */
	public static final String ID = "id";
	
	/**
	 * Represents uk.co.agena.minerva.util.model.NameDescription.shortDescription
	 */
	public static final String NAME = "name";
	public static final String CONFIGURATION = "configuration";
	public static final String SIMULATED = "simulated";
	
	public static final String TYPE = "type";
	public static enum NODE_TYPE {
		Boolean,
		Labelled,
		Ranked,
		DiscreteReal,
		ContinuousInterval,
		IntegerInterval
	}
	
	public static enum GRAPHICS_SHAPE {
		Ellipse,
		Rectangle,
		RoundedRectangle
	}
	
	public static enum GRAPHICS_LINE_STYLE {
		Solid,
		Dashed
	}
	
	public static enum GRAPHICS_TEXT_ALIGNV {
		Top,
		Center,
		Bottom
	}
	
	public static enum GRAPHICS_TEXT_ALIGNH {
		Left,
		Center,
		Right
	}
	
	public static enum NODE_GRAPH_TYPE {
		Line,
		Bar,
		Area,
		ScatterPlot,
		Histogram
	}
	
	public static enum NODE_GRAPH_PLOT_TYPE {
		ProbabilityDistribution,
		CumulativeDistribution
	}
	
	public static final String NODE_INPUT = "input";
	public static final String NODE_OUTPUT = "output";
	
	public static final String EXPRESSIONS = "expressions";
	public static final String EXPRESSION = "expression";
	
	public static final String LINKS = "links";
	public static final String LINK = "link";
	
	public static enum LINK_TYPE {
		Marginals,
		Mean,
		Median,
		Variance,
		StandardDeviation,
		LowerPercentile,
		UpperPercentile,
		State
	}
	
	public static final String LINK_SOURCE_NETWORK = "sourceNetwork";
	public static final String LINK_TARGET_NETWORK = "targetNetwork";
	public static final String LINK_SOURCE_NODE = "sourceNode";
	public static final String LINK_TARGET_NODE = "targetNode";
	public static final String LINK_PASS_STATE = "passState";
	
	public static final String PARENT = "parent";
	public static final String CHILD = "child";
	
	public static final String TABLE = "table";
	public static enum TABLE_TYPE {
		Manual,
		Expression,
		Partitioned
	}
	
	public static final String PROBABILITIES = "probabilities";
	public static final String ROW = "row";
	public static final String CELL = "cell";
	
	public static final String PARTITIONS = "partitions";
	public static final String PARTITION = "partition";
	
	public static final String STATES = "states";
	public static final String STATE = "state";
	
	public static final String META = "meta";
	public static final String NOTES = "notes";
	public static final String NOTE = "note";
	public static final String NOTE_NAME = "name";
	public static final String NOTE_TEXT = "text";
	
	public static final String GRAPHICS = "graphics";
	
	public static final String RISK_TABLE = "riskTable";
	public static final String RISK_TABLE_ENTRY = "riskTableEntry";
	
	public static final String TEXTS = "texts";
	public static final String TEXT_ENTRY = "text";
	public static final String TEXT_CONTENT = "content";
	
	public static final String PICTURES = "pictures";
	public static final String PICTURE = "picture";
	public static final String PICTURE_DATA = "data";
	
	public static final String MODEL_AUDIT = "audit";
	public static final String MODEL_SETTINGS = "settings";
}
