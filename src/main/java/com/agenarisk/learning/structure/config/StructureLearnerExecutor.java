package com.agenarisk.learning.structure.config;

import BNlearning.Database;
import BNlearning.structureLearning;
import com.agenarisk.learning.structure.config.Config;
import com.agenarisk.learning.structure.exception.StructureLearningException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 *
 * @author Eugene Dementiev
 */
public class StructureLearnerExecutor extends Configurer<StructureLearnerExecutor> implements Executable {
	
	protected StructureLearnerExecutor(Config config) {
		super(config);
		init();
	}
	
	protected StructureLearnerExecutor() {
		super();
		init();
	}
	
	private void init() throws StructureLearningException {
		config.setLearningEnabled(true);
		config.setGenerateModelAgenarisk(true);
//		String fileOutputCmp = "model.cmp";
//		try {
//			Path tempOutputFile = Files.createTempFile(Paths.get(uk.co.agena.minerva.util.Config.getDirectoryTempAgenaRisk()), null, ".cmp");
//			tempOutputFile.toFile().deleteOnExit();
//			fileOutputCmp = tempOutputFile.getFileName().toString();
//		}
//		catch (IOException ex){
//			throw new StructureLearningException("Failed to create a temp file for output", ex);
//		}
//		config.setFileOutputCmp(fileOutputCmp);
	}
	
	/**
	 * If set, the file at provided path will not be deleted upon exit and can be used for debugging
	 * @param path to be resolved from config.getPathOutput()
	 * @return this Executor
	 */
	public StructureLearnerExecutor setFileOutputCmp(String path){
		config.setFileOutputCmp(path);
		return this; 
	}
	
	/**
	 * If set, will override any previous data passed as JSON
	 * @param path to be resolved from config.getPathInput()
	 * @return this Executor
	 */
	public StructureLearnerExecutor setFileInputTrainingDataCsv(String path){
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
			structureLearning.initialiseProcess(true, false, config.getGenerateModelAgenarisk() || config.getGenerateModelGenie(), false);
		}
		catch (Exception ex){
			throw new StructureLearningException("Structure learning failed", ex);
		}
	}

}
