package com.agenarisk.learning.structure.config;

import com.agenarisk.api.model.Model;
import com.agenarisk.api.model.Network;
import com.agenarisk.api.model.Node;
import com.agenarisk.api.util.CsvReader;
import com.agenarisk.api.util.TempFileCleanup;
import com.agenarisk.learning.structure.exception.StructureLearningException;
import com.agenarisk.learning.structure.logger.BLogger;
import java.util.ArrayList;
import java.util.List;
import uk.co.agena.minerva.util.EM.Data;
import uk.co.agena.minerva.util.EM.EMCal;
import uk.co.agena.minerva.util.EM.EMLogger;

/**
 *
 * @author Eugene Dementiev
 */
public class TableLearningExecutor extends Configurer<TableLearningExecutor> implements Executable {
	
	private TableLearningConfigurer originalConfigurer;
	
	protected TableLearningExecutor(Config config) {
		super(config);
	}
	
	protected TableLearningExecutor() {
		super();
	}

	public void setOriginalConfigurer(TableLearningConfigurer originalConfigurer) {
		this.originalConfigurer = originalConfigurer;
	}
	
	@Override
	public void execute() throws StructureLearningException {
		try {
			if (originalConfigurer == null){
				BLogger.logConditional("Original configurer not set");
				return;
			}
			
			Model model = originalConfigurer.getModel();
			Data data = new Data(originalConfigurer.getDataPath().toString(), originalConfigurer.getMissingValue(), originalConfigurer.getValueSeparator());
			uk.co.agena.minerva.model.Model.EM_ON = true;
			Network network = model.getNetworkList().get(0);
			network.getLogicNetwork().setConfidence(1-originalConfigurer.getDataWeight());
			ArrayList<String> skipNodes = new ArrayList<>();
			originalConfigurer.getNodeDataWeightsCustom().forEach((nodeId, weight) -> {
				try {
					network.getNode(nodeId).getLogicNode().setConfidence(1-weight);
					if (weight == 0){
						skipNodes.add(nodeId);
					}
				}
				catch (NullPointerException ex){
					BLogger.logConditional("Node " + nodeId + " not found, ignoring when trying to set data weight");
				}
			});
			
//			model.getLogicModel().setEMLogging(true);
			model.getNetworkList().get(0).getLogicNetwork().reinitialise(false);
			
			EMCal emcal = new EMCal(model.getLogicModel(),
					model.getNetworkList().get(0).getLogicNetwork(),
					data,
					originalConfigurer.getMissingValue(),
					originalConfigurer.getModelPrefix()+".cmp",
					skipNodes,
					true);
			emcal.setMaxIterations(originalConfigurer.getMaxIterations());
			emcal.threshold = originalConfigurer.getConvergenceThreshold();

			emcal.calculateEM();

			model.save(originalConfigurer.getModelPath().toString());
			
			originalConfigurer.setModel(model);
		}
		catch (Exception ex){
			throw new StructureLearningException(ex.getMessage(), ex);
		}
	}

}
