package com.agenarisk.api.util;

import com.agenarisk.api.exception.AgenaRiskRuntimeException;
import com.agenarisk.api.exception.ModelException;
import com.agenarisk.api.model.Model;
import com.agenarisk.api.model.Network;
import com.agenarisk.api.model.Node;
import com.singularsys.jep.JepException;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.stream.Collectors;
import uk.co.agena.minerva.model.corebn.CoreBN;
import uk.co.agena.minerva.model.corebn.CoreBNNode;
import uk.co.agena.minerva.model.corebn.CoreBNNodeList;
import uk.co.agena.minerva.model.extendedbn.ContinuousEN;
import uk.co.agena.minerva.model.extendedbn.ExtendedBN;
import uk.co.agena.minerva.model.extendedbn.ExtendedNode;
import uk.co.agena.minerva.model.extendedbn.ExtendedNodeFunction;
import uk.co.agena.minerva.model.extendedbn.ExtendedState;
import uk.co.agena.minerva.model.extendedbn.IntegerIntervalEN;
import uk.co.agena.minerva.util.model.Range;
import uk.co.agena.minerva.util.nptgenerator.ExpressionParser;

/**
 * Generates synthetic tabular data from an existing AgenaRisk model by forward (ancestral) sampling.
 * <br><br>
 * Nodes are visited in topological order (parents before children) and, for each generated row, each Node is sampled
 * conditional on the values already sampled for its parents. This reproduces the joint distribution encoded by the
 * model (its generative prior), independent of any evidence stored in the model's scenarios, and needs no per-row
 * inference so it scales to hundreds of thousands of rows.
 * <br><br>
 * Two sampling regimes are used, mirroring how the model defines each Node:
 * <ul>
 *  <li><b>Discrete / statically-discretised Nodes</b> (Boolean, Labelled, Ranked, DiscreteReal, and non-simulation
 *  interval nodes): the Node's state is drawn from its Node Probability Table (NPT) column selected by the parents'
 *  sampled states.</li>
 *  <li><b>Simulation Nodes</b> (continuous/integer interval nodes with dynamic discretisation): the Node's value is
 *  drawn in native (continuous) space directly from its statistical-distribution expression — e.g.
 *  {@code Normal(mean_expr, variance_expr)}, {@code TNormal(...)}, {@code Triangle(...)}, {@code Arithmetic(...)} —
 *  with the parameter expressions evaluated against the parents' already-sampled values. For a partitioned
 *  expression the partition is selected by the discrete parents' sampled states. This is true Monte Carlo forward
 *  simulation, so it neither depends on the model's (evidence-dependent) marginals nor collapses the joint the way
 *  sampling each Node from its own marginal would.</li>
 * </ul>
 * The output is a rectangular table in the layout expected by the structure-discovery and parameter-learning pipeline
 * in this codebase (see {@code com.agenarisk.learning.structure}):
 * <ul>
 *  <li>Row 0 is a header of Node <b>IDs</b>, one column per Node, in {@link Network#getNodeList()} order.</li>
 *  <li>Each subsequent row is one case. Discrete Nodes emit their sampled <b>state label</b>; simulation Nodes emit
 *  their sampled <b>numeric value</b> by default (ideal for continuous/regression learners and for re-binning
 *  downstream), or their containing state's label if {@link #setDiscretiseContinuous(boolean)} is enabled (for the
 *  discrete structure-discovery path, which treats each distinct value as a state).</li>
 * </ul>
 * The table can be written to CSV with {@link CsvWriter} (see {@link #writeCsv(List, Path)}). A second dataset with
 * missing values can be produced from the same complete sample via
 * {@link #generateWithMissing(Network, int, double, double, String)} or
 * {@link #applyMissingness(List, double, double, String)}, which use a two-stage model: each row is affected with a
 * given probability, and within an affected row each cell is blanked with a given probability.
 * <br><br>
 * <b>Supported simulation distributions:</b> Normal, TNormal, Uniform, Triangle, Beta, BetaPert, Gamma, Log Normal,
 * Exponential, Weibull, Logistic, Chi Squared, Student, Binomial, Negative Binomial, Poisson, Geometric and
 * Arithmetic. Comparative / MultinomialLogit and any other function are rejected with a clear exception rather than
 * producing incorrect data.
 *
 * @author Eugene Dementiev
 */
public class DataGenerator {

	/**
	 * Default token written into a cell that has been marked as missing (empty string, matching the EM parameter
	 * learner's default missing-value representation).
	 */
	public static final String DEFAULT_MISSING_TOKEN = "";

	/**
	 * Source of randomness for state sampling, variate sampling and missing-value placement.
	 */
	private final Random random;

	/**
	 * If true, each non-simulated, non-input Node's NPT is regenerated from its expressions before sampling, so that
	 * expression/partitioned tables are materialised. Regeneration is best-effort per Node; failures fall back to the
	 * NPT currently stored on the Node.
	 */
	private boolean regenerateTables = true;

	/**
	 * If true, simulation Nodes emit the label of the state their sampled value falls into (discretised output) rather
	 * than the raw numeric value. Off by default.
	 */
	private boolean discretiseContinuous = false;

	/**
	 * Number of decimal places used when formatting sampled continuous values as strings.
	 */
	private int continuousDecimalPlaces = 4;

	/**
	 * Creates a DataGenerator with a non-deterministic random source.
	 */
	public DataGenerator() {
		this.random = new Random();
	}

	/**
	 * Creates a DataGenerator with a fixed seed, for reproducible datasets.
	 *
	 * @param seed the random seed
	 */
	public DataGenerator(long seed) {
		this.random = new Random(seed);
	}

	/**
	 * Creates a DataGenerator backed by the provided Random.
	 *
	 * @param random the random source to use
	 */
	public DataGenerator(Random random) {
		this.random = Objects.requireNonNull(random, "random must not be null");
	}

	/**
	 * Controls whether NPTs are regenerated from Node expressions before sampling. Enabled by default.
	 *
	 * @param regenerateTables whether to regenerate NPTs before sampling
	 *
	 * @return this DataGenerator, for chaining
	 */
	public DataGenerator setRegenerateTables(boolean regenerateTables) {
		this.regenerateTables = regenerateTables;
		return this;
	}

	/**
	 * Controls whether simulation Nodes emit a discretised state label (true) or the raw numeric value (false, the
	 * default).
	 *
	 * @param discretiseContinuous whether to discretise continuous samples to state labels
	 *
	 * @return this DataGenerator, for chaining
	 */
	public DataGenerator setDiscretiseContinuous(boolean discretiseContinuous) {
		this.discretiseContinuous = discretiseContinuous;
		return this;
	}

	/**
	 * Sets the number of decimal places used when formatting sampled continuous values.
	 *
	 * @param continuousDecimalPlaces number of decimal places (must be non-negative)
	 *
	 * @return this DataGenerator, for chaining
	 */
	public DataGenerator setContinuousDecimalPlaces(int continuousDecimalPlaces) {
		if (continuousDecimalPlaces < 0) {
			throw new IllegalArgumentException("continuousDecimalPlaces must be non-negative");
		}
		this.continuousDecimalPlaces = continuousDecimalPlaces;
		return this;
	}

	/**
	 * Generates a complete dataset of the requested size from the first Network of the given Model.
	 *
	 * @param model the source Model
	 * @param rowCount number of cases (data rows) to generate
	 *
	 * @return table with a header row of Node IDs followed by {@code rowCount} rows
	 */
	public List<List<String>> generate(Model model, int rowCount) {
		return generate(firstNetwork(model), rowCount);
	}

	/**
	 * Generates a complete dataset of the requested size from the given Network.
	 *
	 * @param network the source Network
	 * @param rowCount number of cases (data rows) to generate
	 *
	 * @return table with a header row of Node IDs followed by {@code rowCount} rows
	 */
	public List<List<String>> generate(Network network, int rowCount) {
		if (rowCount < 0) {
			throw new IllegalArgumentException("rowCount must not be negative");
		}

		PreparedNetwork prepared = prepare(network);

		List<List<String>> table = new ArrayList<>(rowCount + 1);
		table.add(new ArrayList<>(prepared.header));
		for (int r = 0; r < rowCount; r++) {
			table.add(prepared.sampleRow());
		}
		return table;
	}

	/**
	 * Generates a dataset with missing values, using the default missing token, from the first Network of the given
	 * Model. Missingness is applied in two stages: each row is affected with probability
	 * {@code rowMissingProbability}, and within an affected row each cell is independently blanked with probability
	 * {@code cellMissingProbability}.
	 *
	 * @param model the source Model
	 * @param rowCount number of cases (data rows) to generate
	 * @param rowMissingProbability probability that a row has any missing values, in [0, 1]
	 * @param cellMissingProbability probability that a cell in an affected row is missing, in [0, 1]
	 *
	 * @return table with a header row of Node IDs followed by {@code rowCount} rows, some cells blanked
	 */
	public List<List<String>> generateWithMissing(Model model, int rowCount, double rowMissingProbability, double cellMissingProbability) {
		return generateWithMissing(firstNetwork(model), rowCount, rowMissingProbability, cellMissingProbability, DEFAULT_MISSING_TOKEN);
	}

	/**
	 * Generates a dataset with missing values (see {@link #applyMissingness(List, double, double, String)} for the
	 * two-stage model), from the first Network of the given Model.
	 *
	 * @param model the source Model
	 * @param rowCount number of cases (data rows) to generate
	 * @param rowMissingProbability probability that a row has any missing values, in [0, 1]
	 * @param cellMissingProbability probability that a cell in an affected row is missing, in [0, 1]
	 * @param missingToken the token to write into missing cells; if null, {@link #DEFAULT_MISSING_TOKEN} is used
	 *
	 * @return table with a header row of Node IDs followed by {@code rowCount} rows, some cells replaced
	 */
	public List<List<String>> generateWithMissing(Model model, int rowCount, double rowMissingProbability, double cellMissingProbability, String missingToken) {
		return generateWithMissing(firstNetwork(model), rowCount, rowMissingProbability, cellMissingProbability, missingToken);
	}

	/**
	 * Generates a dataset with missing values, using the default missing token (see
	 * {@link #applyMissingness(List, double, double, String)} for the two-stage model).
	 *
	 * @param network the source Network
	 * @param rowCount number of cases (data rows) to generate
	 * @param rowMissingProbability probability that a row has any missing values, in [0, 1]
	 * @param cellMissingProbability probability that a cell in an affected row is missing, in [0, 1]
	 *
	 * @return table with a header row of Node IDs followed by {@code rowCount} rows, some cells blanked
	 */
	public List<List<String>> generateWithMissing(Network network, int rowCount, double rowMissingProbability, double cellMissingProbability) {
		return generateWithMissing(network, rowCount, rowMissingProbability, cellMissingProbability, DEFAULT_MISSING_TOKEN);
	}

	/**
	 * Generates a dataset with missing values (see {@link #applyMissingness(List, double, double, String)} for the
	 * two-stage model).
	 *
	 * @param network the source Network
	 * @param rowCount number of cases (data rows) to generate
	 * @param rowMissingProbability probability that a row has any missing values, in [0, 1]
	 * @param cellMissingProbability probability that a cell in an affected row is missing, in [0, 1]
	 * @param missingToken the token to write into missing cells; if null, {@link #DEFAULT_MISSING_TOKEN} is used
	 *
	 * @return table with a header row of Node IDs followed by {@code rowCount} rows, some cells replaced
	 */
	public List<List<String>> generateWithMissing(Network network, int rowCount, double rowMissingProbability, double cellMissingProbability, String missingToken) {
		List<List<String>> table = generate(network, rowCount);
		applyMissingness(table, rowMissingProbability, cellMissingProbability, missingToken);
		return table;
	}

	/**
	 * Marks cells of an existing table as missing in place, using a two-stage model that mimics real-world incomplete
	 * records: each data row is independently "affected" with probability {@code rowMissingProbability}, and within an
	 * affected row each cell is independently replaced by {@code missingToken} with probability
	 * {@code cellMissingProbability}. Rows that are not affected are left complete. The header row (row 0) is never
	 * modified.
	 *
	 * @param table the table to modify in place; row 0 is treated as a header and left untouched
	 * @param rowMissingProbability probability that a row has any missing values, in [0, 1]
	 * @param cellMissingProbability probability that a cell in an affected row is missing, in [0, 1]
	 * @param missingToken the token to write into missing cells; if null, {@link #DEFAULT_MISSING_TOKEN} is used
	 */
	public void applyMissingness(List<List<String>> table, double rowMissingProbability, double cellMissingProbability, String missingToken) {
		if (rowMissingProbability < 0d || rowMissingProbability > 1d) {
			throw new IllegalArgumentException("rowMissingProbability must be in [0, 1], got " + rowMissingProbability);
		}
		if (cellMissingProbability < 0d || cellMissingProbability > 1d) {
			throw new IllegalArgumentException("cellMissingProbability must be in [0, 1], got " + cellMissingProbability);
		}
		String token = (missingToken == null) ? DEFAULT_MISSING_TOKEN : missingToken;

		if (rowMissingProbability == 0d || cellMissingProbability == 0d) {
			return;
		}

		// Skip row 0 (header)
		for (int r = 1; r < table.size(); r++) {
			if (random.nextDouble() >= rowMissingProbability) {
				// Row is not affected; leave it complete
				continue;
			}
			List<String> row = table.get(r);
			for (int c = 0; c < row.size(); c++) {
				if (random.nextDouble() < cellMissingProbability) {
					row.set(c, token);
				}
			}
		}
	}

	/**
	 * Convenience end-to-end helper: load a model file, generate a complete dataset and a dataset with missing values
	 * derived from the same cases, and write both to CSV.
	 *
	 * @param modelPath path to the model file (e.g. .cmpx, .ast, .cmp, .json)
	 * @param rowCount number of cases to generate
	 * @param rowMissingProbability probability that a row has any missing values, in [0, 1]
	 * @param cellMissingProbability probability that a cell in an affected row is missing, in [0, 1]
	 * @param completeCsv output path for the complete dataset
	 * @param missingCsv output path for the dataset with missing values
	 * @param seed random seed for reproducibility
	 *
	 * @throws ModelException if the model fails to load
	 * @throws IOException if writing either CSV fails
	 */
	public static void generateFromModelFile(Path modelPath, int rowCount, double rowMissingProbability, double cellMissingProbability, Path completeCsv, Path missingCsv, long seed) throws ModelException, IOException {
		Model model = Model.loadModel(modelPath.toString());
		DataGenerator generator = new DataGenerator(seed);
		Network network = generator.firstNetwork(model);

		List<List<String>> complete = generator.generate(network, rowCount);
		writeCsv(complete, completeCsv);

		List<List<String>> missing = deepCopy(complete);
		generator.applyMissingness(missing, rowMissingProbability, cellMissingProbability, DEFAULT_MISSING_TOKEN);
		writeCsv(missing, missingCsv);
	}

	/**
	 * Convenience helper: load a model file, generate a complete dataset and write it to CSV.
	 *
	 * @param modelPath path to the model file (e.g. .cmpx, .ast, .cmp, .json)
	 * @param rowCount number of cases to generate
	 * @param completeCsv output path for the complete dataset
	 * @param seed random seed for reproducibility
	 *
	 * @throws ModelException if the model fails to load
	 * @throws IOException if writing the CSV fails
	 */
	public static void generateCompleteToCsv(Path modelPath, Path completeCsv, int rowCount, long seed) throws ModelException, IOException {
		Model model = Model.loadModel(modelPath.toString());
		DataGenerator generator = new DataGenerator(seed);
		writeCsv(generator.generate(generator.firstNetwork(model), rowCount), completeCsv);
	}

	/**
	 * Convenience helper: load a model file, generate a dataset with missing values and write it to CSV.
	 *
	 * @param modelPath path to the model file (e.g. .cmpx, .ast, .cmp, .json)
	 * @param rowCount number of cases to generate
	 * @param rowMissingProbability probability that a row has any missing values, in [0, 1]
	 * @param cellMissingProbability probability that a cell in an affected row is missing, in [0, 1]
	 * @param missingCsv output path for the dataset with missing values
	 * @param seed random seed for reproducibility
	 *
	 * @throws ModelException if the model fails to load
	 * @throws IOException if writing the CSV fails
	 */
	public static void generateMissingToCsv(Path modelPath, Path missingCsv, int rowCount, double rowMissingProbability, double cellMissingProbability, long seed) throws ModelException, IOException {
		Model model = Model.loadModel(modelPath.toString());
		DataGenerator generator = new DataGenerator(seed);
		writeCsv(generator.generateWithMissing(generator.firstNetwork(model), rowCount, rowMissingProbability, cellMissingProbability), missingCsv);
	}

	/**
	 * Writes a generated table to a comma-delimited CSV file.
	 *
	 * @param table the table (header plus data rows) to write
	 * @param path the output path
	 *
	 * @throws IOException if writing fails
	 */
	public static void writeCsv(List<List<String>> table, Path path) throws IOException {
		CsvWriter.writeCsv(table, path);
	}

	/**
	 * Returns the first Network of the Model, or throws if the Model has no Networks.
	 *
	 * @param model the Model
	 *
	 * @return the first Network
	 */
	public Network firstNetwork(Model model) {
		List<Network> networks = model.getNetworkList();
		if (networks.isEmpty()) {
			throw new AgenaRiskRuntimeException("Model has no Networks to sample from");
		}
		return networks.get(0);
	}

	/**
	 * Prepares a Network for sampling: optionally regenerates NPTs, computes a topological order and builds a per-Node
	 * sampler.
	 *
	 * @param network the Network to prepare
	 *
	 * @return a reusable sampling plan for the Network
	 */
	private PreparedNetwork prepare(Network network) {
		Objects.requireNonNull(network, "network must not be null");
		List<Node> nodes = network.getNodeList();
		if (nodes.isEmpty()) {
			throw new AgenaRiskRuntimeException("Network `" + network.getId() + "` has no Nodes to sample from");
		}

		ExtendedBN logicNetwork = network.getLogicNetwork();
		CoreBN connBn = logicNetwork.getConnBN();

		// Materialise expression/partitioned tables so getNPT() returns the compiled distribution for discrete nodes.
		if (regenerateTables) {
			for (Node node : nodes) {
				if (node.isSimulated() || node.isConnectedInput()) {
					continue;
				}
				try {
					logicNetwork.regenerateNPT(node.getLogicNode());
				}
				catch (Exception ex) {
					// Keep the existing NPT for this Node
				}
			}
		}

		List<Node> ordered = topologicalOrder(network, nodes);

		List<NodeSampler> samplers = new ArrayList<>(ordered.size());
		for (Node node : ordered) {
			if (node.isSimulated()) {
				samplers.add(new ContinuousExpressionSampler(node, network));
			}
			else {
				samplers.add(new DiscreteNptSampler(node, connBn));
			}
		}

		List<String> header = nodes.stream().map(Node::getId).collect(Collectors.toList());
		return new PreparedNetwork(header, samplers);
	}

	/**
	 * Computes a topological order (parents before children) using Kahn's algorithm, considering only intra-Network
	 * parents.
	 */
	private List<Node> topologicalOrder(Network network, List<Node> nodes) {
		Map<Node, Integer> inDegree = new LinkedHashMap<>();
		Map<Node, List<Node>> children = new LinkedHashMap<>();
		for (Node node : nodes) {
			inDegree.put(node, 0);
			children.put(node, new ArrayList<>());
		}

		for (Node node : nodes) {
			for (Node parent : node.getParents()) {
				if (!Objects.equals(parent.getNetwork(), network) || !inDegree.containsKey(parent)) {
					continue;
				}
				inDegree.put(node, inDegree.get(node) + 1);
				children.get(parent).add(node);
			}
		}

		Deque<Node> ready = new ArrayDeque<>();
		for (Node node : nodes) {
			if (inDegree.get(node) == 0) {
				ready.add(node);
			}
		}

		List<Node> ordered = new ArrayList<>(nodes.size());
		while (!ready.isEmpty()) {
			Node node = ready.poll();
			ordered.add(node);
			for (Node child : children.get(node)) {
				int remaining = inDegree.get(child) - 1;
				inDegree.put(child, remaining);
				if (remaining == 0) {
					ready.add(child);
				}
			}
		}

		if (ordered.size() != nodes.size()) {
			throw new AgenaRiskRuntimeException("Network `" + network.getId() + "` appears to contain a cycle; "
					+ "cannot compute a sampling order");
		}
		return ordered;
	}

	/**
	 * Formats a sampled continuous value for output.
	 */
	private String formatValue(double value, boolean integerNode) {
		if (Double.isNaN(value) || Double.isInfinite(value)) {
			return String.valueOf(value);
		}
		if (integerNode) {
			return Long.toString(Math.round(value));
		}
		BigDecimal bd = BigDecimal.valueOf(value).setScale(continuousDecimalPlaces, RoundingMode.HALF_UP).stripTrailingZeros();
		return bd.toPlainString();
	}

	private static List<List<String>> deepCopy(List<List<String>> table) {
		List<List<String>> copy = new ArrayList<>(table.size());
		for (List<String> row : table) {
			copy.add(new ArrayList<>(row));
		}
		return copy;
	}

	private static boolean isFinite(double d) {
		return !Double.isNaN(d) && !Double.isInfinite(d);
	}

	// =====================================================================================================
	// Per-row sampling state and samplers
	// =====================================================================================================

	/**
	 * Working state for a single row: each Node records a state index (into its states), a representative numeric
	 * value (for use by continuous children's expressions), and its output cell string.
	 */
	private static final class RowState {
		final Map<String, Integer> stateIndex = new LinkedHashMap<>();
		final Map<String, Double> value = new LinkedHashMap<>();
		final Map<String, String> cell = new LinkedHashMap<>();
	}

	/**
	 * A reusable plan for sampling rows: the output header (Node IDs) and the per-Node samplers in topological order.
	 */
	private final class PreparedNetwork {

		private final List<String> header;
		private final List<NodeSampler> samplers;

		private PreparedNetwork(List<String> header, List<NodeSampler> samplers) {
			this.header = header;
			this.samplers = samplers;
		}

		private List<String> sampleRow() {
			RowState state = new RowState();
			for (NodeSampler sampler : samplers) {
				sampler.sample(state);
			}
			List<String> row = new ArrayList<>(header.size());
			for (String nodeId : header) {
				row.add(state.cell.get(nodeId));
			}
			return row;
		}
	}

	/**
	 * Samples one Node per row, writing its state index / value / cell into the RowState.
	 */
	private interface NodeSampler {
		void sample(RowState state);
	}

	/**
	 * Samples a discrete / statically-discretised Node from its NPT column, selected by parents' sampled states.
	 * NPT layout is {@code npt[stateIndex][parentColumn]}; the column is the mixed-radix index over the CoreBN parent
	 * order (first parent slowest, last fastest), matching the engine.
	 */
	private final class DiscreteNptSampler implements NodeSampler {

		private final String nodeId;
		private final String[] stateLabels;
		private final double[] stateValues;
		private final float[][] npt;
		private final String[] parentIds;
		private final int[] parentStateCounts;

		private DiscreteNptSampler(Node node, CoreBN connBn) {
			this.nodeId = node.getId();
			ExtendedNode en = node.getLogicNode();

			CoreBNNode coreNode;
			try {
				coreNode = connBn.getNodeWithAltId(nodeId);
			}
			catch (Exception ex) {
				throw new AgenaRiskRuntimeException("Failed to resolve CoreBN node for `" + nodeId + "`", ex);
			}

			this.stateLabels = coreNode.getStateLabels().clone();
			this.stateValues = representativeStateValues(en);

			try {
				this.npt = en.getNPT();
			}
			catch (Exception ex) {
				throw new AgenaRiskRuntimeException("Failed to retrieve NPT for node `" + nodeId + "`", ex);
			}

			if (npt.length != stateLabels.length) {
				throw new AgenaRiskRuntimeException("NPT row count (" + npt.length + ") does not match state count ("
						+ stateLabels.length + ") for node `" + nodeId + "`");
			}

			CoreBNNodeList coreParents = coreNode.getParentNodes();
			int parentCount = coreParents.size();
			this.parentIds = new String[parentCount];
			this.parentStateCounts = new int[parentCount];
			long expectedColumns = 1L;
			for (int p = 0; p < parentCount; p++) {
				CoreBNNode coreParent = coreParents.get(p);
				parentIds[p] = coreParent.getAltId();
				parentStateCounts[p] = coreParent.getStateLabels().length;
				expectedColumns *= parentStateCounts[p];
			}

			int actualColumns = (npt.length > 0) ? npt[0].length : 0;
			if (actualColumns != expectedColumns) {
				throw new AgenaRiskRuntimeException("NPT column count (" + actualColumns + ") for node `" + nodeId
						+ "` does not match the number of parent state combinations (" + expectedColumns
						+ "). The table may not be compiled.");
			}
		}

		@Override
		public void sample(RowState state) {
			int column = 0;
			for (int p = 0; p < parentIds.length; p++) {
				Integer parentIndex = state.stateIndex.get(parentIds[p]);
				if (parentIndex == null) {
					throw new AgenaRiskRuntimeException("Parent `" + parentIds[p] + "` of node `" + nodeId
							+ "` was not sampled before it; topological order is broken");
				}
				column = column * parentStateCounts[p] + parentIndex;
			}

			double total = 0d;
			for (int s = 0; s < npt.length; s++) {
				double v = npt[s][column];
				if (v > 0d && !Double.isNaN(v)) {
					total += v;
				}
			}
			if (total <= 0d) {
				throw new AgenaRiskRuntimeException("Node `" + nodeId + "` has a zero/invalid probability column ("
						+ column + "); cannot sample. The table may not be compiled.");
			}

			double threshold = random.nextDouble() * total;
			double cumulative = 0d;
			int chosen = npt.length - 1;
			for (int s = 0; s < npt.length; s++) {
				double v = npt[s][column];
				if (v > 0d && !Double.isNaN(v)) {
					cumulative += v;
					if (threshold < cumulative) {
						chosen = s;
						break;
					}
				}
			}

			state.stateIndex.put(nodeId, chosen);
			state.value.put(nodeId, stateValues[chosen]);
			state.cell.put(nodeId, stateLabels[chosen]);
		}
	}

	/**
	 * Samples a simulation (continuous/integer interval) Node in native space from its statistical-distribution
	 * expression, evaluating the parameter expressions against the parents' sampled values.
	 */
	private final class ContinuousExpressionSampler implements NodeSampler {

		private final String nodeId;
		private final boolean integerNode;
		private final double[] stateLowerBounds;
		private final double[] stateUpperBounds;
		private final String[] stateLabels;

		// Parent value binding
		private final ExpressionParser parser;
		private final Map<String, com.singularsys.jep.Variable> parentVariables = new LinkedHashMap<>();

		// Partitioning (empty if single expression)
		private final String[] partitionParentIds;
		private final int[] partitionParentStateCounts;

		// One distribution spec per partition combination (or a single element if not partitioned)
		private final DistributionSpec[] specs;

		private ContinuousExpressionSampler(Node node, Network network) {
			this.nodeId = node.getId();
			ContinuousEN cen = (ContinuousEN) node.getLogicNode();
			this.integerNode = cen instanceof IntegerIntervalEN;

			// Capture the node's own discretisation, for binning the sampled value into a state.
			List<ExtendedState> states = cen.getExtendedStates();
			int stateCount = states.size();
			this.stateLowerBounds = new double[stateCount];
			this.stateUpperBounds = new double[stateCount];
			this.stateLabels = new String[stateCount];
			for (int i = 0; i < stateCount; i++) {
				ExtendedState es = states.get(i);
				Range r = es.getRange();
				stateLowerBounds[i] = (r != null) ? r.getLowerBound() : Double.NEGATIVE_INFINITY;
				stateUpperBounds[i] = (r != null) ? r.getUpperBound() : Double.POSITIVE_INFINITY;
				stateLabels[i] = es.getName().getShortDescription();
			}

			// Build one parser, declaring every in-network parent id and the node's own expression variables.
			this.parser = new ExpressionParser();
			try {
				for (Node parent : node.getParents()) {
					if (!Objects.equals(parent.getNetwork(), network)) {
						continue;
					}
					String pid = parent.getId();
					if (!parentVariables.containsKey(pid)) {
						parentVariables.put(pid, parser.addVariable(pid));
					}
				}
				List<uk.co.agena.minerva.util.model.Variable> vars = cen.getExpressionVariables().getVariables();
				for (uk.co.agena.minerva.util.model.Variable v : vars) {
					parser.addVariable(v.getName(), v.getValue());
				}
			}
			catch (JepException ex) {
				throw new AgenaRiskRuntimeException("Failed to declare expression variables for node `" + nodeId + "`", ex);
			}

			int mode = cen.getFunctionMode();
			if (mode == ExtendedNode.EDITABLE_PARENT_STATE_FUNCTIONS) {
				List<ExtendedNode> partitionParents = cen.getPartitionedExpressionModelNodes();
				this.partitionParentIds = new String[partitionParents.size()];
				this.partitionParentStateCounts = new int[partitionParents.size()];
				for (int i = 0; i < partitionParents.size(); i++) {
					ExtendedNode pp = partitionParents.get(i);
					partitionParentIds[i] = pp.getConnNodeId();
					partitionParentStateCounts[i] = pp.getExtendedStates().size();
				}
				List<ExtendedNodeFunction> enfs = cen.getCurrentPartitionedModelNodeFunctions();
				this.specs = new DistributionSpec[enfs.size()];
				for (int i = 0; i < enfs.size(); i++) {
					specs[i] = buildSpec(enfs.get(i));
				}
			}
			else if (mode == ExtendedNode.EDITABLE_NODE_FUNCTION) {
				this.partitionParentIds = new String[0];
				this.partitionParentStateCounts = new int[0];
				ExtendedNodeFunction enf = cen.getCurrentNodeFunction();
				if (enf == null) {
					throw new AgenaRiskRuntimeException("Simulation node `" + nodeId + "` has no expression to sample from");
				}
				this.specs = new DistributionSpec[]{buildSpec(enf)};
			}
			else {
				throw new AgenaRiskRuntimeException("Simulation node `" + nodeId + "` is not expression-based "
						+ "(function mode " + mode + "); cannot sample it in native space");
			}
		}

		private DistributionSpec buildSpec(ExtendedNodeFunction enf) {
			String distribution = enf.getName();
			List<String> paramExpressions = enf.getParameters();
			com.singularsys.jep.parser.Node[] parsed = new com.singularsys.jep.parser.Node[paramExpressions.size()];
			for (int i = 0; i < paramExpressions.size(); i++) {
				String expr = paramExpressions.get(i);
				try {
					parsed[i] = parser.parse(expr);
				}
				catch (Exception ex) {
					throw new AgenaRiskRuntimeException("Failed to parse parameter expression `" + expr
							+ "` of node `" + nodeId + "`", ex);
				}
			}
			return new DistributionSpec(distribution, parsed);
		}

		@Override
		public void sample(RowState state) {
			// Bind parent values for this row
			for (Map.Entry<String, com.singularsys.jep.Variable> e : parentVariables.entrySet()) {
				Double v = state.value.get(e.getKey());
				e.getValue().setValue((v == null || Double.isNaN(v)) ? 0d : v);
			}

			// Select the distribution spec (partition) from discrete parents' states
			int specIndex = 0;
			for (int p = 0; p < partitionParentIds.length; p++) {
				Integer idx = state.stateIndex.get(partitionParentIds[p]);
				if (idx == null) {
					throw new AgenaRiskRuntimeException("Partition parent `" + partitionParentIds[p] + "` of node `"
							+ nodeId + "` was not sampled before it; topological order is broken");
				}
				specIndex = specIndex * partitionParentStateCounts[p] + idx;
			}
			DistributionSpec spec = specs[specIndex];

			// Evaluate parameter expressions
			double[] params = new double[spec.paramNodes.length];
			for (int i = 0; i < params.length; i++) {
				try {
					Object result = parser.evaluate(spec.paramNodes[i]);
					if (!(result instanceof Number)) {
						throw new AgenaRiskRuntimeException("Parameter " + i + " of node `" + nodeId
								+ "` did not evaluate to a number");
					}
					params[i] = ((Number) result).doubleValue();
				}
				catch (JepException ex) {
					throw new AgenaRiskRuntimeException("Failed to evaluate parameter " + i + " of node `" + nodeId + "`", ex);
				}
			}

			double sampled = DistributionSampler.sample(spec.distribution, params, integerNode, random);

			int binned = binValue(sampled);
			state.value.put(nodeId, sampled);
			state.stateIndex.put(nodeId, binned);
			if (discretiseContinuous && binned >= 0) {
				state.cell.put(nodeId, stateLabels[binned]);
			}
			else {
				state.cell.put(nodeId, formatValue(sampled, integerNode));
			}
		}

		/**
		 * Bins a sampled value into the node's current discretisation, clamping to the first/last state if out of range.
		 */
		private int binValue(double value) {
			if (stateLowerBounds.length == 0) {
				return -1;
			}
			for (int i = 0; i < stateLowerBounds.length; i++) {
				if (value >= stateLowerBounds[i] && value < stateUpperBounds[i]) {
					return i;
				}
			}
			if (value <= stateLowerBounds[0]) {
				return 0;
			}
			return stateLowerBounds.length - 1;
		}
	}

	/**
	 * A distribution name plus its pre-parsed parameter expression trees, evaluated per row.
	 */
	private static final class DistributionSpec {
		private final String distribution;
		private final com.singularsys.jep.parser.Node[] paramNodes;

		private DistributionSpec(String distribution, com.singularsys.jep.parser.Node[] paramNodes) {
			this.distribution = distribution;
			this.paramNodes = paramNodes;
		}
	}

	/**
	 * Computes a representative numeric value per state of a discrete/statically-discretised Node, used when the Node
	 * feeds a continuous child's expression. Numeric nodes use the state's point value or interval midpoint; purely
	 * categorical nodes (Boolean/Labelled/Ranked) use the state index.
	 */
	private static double[] representativeStateValues(ExtendedNode en) {
		List<ExtendedState> states = en.getExtendedStates();
		double[] values = new double[states.size()];
		boolean numeric = (en instanceof ContinuousEN) || (en instanceof uk.co.agena.minerva.model.extendedbn.DiscreteRealEN);
		for (int i = 0; i < states.size(); i++) {
			ExtendedState es = states.get(i);
			double value = i;
			if (numeric) {
				Range r = es.getRange();
				if (r != null) {
					double lo = r.getLowerBound();
					double hi = r.getUpperBound();
					if (isFinite(lo) && isFinite(hi)) {
						value = (lo + hi) / 2d;
					}
					else if (isFinite(lo)) {
						value = lo;
					}
					else if (isFinite(hi)) {
						value = hi;
					}
					else if (isFinite(es.getNumericalValue())) {
						value = es.getNumericalValue();
					}
				}
				else if (isFinite(es.getNumericalValue())) {
					value = es.getNumericalValue();
				}
			}
			values[i] = value;
		}
		return values;
	}

	// =====================================================================================================
	// Native-space random variate samplers (java.util.Random based, single-seed reproducible)
	// =====================================================================================================

	/**
	 * Draws random variates from AgenaRisk statistical-distribution functions given already-evaluated numeric
	 * parameters. Parameterisations exactly match the AgenaRisk NPT generators (see
	 * {@code uk.co.agena.minerva.util.nptgenerator.*}): e.g. Normal/TNormal take variance (not standard deviation),
	 * Gamma is shape/scale, Exponential is a rate, Weibull is shape/scale, Log Normal's second parameter is the
	 * standard deviation of the underlying normal.
	 */
	static final class DistributionSampler {

		private DistributionSampler() {
		}

		static double sample(String distributionName, double[] p, boolean integerNode, Random r) {
			String name = distributionName.replaceAll("\\s", "").toLowerCase();
			double value;
			switch (name) {
				case "normal":
					value = p[0] + Math.sqrt(Math.max(0d, p[1])) * r.nextGaussian();
					break;
				case "tnormal":
					value = truncatedNormal(p[0], Math.sqrt(Math.max(0d, p[1])), p[2], p[3], r);
					break;
				case "uniform":
					value = p[0] + r.nextDouble() * (p[1] - p[0]);
					break;
				case "triangle":
					value = triangle(p[0], p[1], p[2], r);
					break;
				case "beta":
					value = p[2] + betaStandard(p[0], p[1], r) * (p[3] - p[2]);
					break;
				case "betapert":
					value = betaPert(p[0], p[1], p[2], p[3], r);
					break;
				case "gamma":
					value = gamma(p[0], p[1], r);
					break;
				case "lognormal":
					value = Math.exp(p[0] + p[1] * r.nextGaussian());
					break;
				case "exponential":
					value = -Math.log(1d - r.nextDouble()) / p[0];
					break;
				case "weibull":
					value = p[1] * Math.pow(-Math.log(1d - r.nextDouble()), 1d / p[0]);
					break;
				case "logistic":
					value = p[0] + p[1] * Math.log(u(r) / (1d - u(r)));
					break;
				case "chisquared":
					value = gamma(p[0] / 2d, 2d, r);
					break;
				case "student":
					value = r.nextGaussian() / Math.sqrt(gamma(p[0] / 2d, 2d, r) / p[0]);
					break;
				case "binomial":
					value = binomial((int) Math.round(p[0]), p[1], r);
					break;
				case "negativebinomial":
					value = poisson(gamma(p[0], (1d - p[1]) / p[1], r), r);
					break;
				case "poisson":
					value = poisson(p[0], r);
					break;
				case "geometric":
					value = geometric(p[0], r);
					break;
				case "arithmetic":
					value = p[0];
					break;
				default:
					throw new AgenaRiskRuntimeException("Simulation distribution `" + distributionName
							+ "` is not supported by DataGenerator");
			}
			return integerNode ? Math.round(value) : value;
		}

		/** Uniform (0,1), excluding exact 0 to avoid log/ratio singularities. */
		private static double u(Random r) {
			double v = r.nextDouble();
			return (v <= 0d) ? Double.MIN_VALUE : v;
		}

		private static double truncatedNormal(double mean, double sd, double lower, double upper, Random r) {
			if (!(upper > lower)) {
				return mean;
			}
			for (int i = 0; i < 1000; i++) {
				double x = mean + sd * r.nextGaussian();
				if (x >= lower && x <= upper) {
					return x;
				}
			}
			// Fallback: uniform over the truncation interval
			return lower + r.nextDouble() * (upper - lower);
		}

		private static double triangle(double min, double max, double mode, Random r) {
			if (!(max > min)) {
				return min;
			}
			double fc = (mode - min) / (max - min);
			double v = r.nextDouble();
			if (v < fc) {
				return min + Math.sqrt(v * (max - min) * (mode - min));
			}
			return max - Math.sqrt((1d - v) * (max - min) * (max - mode));
		}

		/** Standard Beta on (0,1) via two Gamma draws. */
		private static double betaStandard(double alpha, double beta, Random r) {
			double g1 = gamma(alpha, 1d, r);
			double g2 = gamma(beta, 1d, r);
			double sum = g1 + g2;
			return (sum > 0d) ? g1 / sum : 0.5d;
		}

		private static double betaPert(double mode, double confidence, double xmin, double xmax, Random r) {
			if (!(xmax > xmin)) {
				return xmin;
			}
			double mean = (xmin + confidence * mode + xmax) / (confidence + 2d);
			double alpha;
			double beta;
			if (Math.abs(mean - mode) < 1e-12) {
				alpha = confidence / 2d + 1d;
				beta = confidence / 2d + 1d;
			}
			else {
				alpha = ((mean - xmin) * (2d * mode - xmin - xmax)) / ((mode - mean) * (xmax - xmin));
				beta = alpha * (xmax - mean) / (mean - xmin);
			}
			alpha = Math.max(alpha, 1e-6);
			beta = Math.max(beta, 1e-6);
			return xmin + betaStandard(alpha, beta, r) * (xmax - xmin);
		}

		/** Gamma(shape, scale) via Marsaglia-Tsang. Mean = shape*scale. */
		private static double gamma(double shape, double scale, Random r) {
			if (shape <= 0d) {
				return 0d;
			}
			if (shape < 1d) {
				double u = u(r);
				return gamma(shape + 1d, scale, r) * Math.pow(u, 1d / shape);
			}
			double d = shape - 1d / 3d;
			double c = 1d / Math.sqrt(9d * d);
			while (true) {
				double x;
				double v;
				do {
					x = r.nextGaussian();
					v = 1d + c * x;
				}
				while (v <= 0d);
				v = v * v * v;
				double u = r.nextDouble();
				if (u < 1d - 0.0331d * (x * x) * (x * x)) {
					return d * v * scale;
				}
				if (Math.log(u) < 0.5d * x * x + d * (1d - v + Math.log(v))) {
					return d * v * scale;
				}
			}
		}

		private static int binomial(int n, double prob, Random r) {
			double p = Math.min(1d, Math.max(0d, prob));
			if (n <= 0) {
				return 0;
			}
			if (n <= 200) {
				int count = 0;
				for (int i = 0; i < n; i++) {
					if (r.nextDouble() < p) {
						count++;
					}
				}
				return count;
			}
			// Normal approximation for large n
			double mean = n * p;
			double sd = Math.sqrt(n * p * (1d - p));
			int v = (int) Math.round(mean + sd * r.nextGaussian());
			return Math.min(n, Math.max(0, v));
		}

		private static int poisson(double lambda, Random r) {
			if (lambda <= 0d) {
				return 0;
			}
			if (lambda < 60d) {
				// Knuth
				double l = Math.exp(-lambda);
				int k = 0;
				double prod = 1d;
				do {
					k++;
					prod *= r.nextDouble();
				}
				while (prod > l);
				return k - 1;
			}
			// Normal approximation for large lambda
			int v = (int) Math.round(lambda + Math.sqrt(lambda) * r.nextGaussian());
			return Math.max(0, v);
		}

		/** Number of failures before the first success; mean = (1-p)/p. */
		private static int geometric(double prob, Random r) {
			double p = Math.min(1d, Math.max(1e-12, prob));
			if (p >= 1d) {
				return 0;
			}
			return (int) Math.floor(Math.log(u(r)) / Math.log(1d - p));
		}
	}

	/**
	 * Command-line entry point for the common workflow.
	 * <br>
	 * Usage: {@code DataGenerator <modelPath> [rowCount=10000] [rowMissingProbability=0.3] [cellMissingProbability=0.2] [outputDir=.] [seed=42] [mode=both]}
	 * <br>
	 * {@code mode} is one of {@code complete}, {@code missing} or {@code both} (the default). Depending on the mode,
	 * writes {@code <modelName>_complete.csv} and/or {@code <modelName>_missing.csv} to the output directory. The
	 * missing dataset blanks cells in two stages: each row is affected with probability {@code rowMissingProbability},
	 * and within an affected row each cell is blanked with probability {@code cellMissingProbability}. Both
	 * probabilities are ignored in {@code complete} mode.
	 *
	 * @param args command-line arguments
	 *
	 * @throws Exception if the model fails to load or the CSVs fail to write
	 */
	public static void main(String[] args) throws Exception {
		if (args.length < 1) {
			System.out.println("Usage: DataGenerator <modelPath> [rowCount=10000] [rowMissingProbability=0.3] [cellMissingProbability=0.2] [outputDir=.] [seed=42] [mode=both]");
			System.out.println("  mode: complete | missing | both");
			return;
		}

		Path modelPath = Paths.get(args[0]);
		int rowCount = (args.length > 1) ? Integer.parseInt(args[1]) : 10000;
		double rowMissingProbability = (args.length > 2) ? Double.parseDouble(args[2]) : 0.3d;
		double cellMissingProbability = (args.length > 3) ? Double.parseDouble(args[3]) : 0.2d;
		Path outputDir = Paths.get((args.length > 4) ? args[4] : ".");
		long seed = (args.length > 5) ? Long.parseLong(args[5]) : 42L;
		String mode = (args.length > 6) ? args[6].trim().toLowerCase() : "both";

		boolean wantComplete = mode.equals("complete") || mode.equals("both");
		boolean wantMissing = mode.equals("missing") || mode.equals("both");
		if (!wantComplete && !wantMissing) {
			System.out.println("Unknown mode `" + mode + "`; expected complete | missing | both");
			return;
		}

		String modelFileName = modelPath.getFileName().toString();
		int dot = modelFileName.lastIndexOf('.');
		String baseName = (dot > 0) ? modelFileName.substring(0, dot) : modelFileName;

		Path completeCsv = outputDir.resolve(baseName + "_complete.csv");
		Path missingCsv = outputDir.resolve(baseName + "_missing.csv");

		String missingDesc = "rows affected ~" + (rowMissingProbability * 100) + "%, cells within ~" + (cellMissingProbability * 100) + "%";

		if (wantComplete && wantMissing) {
			// Sample once; the missing dataset is the same cases with holes punched
			generateFromModelFile(modelPath, rowCount, rowMissingProbability, cellMissingProbability, completeCsv, missingCsv, seed);
			System.out.println("Wrote " + rowCount + " complete rows to " + completeCsv.toAbsolutePath());
			System.out.println("Wrote " + rowCount + " rows with missing values (" + missingDesc + ") to " + missingCsv.toAbsolutePath());
		}
		else if (wantComplete) {
			generateCompleteToCsv(modelPath, completeCsv, rowCount, seed);
			System.out.println("Wrote " + rowCount + " complete rows to " + completeCsv.toAbsolutePath());
		}
		else {
			generateMissingToCsv(modelPath, missingCsv, rowCount, rowMissingProbability, cellMissingProbability, seed);
			System.out.println("Wrote " + rowCount + " rows with missing values (" + missingDesc + ") to " + missingCsv.toAbsolutePath());
		}
	}
}
