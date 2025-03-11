package com.agenarisk.learning.structure.config;

import BNlearning.Database;
import BNlearning.generateModelAveGraph;
import com.agenarisk.learning.structure.exception.StructureLearningException;

/**
 *
 * @author Eugene Dementiev
 */
public class AveragingExecutor extends Configurer<AveragingExecutor> implements Executable {
	
	protected AveragingExecutor(Config config) {
		super(config);
	}
	
	protected AveragingExecutor() {
		super();
	}
	
	@Override
	public void execute() throws StructureLearningException {
		try {
			Database.modelAveragingGraphsDataFilepath = Config.getInstance().getPathInput().resolve("modelAveragingGraphs.csv");
			generateModelAveGraph.initialiseProcess();
		}
		catch (Exception ex){
			ex.printStackTrace(System.out);
			throw new StructureLearningException(ex.getMessage(), ex);
		}
	}

}
