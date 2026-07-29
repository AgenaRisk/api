package com.agenarisk.learning.structure.execution.graph.node;

import com.agenarisk.learning.structure.execution.graph.GraphExecutionContext;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;

public abstract class ModelNode extends GraphNode {

	public Path getModelPath(GraphExecutionContext ctx) {
		return ctx.getOutputDirPath().resolve(getLabel() + ".cmpx");
	}

	@Override
	public List<Path> getOutputFiles(GraphExecutionContext ctx) {
		return Collections.singletonList(getModelPath(ctx));
	}
}
