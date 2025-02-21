package com.agenarisk.learning.structure.config;

import BNlearning.Database;
import BNlearning.structureLearning;
import com.agenarisk.learning.structure.exception.StructureLearningException;

/**
 *
 * @author Eugene Dementiev
 */
public class EvaluationExecutor extends Configurer<EvaluationExecutor> implements Executable {
	
	protected EvaluationExecutor(Config config) {
		super(config);
	}
	
	protected EvaluationExecutor() {
		super();
	}
	
	/**
	 * If set, will override any previous data passed as JSON
	 * @param path to be resolved from config.getPathOutput()
	 * @return this Executor
	 */
	public EvaluationExecutor setFileOutputCmp(String path){
		config.setFileOutputCmp(path);
		return this; 
	}
	
	/**
	 * If set, will override any previous data passed as JSON
	 * @param path to be resolved from config.getPathInput()
	 * @return this Executor
	 */
	public EvaluationExecutor setFileInputTrainingDataCsv(String path){
		config.setFileInputTrainingDataCsv(path);
		return this; 
	}

	@Override
	public void execute() throws StructureLearningException {
		Database.trainingDataFilepath = config.getPathInput().resolve(config.getFileInputTrainingDataCsv());
		Database.learnedDAGDataFilepath = config.getPathOutput().resolve(config.getFileOutputDagLearnedCsv());
		Database.temporalDataFilepath = config.getPathInput().resolve("constraintsTemporal.csv");
		Database.directedDataFilepath = config.getPathInput().resolve("constraintsDirected.csv");
		Database.undirectedDataFilepath = config.getPathInput().resolve("constraintsUndirected.csv");
		Database.independenceDataFilepath = config.getPathInput().resolve("constraintsForbidden.csv");
		Database.graphDataFilepath = config.getPathInput().resolve("constraintsGraph.csv");
		Database.targetDataFilepath = config.getPathInput().resolve("constraintsTarget.csv");
		Database.bdnDataFilepath = config.getPathInput().resolve("constraintsBDN.csv");
		Database.trueDAGDataFilepath = config.getPathInput().resolve("DAGtrue.csv");
//			updateMMDscoreSelections();                  
//
//			if(config.getLearningAlgorithm().equals(Config.LearningAlgorithm.SaiyanH))
//			{
//				config.setSaiyanHassociationalScore(discrepancyTypeSaiyanH.getSelectedItem().toString());
//				if(pruneCondIndep.isSelected())
//				{    config.setSaiyanHpruningCondIndep(true);    }
//				else {    config.setSaiyanHpruningCondIndep(false);    }
//				if(pruneFaithfulnessCond.isSelected())
//				{    config.setSaiyanHpruningAssocStrength(true);    }
//				else {    config.setSaiyanHpruningAssocStrength(false);    }
//				config.setSaiyanHMaxTabuEscapes(escapesTABU.getSelectedItem().toString());
//				config.setSaiyanHPruningAssocStrengthThreshold(Double.parseDouble(thresholdFaithfulness.getSelectedItem().toString()));
//			}
		try {
			structureLearning.initialiseProcess(false, true, false, false);
		}
		catch (Exception ex){
			ex.printStackTrace(System.out);
			throw new StructureLearningException(ex.getMessage(), ex);
		}
	}

}
