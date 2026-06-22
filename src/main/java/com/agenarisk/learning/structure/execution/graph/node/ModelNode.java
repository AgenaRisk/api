package com.agenarisk.learning.structure.execution.graph.node;

import com.agenarisk.learning.structure.execution.graph.GraphExecutionContext;
import java.nio.file.Path;

public abstract class ModelNode extends GraphNode {

	public Path getModelPath(GraphExecutionContext ctx) {
		return ctx.getOutputDirPath().resolve(getLabel() + ".cmpx");
	}
}
