# JSON Configuration Format

## Top-level Properties

### `dataFilePath`
- Type: `string`
- Path to the input data file (relative or absolute).

### `outputDirPath`
- Type: `string`
- Path to the output directory (relative or absolute).

### `printSummary`
- Type: `boolean`
- Default: `false`
- Flag to indicate whether to print a summary of discovery and evaluation.

### `saveSummary`
- Type: `boolean`
- Default: `false`
- Flag to indicate whether to save a CSV summary of discovery and evaluation.

### `pipeline`
- Type: `array`
- Sequence of pipeline stages.

---

## `pipeline[]` Stage Object

### `stage`
- Type: `string`
- Pipeline stage type.
- Enum: `generation`, `discovery`, `averaging`, `evaluation`, `merger`
    - `generation` stage allows to generate a model rather than use structure discovery algorithms.  
    For example, you could generate a null-hypothesis model where all variables are disconnected, or a randomised model where random edges are created.
    - `discovery` stage uses one of the available structure learning algorithms to analyse the data and generate a suitable structure.  
    This stage allows to specify knowledge constraints to guide structure discovery.  
    One stage can generate only one model, so you can use multiple stages with different parameters or knowledge to generate multiple model variants for later evaluation and comparison.
    - `averaging` stage generates an average model based on all previously generated or discovered models.
    - `evaluation` stage calculates a score for each previously generated or discovered model so it is possible to rank or compare.  
    Bayesian Information Criterion (BIC) and Log-Likelihood (LL) scores can be calculated.
    - `merger` stage is designed for local use only and will merge all previously generated or discovered models into a single CMPX file, such that each model appears as a network in this file. This file can be viewed in agena .ai modeller desktop or in the [online model viewer](https://portal.agena.ai/modeller).

### `algorithm`
- Type: `string`
- Enum: `HC`, `SaiyanH`, `GES`, `TABU`, `MAHC`
- Algorithm used for the stage (if applicable).

### `label`
- Type: `string`
- Label for this stage.

### `knowledge`
- Type: `object`
- Prior knowledge constraints.

### `parameters`
- Type: `object`
- Stage-specific parameters.

---

## `pipeline[].knowledge`

Only applies when `stage` is `discovery`.

### `connectionsDirected`
- Type: `array`
- Directed edges.

### `connectionsUndirected`
- Type: `array`
- Undirected edges.

### `connectionsForbidden`
- Type: `array`
- Forbidden connections.

### `connectionsTemporal`
- Type: `array`
- Temporal orderings (array of tiers, each tier is an array of variables in that tier).

### `prohibitConnectionsSameTemporalTier`
- Type: `boolean`
- Default: `false`
- Disallow connections within the same tier.

### `connectionsInitialGuess`
- Type: `array`
- Initial connection guesses, which may inform the starting point of discovery but are not required to appear in the final structure.

### `variablesAreRelevant`
- Type: `boolean`
- Default: `false`
- Mark all variables as relevant, which prohibits islands of variables.

### `reduceDimensionalityPenaltyForVariables`
- Type: `array`
- List of variable groups (as arrays) for which dimensionality penalty is reduced.

### `dimensionalityReductionRate`
- Type: `integer`
- Rate of dimensionality reduction. Must be between 2 and 30.

### `maxInDegreePreProcessing`
- Type: `integer`
- Enum: `2`, `3`
- Default: `3`
- Maximum in-degree before processing.

---

## `pipeline[].parameters`

### `pruningLevel`
- Type: `integer`
- Default: `0`
- (Applies when: `stage` is `discovery` and `algorithm` is one of: `HC`, `TABU`, `MAHC`)

### `bicLog`
- Type: `string`
- Enum: `2`, `10`, `e`
- (Applies when: `stage` is `discovery` or `evaluation`)

### `logLikelihoodScore`
- Type: `boolean`
- Default: `False`
- (Applies to `stage` is `evaluation`)

### `minimumEdgeAppearanceCountToKeep`
- Type: `integer`
- Default: `1`
- (Applies to: `stage`: averaging)

### `evaluationDataPath`
- Type: `string`
- Path to the evaluation data file to be used only during evaluation (relative or absolute). (Applies when `stage` is `evaluation`)

### `maximumEdgeCount`
- Type: `integer`
- Default: `0`
- Maximum number of edges for generation. (Applies when `stage` is `generation`)

### `maximumMeanDiscrepancyType`
- Type: `string`
- Enum: `Mutual_Information`, `Mean_Absolute`, `Max_Absolute`, `MeanMax_Absolute`, `Mean_Relative`, `Max_Relative`
- Default: `Mean_Absolute`
- Maximum Mean Discrepancy (MMD) type and Score and Distance types for SaiyanH. (Applies when `stage` is `discovery` and `algorithm` is `SaiyanH`)
