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
- Enum: `generation`, `discovery`, `averaging`, `tableLearning`, `structureEvaluation`, `performanceEvaluation`, `merger`
    - `generation` stage allows to generate a model rather than use structure discovery algorithms.  
    For example, you could generate a null-hypothesis model where all variables are disconnected, or a randomised model where random edges are created.  
    Parameters:
        - `maximumEdgeCount`
        - `statesFromData`
        - `dataPath`
    - `discovery` stage uses one of the available structure learning algorithms to analyse the data and generate a suitable structure.  
    This stage allows to specify `knowledge` constraints to guide structure discovery.  
    One stage can generate only one model, so you can use multiple stages with different parameters or knowledge to generate multiple model variants for later evaluation and comparison.  
    Parameters:
        - `pruningLevel`
        - `bicLog`
        - `logLikelihoodScore`
        - `maximumMeanDiscrepancyType`
    - `averaging` stage generates an average model based on all previously generated or discovered models.  
    Parameters:
        - `minimumEdgeAppearanceCountToKeep`
        - `statesFromData`
        - `dataPath`
    - `tableLearning` stage can be used to learn variable state probabilities.  
    EM algorithm is used.  
    This stage allows to specify `knowledge` constraints to guide EM algorithm. Knowledge vs data weights can be applied on a model-wide level or individually to variables. In this context, the knowledge is considered to be the existing probabilities in the model prior to table learning.  
    Parameters:
        - `dataPath`
        - `modelStageLabel`
        - `convergenceThreshold`
        - `maxIterations`
        - `missingValue`
        - `valueSeparator`
    - `structureEvaluation` stage calculates a score for each previously generated or discovered model so it is possible to rank or compare.  
    Bayesian Information Criterion (BIC) and Log-Likelihood (LL) scores can be calculated.  
    Parameters:
        - `dataPath`
    - `performanceEvaluation` stage calculates various accuracy and performance metrics in relation to a selected target node for each previously generated or discovered model so it is possible to rank or compare.  
    Includes Absolute Error, Brier Score, Spherical Score. Receiver Operating Characteristic (ROC) macro and micro AUC, as well as graph points can also be calculated.  
    Parameters:
        - `dataPath`
        - `target`
        - `calculateRoc`
    - `merger` stage is designed for local use only and will merge all previously generated or discovered models into a single CMPX file, such that each model appears as a network in this file.  
    This file can be viewed in agena .ai modeller desktop or in the [online model viewer](https://portal.agena.ai/modeller).
        
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
- Applies when: `stage` is `discovery`.

### `connectionsUndirected`
- Type: `array`
- Undirected edges.
- Applies when: `stage` is `discovery`.

### `connectionsForbidden`
- Type: `array`
- Forbidden connections.
- Applies when: `stage` is `discovery`.

### `connectionsTemporal`
- Type: `array`
- Temporal orderings (array of tiers, each tier is an array of variables in that tier).
- Applies when: `stage` is `discovery`.

### `prohibitConnectionsSameTemporalTier`
- Type: `boolean`
- Default: `false`
- Disallow connections within the same tier.
- Applies when: `stage` is `discovery`.

### `connectionsInitialGuess`
- Type: `array`
- Initial connection guesses, which may inform the starting point of discovery but are not required to appear in the final structure.
- Applies when: `stage` is `discovery`.

### `variablesAreRelevant`
- Type: `boolean`
- Default: `false`
- Mark all variables as relevant, which prohibits islands of variables.
- Applies when: `stage` is `discovery`.

### `reduceDimensionalityPenaltyForVariables`
- Type: `array`
- List of variable groups (as arrays) for which dimensionality penalty is reduced.
- Applies when: `stage` is `discovery`.

### `dimensionalityReductionRate`
- Type: `integer`
- Rate of dimensionality reduction. Must be between 2 and 30.
- Applies when: `stage` is `discovery`.

### `skipNodes`
- Type: `array`
- Items: `string`
- List of nodes to skip from structure learning.
- Applies when: `stage` is `tableLearning`.

### `nodeDataWeightsCustom`
- Type: `array`
- Items: arrays with exactly two elements: `[string, number]`
- Custom data weights per node. First element is the node name, second is the weight.
- Applies when: `stage` is `tableLearning`.

---

## `pipeline[].parameters`

### `pruningLevel`
- Type: `integer`
- Default: `0`
- Applies when: `stage` is `discovery` and `algorithm` is one of: `HC`, `TABU`, `MAHC`.

### `maxInDegreePreProcessing`
- Type: `integer`
- Enum: `2`, `3`
- Default: `3`
- Maximum in-degree before processing.
- Applies when: `stage` is `discovery` and `algorithm` is `MAHC`.

### `bicLog`
- Type: `string`
- Enum: `2`, `10`, `e`
- Applies when: `stage` is `discovery` or `structureEvaluation`.

### `logLikelihoodScore`
- Type: `boolean`
- Default: `false`
- Applies when `stage` is `structureEvaluation`.

### `minimumEdgeAppearanceCountToKeep`
- Type: `integer`
- Default: `1`
- Applies when `stage` is `averaging`.

### `evaluationDataPath`
- Type: `string`
- Path to the evaluation data file to be used only during evaluation (relative or absolute).
- Applies when `stage` is `structureEvaluation`.

### `dataPath`
- Type: `string`
- Path to the data file (relative or absolute).  
When used with `generation` stage, this file informs the states to be created for relevant variables.  
When used with `structureEvaluation` stage, this file will be used to evaluate the models against.  
When used with `performanceEvaluation` stage, this file will be used to evaluate predictive performance of the model against.
When used with `tableLearning` stage, this data is used to learn state probabilities using EM algorithm.
- Applies when `stage` is `structureEvaluation`, `performanceEvaluation`, `generation` or `tableLearning`.

### `maximumEdgeCount`
- Type: `integer`
- Default: `0`
- Maximum number of edges for generation.
- Applies when `stage` is `generation`.

### `statesFromData`
- Type: `boolean`
- Default: `false`
- When set to true, whole training dataset is processed to collect unique states and configure nodes accordingly.
- Applies when `stage` is `generation` or `averaging`.

### `maximumMeanDiscrepancyType`
- Type: `string`
- Enum: `Mutual_Information`, `Mean_Absolute`, `Max_Absolute`, `MeanMax_Absolute`, `Mean_Relative`, `Max_Relative`
- Default: `Mean_Absolute`
- Maximum Mean Discrepancy (MMD) type and Score and Distance types for SaiyanH.
- Applies when `stage` is `discovery` and `algorithm` is `SaiyanH`.

### `modelStageLabel`
- Type: `string`
- Label of the model stage to which this table learning step applies.
- Required when: `stage` is `tableLearning`.

### `missingValue`
- Type: `string`
- Default: `""`
- Symbol used to represent missing values in the input data file.
- Applies when: `stage` is `tableLearning`.

### `valueSeparator`
- Type: `string`
- Default: `","`
- Separator character for values in the input file.
- Applies when: `stage` is `tableLearning`.

### `maxIterations`
- Type: `integer`
- Range: `0` to `50`
- Maximum number of iterations before the EM algorithm stops, even if it has not converged.
- Applies when: `stage` is `tableLearning`.

### `convergenceThreshold`
- Type: `number`
- Range: `0` to `1`
- Convergence threshold for the EM algorithm. Once the entropy error is below this threshold, the algorithm is considered converged and stops.
- Applies when: `stage` is `tableLearning`.
