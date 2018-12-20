package com.agenarisk.api.io.stub;

/**
 * This is a stub class that only contains field values for input/output to XML and JSON format.
 * 
 * @author Eugene Dementiev
 */
public class NodeConfiguration {
	
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
