package expressiontree

import baseoperations.*
import config.CheckingKeyWords.Companion.comparisonTypesConflict
import config.ComparisonType
import config.CompiledConfiguration
import config.reverse
import config.strictComparison
import factstransformations.SubstitutionDirection
import logs.MessageType
import logs.log
import numbers.Complex
import optimizerutils.DomainPointGenerator
import kotlin.math.abs
import kotlin.math.max

class ExpressionComparator(
    val baseOperationsDefinitions: BaseOperationsDefinitions = BaseOperationsDefinitions(),
    private val baseOperationsComputationDouble: BaseOperationsComputation = BaseOperationsComputation(ComputationType.DOUBLE),
    private val baseOperationsComputationComplex: BaseOperationsComputation = BaseOperationsComputation(ComputationType.COMPLEX)
) {
    lateinit var compiledConfiguration: CompiledConfiguration
    lateinit var definedFunctionNameNumberOfArgsSet: MutableSet<String>

    fun init(compiledConfiguration: CompiledConfiguration) {
        this.compiledConfiguration = compiledConfiguration
        definedFunctionNameNumberOfArgsSet = compiledConfiguration.definedFunctionNameNumberOfArgsSet
    }

    fun compareAsIs(
        left: ExpressionNode, right: ExpressionNode, nameArgsMap: MutableMap<String, String> = mutableMapOf(),
        withBracketUnification: Boolean = false
    ): Boolean {
        val normalized = normalizeExpressionsForComparison(left, right)
        if (normalized.first.isNodeSubtreeEquals(normalized.second, nameArgsMap)) {
            return true
        } else if (!withBracketUnification) {
            return false
        }
        val lUnified = normalized.first
        lUnified.dropBracketNodesIfOperationsSame()
        val rUnified = normalized.second
        rUnified.dropBracketNodesIfOperationsSame()
        if (lUnified.isNodeSubtreeEquals(rUnified, nameArgsMap)) {
            return true
        }
        lUnified.normalizeSubTree(sorted = true)
        rUnified.normalizeSubTree(sorted = true)
        return lUnified.isNodeSubtreeEquals(rUnified, nameArgsMap)
    }

    private fun logicFullSearchComparison(
        leftOrigin: ExpressionNode, rightOrigin: ExpressionNode,
        comparisonType: ComparisonType = compiledConfiguration.comparisonSettings.defaultComparisonType,
        maxBustCount: Int = compiledConfiguration.comparisonSettings.maxExpressionBustCount
    ): Boolean {
        val normalized = normalizeExpressionsForComparison(leftOrigin, rightOrigin)
        val left = normalized.first
        val right = normalized.second
        if (compareAsIs(left, right)) {
            return !strictComparison(comparisonType)
        }
        if (right.children.isEmpty() || left.children.isEmpty()) {
            return false
        }
        baseOperationsDefinitions.computeExpressionTree(left.children[0])
        baseOperationsDefinitions.computeExpressionTree(right.children[0])

        val variablesNamesSet = mutableSetOf<String>()
        variablesNamesSet.addAll(left.getVariableNames())
        variablesNamesSet.addAll(right.getVariableNames())

        val variables = variablesNamesSet.toList()
        if (variables.size > maxBustCount) {
            return true
        }
        val variableValues = variables.map { Pair(it, "0") }.toMap().toMutableMap()

        return logicFullSearchComparisonRecursive(variables, variableValues, left, right, comparisonType, 0)
    }

    private fun logicFullSearchComparisonRecursive(
        variables: List<String>,
        variableValues: MutableMap<String, String>,
        left: ExpressionNode,
        right: ExpressionNode,
        comparisonType: ComparisonType,
        currentIndex: Int
    ): Boolean {
        if (currentIndex == variables.size) {
            val l = baseOperationsComputationDouble.compute(
                left.cloneWithNormalization(variableValues, sorted = false)
            ) as Double
            val r = baseOperationsComputationDouble.compute(
                right.cloneWithNormalization(variableValues, sorted = false)
            ) as Double
            when (comparisonType) {
                ComparisonType.LEFT_MORE_OR_EQUAL -> if (l < r && !baseOperationsDefinitions.additivelyEqual(l, r)) {
                    return false
                }
                ComparisonType.LEFT_MORE -> if (l <= r || baseOperationsDefinitions.additivelyEqual(l, r)) {
                    return false
                }
                ComparisonType.LEFT_LESS_OR_EQUAL -> if (l > r && !baseOperationsDefinitions.additivelyEqual(l, r)) {
                    return false
                }
                ComparisonType.LEFT_LESS -> if (l >= r || baseOperationsDefinitions.additivelyEqual(l, r)) {
                    return false
                }
                else -> if (!baseOperationsDefinitions.additivelyEqual(l, r)) {
                    return false
                }
            }
        } else {
            variableValues[variables[currentIndex]] = "0"
            if (!logicFullSearchComparisonRecursive(
                    variables,
                    variableValues,
                    left,
                    right,
                    comparisonType,
                    currentIndex + 1
                )
            ) {
                return false
            }

            variableValues[variables[currentIndex]] = "1"
            if (!logicFullSearchComparisonRecursive(
                    variables,
                    variableValues,
                    left,
                    right,
                    comparisonType,
                    currentIndex + 1
                )
            ) {
                return false
            }
        }
        return true
    }

    fun probabilityTestComparison(
        leftOrigin: ExpressionNode, rightOrigin: ExpressionNode,
        comparisonType: ComparisonType = compiledConfiguration.comparisonSettings.defaultComparisonType,
        justInDomainsIntersection: Boolean = compiledConfiguration.comparisonSettings.justInDomainsIntersection,

        maxMinNumberOfPointsForEquality: Int = compiledConfiguration.comparisonSettings.minNumberOfPointsForEquality,
        allowedPartOfErrorTests: Double = compiledConfiguration.comparisonSettings.allowedPartOfErrorTests,
        testWithUndefinedResultIncreasingCoef: Double = compiledConfiguration.comparisonSettings.testWithUndefinedResultIncreasingCoef,
        useGradientDescentComparison: Boolean = false
    ): Boolean {
        val normalized = normalizeExpressionsForComparison(leftOrigin, rightOrigin)
        val left = normalized.first
        val right = normalized.second
        if (compareAsIs(left, right)) {
            return !strictComparison(comparisonType)
        }
        if (right.children.isEmpty() || left.children.isEmpty()) {
            return false
        }
        baseOperationsDefinitions.computeExpressionTree(left.children[0])
        baseOperationsDefinitions.computeExpressionTree(right.children[0])
        var numberOfRemainingTests = (left.getCountOfNodes() + right.getCountOfNodes()).toDouble()
        if (comparisonType == ComparisonType.EQUAL) {
            val domain = PointGenerator(baseOperationsDefinitions, arrayListOf(left, right))
            val totalTests = numberOfRemainingTests
            var passedTests = 0.0
            val minNumberOfPointsForEquality = max(
                max(left.getMaxMinNumberOfPointsForEquality(), right.getMaxMinNumberOfPointsForEquality()),
                maxMinNumberOfPointsForEquality
            )
            val isHaveComplexNode = left.haveComplexNode() || right.haveComplexNode()
            if (domain.variablesSet.isEmpty()) {
                if (isHaveComplexNode) {
                    return (baseOperationsComputationComplex.compute(left) as Complex).equals(
                        baseOperationsComputationComplex.compute(right) as Complex
                    )
                } else {
                    return baseOperationsDefinitions.additivelyEqual(
                        baseOperationsComputationDouble.compute(left) as Double,
                        baseOperationsComputationDouble.compute(right) as Double
                    )
                }
            }
            while (numberOfRemainingTests-- > 0) {
                val pointI = domain.generateNewPoint()
                val leftInPoint = left.cloneWithNormalization(pointI, sorted = false)
                val rightInPoint = right.cloneWithNormalization(pointI, sorted = false)
                if (isHaveComplexNode) {
                    val lComplex = baseOperationsComputationComplex.compute(leftInPoint) as Complex
                    val rComplex = baseOperationsComputationComplex.compute(rightInPoint) as Complex
                    if (lComplex.equals(rComplex)) {
                        passedTests++
                    }
                } else {
                    val lDouble = baseOperationsComputationDouble.compute(leftInPoint) as Double
                    val rDouble = baseOperationsComputationDouble.compute(rightInPoint) as Double
                    if (lDouble.isNaN() || rDouble.isNaN()) {
                        if ((lDouble.isNaN() != rDouble.isNaN()) && justInDomainsIntersection) {
                            return false
                        }
                        val lComplex = baseOperationsComputationComplex.compute(leftInPoint) as Complex
                        val rComplex = baseOperationsComputationComplex.compute(rightInPoint) as Complex
                        if (lComplex.equals(rComplex)) {
                            passedTests++
                        }
                    } else {
                        if (!lDouble.isFinite() || !rDouble.isFinite()) {
                            numberOfRemainingTests += testWithUndefinedResultIncreasingCoef
                        } else if (baseOperationsDefinitions.additivelyEqual(lDouble, rDouble)) {
                            passedTests++
                        } else {
                            return false
                        }
                    }
                }
                if (passedTests >= minNumberOfPointsForEquality) {
                    return true
                }
            }
            return (passedTests >= totalTests * (1 - allowedPartOfErrorTests) && passedTests >= minNumberOfPointsForEquality) || (passedTests >= totalTests)
        } else {
            val domain = DomainPointGenerator(arrayListOf(left, right), baseOperationsDefinitions)
            if (!domain.hasVariables()) {
                return compareInequalityInPoint(left, mutableMapOf(), right, justInDomainsIntersection, comparisonType)
            }
            val points = domain.generateSimplePoints()
            for (pointI in points) {
                if (!compareInequalityInPoint(left, pointI, right, justInDomainsIntersection, comparisonType)) {
                    return false
                }
            }
            while (numberOfRemainingTests-- > 0) {
                val pointI = domain.generateNewPoint()
                if (!compareInequalityInPoint(left, pointI, right, justInDomainsIntersection, comparisonType)) {
                    return false
                }
            }
            if (useGradientDescentComparison) {
                return gradientDescentComparison(left, right, compiledConfiguration, comparisonType, domain)
            } else {
                return true
            }
        }
    }

    private fun compareInequalityInPoint(
        left: ExpressionNode,
        pointI: MutableMap<String, String>,
        right: ExpressionNode,
        justInDomainsIntersection: Boolean,
        comparisonType: ComparisonType
    ): Boolean {
        val l = baseOperationsComputationDouble.compute(
            left.cloneWithNormalization(pointI, sorted = false)
        ) as Double
        val r = baseOperationsComputationDouble.compute(
            right.cloneWithNormalization(pointI, sorted = false)
        ) as Double
        if (justInDomainsIntersection && (l.isNaN() != r.isNaN())) {
            return false
        } else when (comparisonType) {
            ComparisonType.LEFT_MORE_OR_EQUAL -> if (l < r && !baseOperationsDefinitions.additivelyEqual(l, r)) {
                return false
            }
            ComparisonType.LEFT_MORE -> if (l <= r || baseOperationsDefinitions.additivelyEqual(l, r)) {
                return false
            }
            ComparisonType.LEFT_LESS_OR_EQUAL -> if (l > r && !baseOperationsDefinitions.additivelyEqual(l, r)) {
                return false
            }
            ComparisonType.LEFT_LESS -> if (l >= r || baseOperationsDefinitions.additivelyEqual(l, r)) {
                return false
            }
            else -> return true
        }
        return true
    }

    fun compareWithoutSubstitutions(
        l: ExpressionNode, r: ExpressionNode,
        comparisonType: ComparisonType = compiledConfiguration.comparisonSettings.defaultComparisonType,
        definedFunctionNameNumberOfArgs: Set<String> = definedFunctionNameNumberOfArgsSet,
        justInDomainsIntersection: Boolean = compiledConfiguration.comparisonSettings.justInDomainsIntersection
    ): Boolean {
        if (compareAsIs(l, r, withBracketUnification = false))
            return true
        val left = l.clone()
        val right = r.clone()
        left.applyAllImmediateSubstitutions(compiledConfiguration)
        right.applyAllImmediateSubstitutions(compiledConfiguration)
        left.applyAllSubstitutions(compiledConfiguration.compiledFunctionDefinitions)
        right.applyAllSubstitutions(compiledConfiguration.compiledFunctionDefinitions)
        left.variableReplacement(compiledConfiguration.variableConfiguration.variableImmediateReplacementMap)
        right.variableReplacement(compiledConfiguration.variableConfiguration.variableImmediateReplacementMap)
        left.normalizeSubTree(sorted = true)
        right.normalizeSubTree(sorted = true)
        if (compiledConfiguration.comparisonSettings.compareExpressionsWithProbabilityRulesWhenComparingExpressions &&
            !left.isBoolExpression(compiledConfiguration.functionConfiguration.boolFunctions) && !right.isBoolExpression(
                compiledConfiguration.functionConfiguration.boolFunctions
            )
        ) {
            val functionIdentifierToVariableMap = mutableMapOf<ExpressionNode, String>()
            left.replaceNotDefinedFunctionsOnVariables(
                functionIdentifierToVariableMap,
                definedFunctionNameNumberOfArgs,
                this
            )
            right.replaceNotDefinedFunctionsOnVariables(
                functionIdentifierToVariableMap,
                definedFunctionNameNumberOfArgs,
                this
            )
            return probabilityTestComparison(
                left,
                right,
                comparisonType,
                justInDomainsIntersection = justInDomainsIntersection
            )
        }
        return compareAsIs(
            left.cloneAndSimplifyByCommutativeNormalizeAndComputeSimplePlaces(compiledConfiguration),
            right.cloneAndSimplifyByCommutativeNormalizeAndComputeSimplePlaces(compiledConfiguration),
            withBracketUnification = true
        )
    }

    private fun checkOpeningBracketsSubstitutions(
        expressionToTransform: ExpressionNode,
        otherExpression: ExpressionNode,
        expressionChainComparisonType: ComparisonType
    ): Boolean {
        val openingBracketsTransformationResults =
            expressionToTransform.computeResultsOfOpeningBracketsSubstitutions(compiledConfiguration)
        for (expression in openingBracketsTransformationResults) {
            if (compareWithoutSubstitutions(expression, otherExpression, expressionChainComparisonType)) {
                return true
            }
        }
        return false
    }

    private val algebraAutoCheckingFunctionsSet = setOf(
        "_0",
        "_1",
        "+_-1",
        "-_-1",
        "*_-1",
        "/_-1",
        "^_-1",
        "sin_1",
        "cos_1",
        "sh_1",
        "ch_1",
        "th_1",
        "tg_1",
        "asin_1",
        "acos_1",
        "atg_1",
        "exp_1",
        "ln_1",
        "abs_1"
    )
    private val setAutoCheckingFunctionsSet = setOf("_0", "_1", "and_-1", "or_-1", "xor_-1", "alleq_-1", "not_1")

    private fun fastProbabilityCheckOnIncorrectTransformation(
        l: ExpressionNode, r: ExpressionNode,
        comparisonType: ComparisonType = compiledConfiguration.comparisonSettings.defaultComparisonType,
        maxBustCount: Int = compiledConfiguration.comparisonSettings.maxExpressionBustCount
    ): Boolean {
        val left = l.clone()
        val right = r.clone()
        left.applyAllImmediateSubstitutions(compiledConfiguration)
        right.applyAllImmediateSubstitutions(compiledConfiguration)
        left.applyAllSubstitutions(compiledConfiguration.compiledFunctionDefinitions)
        right.applyAllSubstitutions(compiledConfiguration.compiledFunctionDefinitions)
        left.variableReplacement(compiledConfiguration.variableConfiguration.variableImmediateReplacementMap)
        right.variableReplacement(compiledConfiguration.variableConfiguration.variableImmediateReplacementMap)
        if (!left.containsFunctionBesides(algebraAutoCheckingFunctionsSet) && !right.containsFunctionBesides(
                algebraAutoCheckingFunctionsSet
            )
        ) {
            if (!probabilityTestComparison(
                    left,
                    right,
                    comparisonType,
                    compiledConfiguration.comparisonSettings.justInDomainsIntersection
                )
            ) {
                return false
            }
        } else if (!left.containsFunctionBesides(setAutoCheckingFunctionsSet) && !right.containsFunctionBesides(
                setAutoCheckingFunctionsSet
            )
        ) {
            if (!logicFullSearchComparison(left, right, comparisonType, maxBustCount)) {
                return false
            }
        }
        return true
    }

    fun fastProbabilityCheckOnZero(expression: ExpressionNode) =
        fastProbabilityCheckOnIncorrectTransformation(
            expression, ExpressionNode(NodeType.FUNCTION, "")
                .apply { addChild(ExpressionNode(NodeType.VARIABLE, "0")) }, ComparisonType.EQUAL
        )

    fun compareWithTreeTransformationRules(
        leftOriginal: ExpressionNode,
        rightOriginal: ExpressionNode,
        transformations: Collection<ExpressionSubstitution>,
        maxTransformationWeight: Double = compiledConfiguration.comparisonSettings.maxExpressionTransformationWeight,
        maxBustCount: Int = compiledConfiguration.comparisonSettings.maxExpressionBustCount,
        minTransformationWeight: Double = transformations.minByOrNull { it.weight }?.weight
            ?: 1.0,
        expressionChainComparisonType: ComparisonType = ComparisonType.EQUAL,
        maxDistBetweenDiffSteps: Double = 1.0
    ): Boolean {
        if (transformations.all { !it.basedOnTaskContext } &&
            !fastProbabilityCheckOnIncorrectTransformation(
                leftOriginal,
                rightOriginal,
                expressionChainComparisonType,
                maxBustCount
            )) { // incorrect comparisons take much more time to check it becouse of importance of all rules combination searching. Here we try to avoid such
            return false
        }

        val resultForOperandsInOriginalOrder = compareWithTreeTransformationRulesInternal(
            leftOriginal,
            rightOriginal,
            transformations,
            maxTransformationWeight,
            maxBustCount,
            minTransformationWeight,
            expressionChainComparisonType,
            false,
            maxDistBetweenDiffSteps = maxDistBetweenDiffSteps
        )
        if (resultForOperandsInOriginalOrder) {
            return true
        } else {
            return compareWithTreeTransformationRulesInternal(
                leftOriginal,
                rightOriginal,
                transformations,
                maxTransformationWeight,
                maxBustCount,
                minTransformationWeight,
                expressionChainComparisonType,
                true,
                maxDistBetweenDiffSteps = maxDistBetweenDiffSteps
            )
        }

    }

    fun compareWithTreeTransformationRulesInternal(
        leftOriginal: ExpressionNode,
        rightOriginal: ExpressionNode,
        transformations: Collection<ExpressionSubstitution>,
        maxTransformationWeight: Double = compiledConfiguration.comparisonSettings.maxExpressionTransformationWeight,
        maxBustCount: Int = compiledConfiguration.comparisonSettings.maxExpressionBustCount,
        minTransformationWeight: Double = transformations.minByOrNull { it.weight }?.weight
            ?: 1.0,
        expressionChainComparisonType: ComparisonType = ComparisonType.EQUAL,
        sortOperands: Boolean = false,
        maxDistBetweenDiffSteps: Double = 1.0
    ): Boolean {
        val normFormsOfExpressionNode =
            mutableMapOf<ExpressionSubstitutionNormType, Pair<ExpressionNode, ExpressionNode>>()
        val left =
            if (sortOperands) leftOriginal.cloneWithSortingChildrenForExpressionSubstitutionComparison() else leftOriginal.clone()
        val right =
            if (sortOperands) rightOriginal.cloneWithSortingChildrenForExpressionSubstitutionComparison() else rightOriginal.clone()
        left.applyAllImmediateSubstitutions(compiledConfiguration)
        right.applyAllImmediateSubstitutions(compiledConfiguration)
        definedFunctionNameNumberOfArgsSet = if (maxTransformationWeight < 0.5) {
            compiledConfiguration.noTransformationDefinedFunctionNameNumberOfArgsSet
        } else {
            compiledConfiguration.definedFunctionNameNumberOfArgsSet
        } //set as global class instance parameter because it also mast be used for function arguments comparison
        if (compareWithoutSubstitutions(
                left,
                right,
                expressionChainComparisonType
            )
        ) return true //we can have situation, when this true after first sorted, but we require sort with only substitution
        if (maxTransformationWeight < minTransformationWeight) return false
        if (left.containsDifferentiation() || right.containsDifferentiation()) {
            val leftDiffWeight =
                (listOf(0.0) + if (maxDistBetweenDiffSteps > unlimitedWeight) listOf(maxDistBetweenDiffSteps) else emptyList()).toMutableList()
            val rightDiffWeight =
                (listOf(0.0) + if (maxDistBetweenDiffSteps > unlimitedWeight) listOf(maxDistBetweenDiffSteps) else emptyList()).toMutableList()
            val leftDiff = left.diff(leftDiffWeight, compiledConfiguration)
            val rightDiff = right.diff(rightDiffWeight, compiledConfiguration)
            if (abs(leftDiffWeight[0] - rightDiffWeight[0]) < maxDistBetweenDiffSteps &&
                compareWithoutSubstitutions(leftDiff, rightDiff, expressionChainComparisonType)
            ) {
                return true
            }
        }
        if (checkOpeningBracketsSubstitutions(left, right, expressionChainComparisonType) ||
            checkOpeningBracketsSubstitutions(right, left, expressionChainComparisonType.reverse())
        ) {
            return true
        }
        val functionsInExpression = left.getContainedFunctions() + right.getContainedFunctions()
        for (originalTransformation in transformations.filter { it.weight <= maxTransformationWeight }) {
            if (!originalTransformation.basedOnTaskContext && //taskContextRules contains quite small count of rules and its applicability depends not only on functions
                originalTransformation.leftFunctions.isNotEmpty() &&
                functionsInExpression.intersect(originalTransformation.leftFunctions).isEmpty()
            ) { //fast check to indicate that rule is not applicable
                continue
            }
            val transformation = if (sortOperands) {
                ExpressionSubstitution(
                    originalTransformation.left.cloneWithSortingChildrenForExpressionSubstitutionComparison(),
                    originalTransformation.right.cloneWithSortingChildrenForExpressionSubstitutionComparison(),
                    originalTransformation.weight,
                    originalTransformation.basedOnTaskContext,
                    originalTransformation.code,
                    originalTransformation.nameEn,
                    originalTransformation.nameRu,
                    originalTransformation.comparisonType,
                    originalTransformation.leftFunctions,
                    originalTransformation.matchJumbledAndNested,
                    originalTransformation.priority,
                    originalTransformation.changeOnlyOrder,
                    originalTransformation.simpleAdditional,
                    originalTransformation.isExtending,
                    originalTransformation.normType
                )
            } else {
                originalTransformation
            }
            if (!normFormsOfExpressionNode.containsKey(transformation.normType)) {
                val l =
                    if (sortOperands) left.cloneWithSortingChildrenForExpressionSubstitutionComparison() else left.clone()
                val r =
                    if (sortOperands) right.cloneWithSortingChildrenForExpressionSubstitutionComparison() else right.clone()
                when (transformation.normType) {
                    ExpressionSubstitutionNormType.ORIGINAL -> normFormsOfExpressionNode[transformation.normType] =
                        Pair(l, r)
                    ExpressionSubstitutionNormType.SORTED -> normFormsOfExpressionNode[transformation.normType] = Pair(
                        left.cloneWithSortingChildrenForExpressionSubstitutionComparison(),
                        right.cloneWithSortingChildrenForExpressionSubstitutionComparison()
                    )
                    ExpressionSubstitutionNormType.I_MULTIPLICATED -> normFormsOfExpressionNode[transformation.normType] =
                        Pair(
                            left.cloneWithIMultipleNorm(),
                            right.clone()
                        )//for right not need normalization because all substitutions in normal forms
                    ExpressionSubstitutionNormType.SORTED_AND_I_MULTIPLICATED -> normFormsOfExpressionNode[transformation.normType] =
                        Pair(
                            left.cloneWithSortingChildrenForExpressionSubstitutionComparison()
                                .iMultiplicativeNormForm(),
                            right.cloneWithSortingChildrenForExpressionSubstitutionComparison()
                        )
                }
            }
            val l = normFormsOfExpressionNode[transformation.normType]!!.first.clone()
            val r = normFormsOfExpressionNode[transformation.normType]!!.second.clone()
            val direction = getComparingDirection(expressionChainComparisonType, transformation.comparisonType)
            if (direction == null) {
                log.add(expressionChainComparisonType.string, transformation.comparisonType.string, {
                    "$comparisonTypesConflict '"
                }, { "' in expression vs '" }, { "' in rule. MSG_CODE_" }, messageType = MessageType.USER)
            }
            val substitutionPlaces = if (direction != SubstitutionDirection.RIGHT_TO_LEFT) {
                transformation.findAllPossibleSubstitutionPlaces(l, this)
            } else {
                listOf<SubstitutionPlace>()
            } +
                if (direction != SubstitutionDirection.LEFT_TO_RIGHT) {
                    transformation.findAllPossibleSubstitutionPlaces(r, this)
                } else {
                    listOf<SubstitutionPlace>()
                }
            val bitMaskCount = 1 shl substitutionPlaces.size
            if (bitMaskCount * transformations.size > maxBustCount) {
                transformation.applySubstitution(substitutionPlaces, this)
                if (compareWithTreeTransformationRulesInternal(
                        l, r, transformations, maxTransformationWeight - transformation.weight,
                        maxBustCount, minTransformationWeight, expressionChainComparisonType, sortOperands
                    )
                )
                    return true
            } else {
                for (bitMask in 1 until bitMaskCount) {
                    transformation.applySubstitutionByBitMask(substitutionPlaces, bitMask)
                    if (compareWithTreeTransformationRulesInternal(
                            l.clone(), r.clone(), transformations, maxTransformationWeight - transformation.weight,
                            maxBustCount, minTransformationWeight, expressionChainComparisonType, sortOperands
                        )
                    )
                        return true
                }
            }
        }
        return false
    }

    fun fullExpressionsCompare(
        left: ExpressionNode, right: ExpressionNode,
        expressionChainComparisonType: ComparisonType = ComparisonType.EQUAL
    ): Boolean {
        left.applyAllSubstitutions(compiledConfiguration.compiledImmediateTreeTransformationRules)
        right.applyAllSubstitutions(compiledConfiguration.compiledImmediateTreeTransformationRules)
        if (compiledConfiguration.comparisonSettings.isComparisonWithRules) {
            if (compareWithTreeTransformationRulesInternal(
                    left,
                    right,
                    compiledConfiguration.compiledExpressionTreeTransformationRules,
                    compiledConfiguration.comparisonSettings.maxTransformationWeight,
                    compiledConfiguration.comparisonSettings.maxBustCount,
                    expressionChainComparisonType = expressionChainComparisonType
                )
            ) return true
            baseOperationsDefinitions.computeExpressionTree(left.children[0])
            baseOperationsDefinitions.computeExpressionTree(right.children[0])
            if (compareWithTreeTransformationRulesInternal(
                    left,
                    right,
                    compiledConfiguration.compiledExpressionTreeTransformationRules,
                    compiledConfiguration.comparisonSettings.maxTransformationWeight,
                    compiledConfiguration.comparisonSettings.maxBustCount,
                    expressionChainComparisonType = expressionChainComparisonType
                )
            ) return true
        } else {
            if (compareWithoutSubstitutions(left, right)) return true
        }
        return false
    }
}

fun getComparingDirection(
    expressionChainComparisonType: ComparisonType,
    transformationComparisonType: ComparisonType
): SubstitutionDirection? {
    return when (expressionChainComparisonType) {
        ComparisonType.EQUAL -> if (transformationComparisonType == ComparisonType.EQUAL) SubstitutionDirection.ALL_TO_ALL else null
        ComparisonType.LEFT_MORE_OR_EQUAL -> when (transformationComparisonType) {
            ComparisonType.EQUAL -> SubstitutionDirection.ALL_TO_ALL
            ComparisonType.LEFT_MORE_OR_EQUAL, ComparisonType.LEFT_MORE -> SubstitutionDirection.LEFT_TO_RIGHT
            ComparisonType.LEFT_LESS_OR_EQUAL, ComparisonType.LEFT_LESS -> SubstitutionDirection.RIGHT_TO_LEFT
        }
        ComparisonType.LEFT_LESS_OR_EQUAL -> when (transformationComparisonType) {
            ComparisonType.EQUAL -> SubstitutionDirection.ALL_TO_ALL
            ComparisonType.LEFT_MORE_OR_EQUAL, ComparisonType.LEFT_MORE -> SubstitutionDirection.RIGHT_TO_LEFT
            ComparisonType.LEFT_LESS_OR_EQUAL, ComparisonType.LEFT_LESS -> SubstitutionDirection.LEFT_TO_RIGHT
        }
        ComparisonType.LEFT_MORE -> when (transformationComparisonType) {
            ComparisonType.LEFT_MORE -> SubstitutionDirection.LEFT_TO_RIGHT
            ComparisonType.LEFT_LESS -> SubstitutionDirection.RIGHT_TO_LEFT
            else -> null
        }
        ComparisonType.LEFT_LESS -> when (transformationComparisonType) {
            ComparisonType.LEFT_MORE -> SubstitutionDirection.RIGHT_TO_LEFT
            ComparisonType.LEFT_LESS -> SubstitutionDirection.LEFT_TO_RIGHT
            else -> null
        }
    }
}