package com.agenarisk.learning.structure.config;

import com.agenarisk.api.model.Model;
import com.agenarisk.api.model.Network;
import com.agenarisk.learning.structure.exception.StructureLearningException;
import com.agenarisk.learning.structure.execution.graph.node.GraphNode;
import com.agenarisk.learning.structure.logger.BLogger;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import uk.co.agena.minerva.util.EM.Data;
import uk.co.agena.minerva.util.EM.EMCal;

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
			
			uk.co.agena.minerva.model.Model.EM_ON = true;
			EMCal emcal = new EMCal(model.getLogicModel(),
					model.getNetworkList().get(0).getLogicNetwork(),
					data,
					originalConfigurer.getMissingValue(),
					originalConfigurer.getModelPrefix()+".cmp",
					skipNodes,
					true);
			emcal.setMaxIterations(originalConfigurer.getMaxIterations());
			emcal.threshold = originalConfigurer.getConvergenceThreshold();

			runWithProgressPolling(emcal);

			byte[] bytes = model.export(Model.ExportFlag.KEEP_META, Model.ExportFlag.KEEP_OBSERVATIONS, Model.ExportFlag.KEEP_RESULTS).toString().getBytes();
			Files.write(originalConfigurer.getModelPath(), bytes, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
			
			originalConfigurer.setModel(model);
			uk.co.agena.minerva.model.Model.EM_ON = false;
		}
		catch (Exception ex){
			uk.co.agena.minerva.model.Model.EM_ON = false;
			throw new StructureLearningException(ex.getMessage(), ex);
		}
	}

	/**
	 * Runs the EM calculation, polling {@code emcal}'s existing (already-implemented, but until now never read by
	 * anything in the CLI/headless path) {@code Progressable} counters from a background thread and emitting
	 * periodic progress - EM has no per-iteration hook of its own to call out to, so this is the only way to get
	 * live feedback without modifying the core module's EM implementation itself.
	 */
	private void runWithProgressPolling(EMCal emcal) throws Exception {
		if (!originalConfigurer.isProgressEnabled()){
			emcal.calculateEM();
			return;
		}

		Thread pollThread = new Thread(() -> {
			try {
				while (!Thread.currentThread().isInterrupted()){
					Thread.sleep(1000);
					int total = emcal.getLengthOfProgressableTask();
					int current = emcal.getCurrentProgress();
					String message = total > 0
							? "Learning probabilities (EM) - " + Math.min(100, current * 100 / total) + "% complete"
							: "Learning probabilities (EM)...";
					GraphNode.emitProgress(originalConfigurer.getNodeLabel(), message, total > 0 ? current : null, total > 0 ? total : null);
				}
			}
			catch (InterruptedException ex){
				// Expected on stop - the EM calculation finished (or failed) before the next poll tick.
			}
		});
		pollThread.setDaemon(true);
		pollThread.start();
		try {
			emcal.calculateEM();
		}
		finally {
			pollThread.interrupt();
		}
	}

}
