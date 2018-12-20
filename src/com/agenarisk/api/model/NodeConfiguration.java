package com.agenarisk.api.model;

import com.agenarisk.api.exception.AgenaRiskRuntimeException;
import com.agenarisk.api.model.Node;
import org.apache.sling.commons.json.JSONArray;
import org.apache.sling.commons.json.JSONException;
import uk.co.agena.minerva.model.extendedbn.BooleanEN;
import uk.co.agena.minerva.model.extendedbn.ContinuousEN;
import uk.co.agena.minerva.model.extendedbn.ContinuousIntervalEN;
import uk.co.agena.minerva.model.extendedbn.DiscreteRealEN;
import uk.co.agena.minerva.model.extendedbn.ExtendedBNException;
import uk.co.agena.minerva.model.extendedbn.ExtendedNode;
import uk.co.agena.minerva.model.extendedbn.IntegerIntervalEN;
import uk.co.agena.minerva.model.extendedbn.LabelledEN;
import uk.co.agena.minerva.model.extendedbn.RankedEN;
import uk.co.agena.minerva.util.model.DataSet;
import uk.co.agena.minerva.util.model.MinervaRangeException;

/**
 * This is a stub class that only contains field values for input/output to XML and JSON format.
 * 
 * @author Eugene Dementiev
 */
public class NodeConfiguration {

	/**
	 * Resolves the Node Type based on the logical node.
	 *
	 * @param en logical node to resolve Type from
	 *
	 * @return resolved Node type
	 */
	public static Node.Type resolveNodeType(ExtendedNode en) {
		if (en instanceof BooleanEN) {
			return Node.Type.Boolean;
		}
		if (en instanceof LabelledEN) {
			return Node.Type.Labelled;
		}
		if (en instanceof RankedEN) {
			return Node.Type.Ranked;
		}
		if (en instanceof DiscreteRealEN) {
			return Node.Type.DiscreteReal;
		}
		if (en instanceof ContinuousIntervalEN) {
			return Node.Type.ContinuousInterval;
		}
		if (en instanceof IntegerIntervalEN) {
			return Node.Type.IntegerInterval;
		}
		throw new AgenaRiskRuntimeException("Invalid node type");
	}

	/**
	 * Gets fully-qualified name for ExtendedNode concrete implementation matching the provided Node type from Note.Type.
	 *
	 * @param type Node type
	 *
	 * @return fully-qualified ExtendedNode subclass name
	 */
	public static String resolveNodeClassName(Node.Type type) {
		String nodeClassName;
		switch (type) {
			case Boolean:
				nodeClassName = BooleanEN.class.getName();
				break;
			case Labelled:
				nodeClassName = LabelledEN.class.getName();
				break;
			case Ranked:
				nodeClassName = RankedEN.class.getName();
				break;
			case DiscreteReal:
				nodeClassName = DiscreteRealEN.class.getName();
				break;
			case ContinuousInterval:
				nodeClassName = ContinuousIntervalEN.class.getName();
				break;
			case IntegerInterval:
				nodeClassName = IntegerIntervalEN.class.getName();
				break;
			default:
				throw new AgenaRiskRuntimeException("Invalid node type provided");
		}
		return nodeClassName;
	}

	/**
	 * NPT in JSON is given by rows, while ExtendedNode expects an array of columns, so we will need to invert it.
	 *
	 * @param jsonNPT
	 *
	 * @return 2D array where first dimension are the columns and second dimension are the cells
	 *
	 * @throws JSONException
	 */
	protected static double[][] extractNPTColumns(JSONArray jsonNPT) throws JSONException {
		int rows = jsonNPT.length();
		int cols = jsonNPT.getJSONArray(0).length();
		double[][] npt = new double[cols][rows];
		for (int r = 0; r < jsonNPT.length(); r++) {
			JSONArray jsonCells = jsonNPT.getJSONArray(r);
			for (int c = 0; c < jsonCells.length(); c++) {
				double cell = jsonCells.getDouble(c);
				npt[c][r] = cell;
			}
		}
		return npt;
	}

	/**
	 * Replaces Node states with 3 default interval states.
	 *
	 * @param node
	 */
	protected static void setDefaultIntervalStates(Node node) {
		ContinuousEN cien = (ContinuousEN) node.getLogicNode();
		DataSet cids = new DataSet();
		cids.addIntervalDataPoint(Double.NEGATIVE_INFINITY, -1);
		cids.addIntervalDataPoint(-1, 1);
		cids.addIntervalDataPoint(1, Double.POSITIVE_INFINITY);
		try {
			cien.removeExtendedStates(0, cien.getExtendedStates().size() - 1, true);
			cien.createExtendedStates(cids);
		}
		catch (ExtendedBNException | MinervaRangeException ex) {
			throw new AgenaRiskRuntimeException("Failed to initialise interval states for node " + node.toStringExtra(), ex);
		}
	}
	
	/**
	 * This is set of fields for input/output to XML and JSON format
	 */
	public static enum Field {
		configuration,
		type,
		simulated,
		simulationConvergence,
		input,
		output
	}
	
	/**
	 * This is set of fields for input/output to XML and JSON format
	 */
	public static enum Table {
		table,
		type,
		
		partitions,
		partition,
		
		expressions,
		expression,
		
		probabilities,
		pvalues,
		row,
		column,
		cell,
		
		nptCompiled
	}
	
	/**
	 * This is set of fields for input/output to XML and JSON format
	 */
	public static enum TableType {
		Manual,
		Expression,
		Partitioned
	}
	
	/**
	 * This is set of fields for input/output to XML and JSON format
	 */
	public static enum Variables {
		variables,
		variable,
		name,
		value
	}
	
	
	/**
	 *
	 * @param matrix
	 * @return
	 */
	public static double[][] transposeMatrix(double[][] matrix) {
		double[][] matrixTransposed = new double[matrix[0].length][matrix.length];
		for (int i = 0; i < matrix.length; i++) {
			for (int j = 0; j < matrix[i].length; j++) {
				matrixTransposed[j][i] = matrix[i][j];
			}
		}
		return matrixTransposed;
	}
}
