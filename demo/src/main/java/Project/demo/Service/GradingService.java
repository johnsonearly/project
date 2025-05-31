package Project.demo.Service;

import Project.demo.Component.RubricLoader;
import Project.demo.DTOs.CodeSubmission;
import Project.demo.DTOs.GradingResult;
import Project.demo.Enums.ProficiencyLevel; // Assuming this Enum exists: Beginner, Intermediate, Advanced
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class GradingService {

    private static final Logger logger = LoggerFactory.getLogger(GradingService.class);
    private static final String DEFAULT_DIFFICULTY = "Beginner";

    private final RubricLoader rubricLoader;

    @Autowired
    public GradingService(RubricLoader rubricLoader) {
        this.rubricLoader = rubricLoader;
    }

    /**
     * Grades a code submission based on predefined rubrics.
     * This service focuses solely on evaluating the code and producing a score and feedback.
     * It no longer directly interacts with QLearningAgent or ProficiencyTracker to update user states;
     * that responsibility now lies with ExerciseServiceImpl after grading.
     *
     * @param submission The CodeSubmission object containing code, questionId, userId, and difficulty.
     * @return A GradingResult object with score, classification, and detailed feedback.
     */
    public GradingResult grade(CodeSubmission submission) {
        String questionId = submission.getQuestionId();
        String code = submission.getCode();
        String userId = submission.getUserName();

        // --- NEW: Preliminary check for valid Java code structure ---
        if (!isLikelyJavaCode(code)) {
            logger.warn("Submitted code for user {} (Question ID '{}') does not resemble valid Java code. Assigning 0 score.", userId, questionId);
            GradingResult result = new GradingResult();
            result.setScore(0.0);
            result.setClassification("Invalid Submission");
            result.setFeedback("The submitted code does not appear to be valid Java syntax (e.g., missing 'public class', 'import', or 'package' statements, or gibberish). Please submit actual Java code.");
            result.setDetailedScores(new LinkedHashMap<>()); // Empty detailed scores
            return result;
        }
        // --- END NEW CHECK ---

        String difficulty = submission.getDifficultyLevel();
        if (difficulty == null || difficulty.isBlank()) {
            difficulty = DEFAULT_DIFFICULTY;
            logger.warn("Difficulty level not provided for submission {}. Defaulting to '{}'. User: {}",
                    questionId, DEFAULT_DIFFICULTY, userId);
        }

        // Rubrics loaded here. Assuming keys are like "Problem-Solving_Problem_Identification_4" mapping to a score (e.g., 4.0)
        Map<String, Double> specificRubric = rubricLoader.loadRubric(questionId);
        Map<String, Double> generalRubric = rubricLoader.loadGeneralRubric();

        // Evaluate the submission based on rubrics
        GradingResult result = evaluateSubmission(code, questionId, difficulty, specificRubric, generalRubric);

        logger.info("Graded submission for user {}: Question ID '{}', Score: {:.2f}, Classification: {}",
                userId, questionId, result.getScore(), result.getClassification());

        return result;
    }

    /**
     * Performs a preliminary check to see if the submitted string resembles Java code.
     * This is a very basic check and not a full syntax validation.
     *
     * @param code The submitted code string.
     * @return true if the code is likely Java, false otherwise.
     */
    private boolean isLikelyJavaCode(String code) {
        if (code == null || code.trim().isEmpty()) {
            return false;
        }
        String normalizedCode = code.toLowerCase();
        // Check for common Java class/method declarations or package/import statements
        boolean hasClassDefinition = normalizedCode.contains("public class") || normalizedCode.contains("class ");
        boolean hasMethodDefinition = normalizedCode.contains("public static void main") || normalizedCode.contains("private void ") || normalizedCode.contains("public void ");
        boolean hasPackageOrImport = normalizedCode.contains("package ") || normalizedCode.contains("import ");

        // Consider it likely Java if it has a class/method definition OR package/import statements.
        // Also, add a basic check for excessive non-alphanumeric characters or very short length.
        long alphaNumericCount = normalizedCode.chars().filter(Character::isLetterOrDigit).count();
        double ratio = (double) alphaNumericCount / normalizedCode.length();

        // If the code is very short or has a very low alphanumeric ratio, it's likely gibberish.
        if (normalizedCode.length() < 20 || ratio < 0.4) { // Thresholds can be adjusted
            return false;
        }

        return hasClassDefinition || hasMethodDefinition || hasPackageOrImport;
    }


    /**
     * Core method to evaluate the submitted code against various criteria.
     *
     * @param code The submitted code string.
     * @param questionId The ID of the question.
     * @param difficulty The difficulty of the question.
     * @param specificRubric Question-specific rubric scores.
     * @param generalRubric General rubric scores.
     * @return A GradingResult containing overall score, classification, feedback, and detailed scores.
     */
    private GradingResult evaluateSubmission(String code, String questionId, String difficulty,
                                             Map<String, Double> specificRubric,
                                             Map<String, Double> generalRubric) {
        GradingResult result = new GradingResult();

        Map<String, Map<String, Double>> detailedScores = new LinkedHashMap<>();

        // Evaluate each main category. These methods now return the *assessed level* (0-4) for each sub-criterion.
        // The actual rubric score will be applied in calculateCategoryScore via mapToRubricScore.
        Map<String, Double> problemSolvingAssessedLevels = evaluateProblemSolving(code, questionId);
        Map<String, Double> criticalThinkingAssessedLevels = evaluateCriticalThinking(code);
        Map<String, Double> programmingLogicAssessedLevels = evaluateProgrammingLogic(code);
        Map<String, Double> creativityAssessedLevels = evaluateCreativity(code, difficulty);

        // Map assessed levels to actual rubric scores and calculate category average (scaled 0-100)
        double problemSolvingScore = calculateCategoryScore(problemSolvingAssessedLevels, specificRubric, generalRubric, "Problem-Solving", detailedScores);
        double criticalThinkingScore = calculateCategoryScore(criticalThinkingAssessedLevels, specificRubric, generalRubric, "Critical Thinking", detailedScores);
        double programmingLogicScore = calculateCategoryScore(programmingLogicAssessedLevels, specificRubric, generalRubric, "Programming Logic", detailedScores);
        double creativityScore = calculateCategoryScore(creativityAssessedLevels, specificRubric, generalRubric, "Creativity", detailedScores);

        // Calculate total score with weights
        boolean isAdvanced = "advanced".equalsIgnoreCase(difficulty);
        double totalScore = (problemSolvingScore * 0.25) +
                (criticalThinkingScore * 0.25) +
                (programmingLogicScore * 0.40) +
                (creativityScore * (isAdvanced ? 0.10 : 0.05));

        totalScore = Math.max(0, Math.min(100, totalScore)); // Ensure 0-100 range

        result.setScore(totalScore);
        result.setClassification(classifyScore(totalScore));
        result.setFeedback(generateFeedback(detailedScores));
        result.setDetailedScores(detailedScores);

        return result;
    }

    /**
     * Calculates the average score for a category and populates the detailedScores map.
     * This method is responsible for mapping the assessed raw levels (0-4) to the actual rubric scores.
     *
     * @param assessedLevels Raw assessment levels (0-4) for each sub-criteria.
     * @param specificRubric Question-specific rubric.
     * @param generalRubric General rubric.
     * @param categoryName The name of the category (e.g., "Problem-Solving").
     * @param detailedScores The map to populate with criterion-level scores.
     * @return The averaged score for the category (scaled to 0-100).
     */
    private double calculateCategoryScore(Map<String, Double> assessedLevels,
                                          Map<String, Double> specificRubric,
                                          Map<String, Double> generalRubric,
                                          String categoryName,
                                          Map<String, Map<String, Double>> detailedScores) {
        Map<String, Double> mappedScores = assessedLevels.entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        entry -> mapToRubricScore(categoryName, entry.getKey(), entry.getValue(), specificRubric, generalRubric),
                        (oldValue, newValue) -> newValue,
                        LinkedHashMap::new
                ));
        detailedScores.put(categoryName, mappedScores);

        // Calculate average of the *mapped* scores for the category
        double averageRubricScore = mappedScores.values().stream()
                .mapToDouble(Double::doubleValue)
                .average()
                .orElse(0.0);

        // Scale the average score from a 0-4 range (assuming rubric max is 4) to a 0-100 range
        return (averageRubricScore / 4.0) * 100;
    }

    /**
     * Maps a raw assessed level (e.g., 0-4 from evaluate methods) to a rubric score based on the rubric data.
     * It constructs a key like "Problem-Solving_Problem_Identification_4" and looks up the score.
     *
     * @param categoryName The name of the category (e.g., "Problem-Solving").
     * @param criteria The name of the criteria (e.g., "Problem Identification").
     * @param assessedLevel The raw level assessed by the internal methods (e.g., 0.0 to 4.0).
     * @param specificRubric Question-specific rubric.
     * @param generalRubric General rubric.
     * @return The mapped rubric score (expected 0.0 to 4.0), or the assessedLevel if no rubric entry.
     */
    private double mapToRubricScore(String categoryName, String criteria, double assessedLevel,
                                    Map<String, Double> specificRubric,
                                    Map<String, Double> generalRubric) {
        // Round the assessedLevel to the nearest integer to match rubric keys (e.g., "_0", "_1", "_2", "_3", "_4")
        int roundedLevel = (int) Math.round(assessedLevel);
        roundedLevel = Math.max(0, Math.min(4, roundedLevel)); // Ensure level is within 0-4

        // Construct rubric key: e.g., "Problem-Solving_Problem_Identification_4"
        String rubricKey = categoryName.replace(" ", "_") + "_" + criteria.replace(" ", "_") + "_" + roundedLevel;

        Double score = specificRubric.get(rubricKey);
        if (score == null) {
            score = generalRubric.get(rubricKey);
        }

        // Fallback: if no rubric entry, return the assessed level itself.
        // This is a safety measure; ideally, all rubric entries should exist.
        if (score == null) {
            logger.warn("Rubric entry not found for key: {}. Falling back to assessed level: {}", rubricKey, assessedLevel);
            return assessedLevel;
        }
        return score;
    }

    // --- EVALUATION METHODS (Return assessed levels, typically 0-4) ---
    // These methods simulate code analysis and return an 'assessed level' (0-4)
    // based on how well the code aligns with the criterion.
    // In a real system, these would leverage AST parsing, static analysis tools (e.g., PMD, Checkstyle),
    // dynamic analysis (running test cases), and potentially AI/ML models.

    // Each method below needs to contain logic to derive a score based on real code characteristics.
    // For this demonstration, the logic remains simplified regex/string checks, but comments
    // indicate what a more robust implementation would entail.

    private Map<String, Double> evaluateProblemSolving(String code, String questionId) {
        Map<String, Double> scores = new LinkedHashMap<>(); // Use LinkedHashMap for predictable order

        // 0: Lacking, 1: Emerging, 2: Proficient, 3: Exceeding, 4: Exceptional
        scores.put("Problem Identification", assessProblemIdentification(code, questionId));
        scores.put("Solution Implementation", assessSolutionImplementation(code, questionId));
        scores.put("Error Handling and Validation", assessErrorHandling(code));
        scores.put("Handling Edge Cases", assessEdgeCases(code, questionId));
        scores.put("Testing the Solution", assessTesting(code));

        return scores;
    }

    private double assessProblemIdentification(String code, String questionId) {
        // TODO: Replace with actual analysis.
        // For example:
        // - Does the code correctly parse/interpret the problem's inputs? (Requires semantic understanding)
        // - Does the solution approach directly address the core problem, not a tangential one?
        // - Could use AI to compare code logic with problem description.
        // For demo: rudimentary check for keywords related to expected problem type.
        switch(questionId) {
            case "1": // Example: Count vowels problem
                return (code.contains("count") && (code.contains("vowel") || code.contains("aeiou") || code.contains("char"))) ? 4.0 : 1.0;
            case "2": // Example: Palindrome check
                return (code.contains("reverse") || code.contains("equalsIgnoreCase") || code.contains("isPalindrome")) ? 4.0 : 1.0;
            default:
                return (code.length() > 50 && (code.contains("main") || code.contains("public class"))) ? 2.0 : 0.0;
        }
    }

    private double assessSolutionImplementation(String code, String questionId) {
        // TODO: Replace with actual analysis.
        // - Run provided test cases against the code. (Functional correctness is paramount)
        // - Check for correctness of algorithm steps.
        // - Analyze control flow.
        // For demo: basic check if the code attempts a solution that might run.
        if (code.contains("System.out.println") || code.contains("return")) {
            return 3.0; // Implemented something
        }
        if (code.length() > 100) {
            return 2.0; // Some implementation exists
        }
        return 0.0; // No meaningful implementation
    }

    private double assessErrorHandling(String code) {
        // TODO: Replace with actual analysis.
        // - Check for try-catch blocks.
        // - Check for input validation (e.g., null checks, range checks, format checks).
        // - Check for custom exception handling.
        // For demo: check for common error handling keywords.
        if (code.contains("try") && code.contains("catch") && code.contains("Exception")) {
            if (Pattern.compile("if\\s*\\([\\w\\d\\s]*==\\s*null\\)").matcher(code).find() || Pattern.compile("if\\s*\\([\\w\\d\\s]*<\\s*0\\)").matcher(code).find()) {
                return 4.0; // Comprehensive error handling and validation
            }
            return 3.0; // Basic error handling
        }
        return 1.0; // Lacking or very minimal
    }

    private double assessEdgeCases(String code, String questionId) {
        // TODO: Replace with actual analysis.
        // - Run specific edge-case test cases (e.g., empty input, max/min values, invalid formats).
        // - Analyze code for specific checks for edge conditions (e.g., array bounds, division by zero).
        // For demo: simplistic check based on problem type and keywords.
        switch(questionId) {
            case "1": // Vowel counting: check for empty string, string with no vowels, very long string
                return (code.contains("length() == 0") || code.contains("if (s.isEmpty())")) ? 4.0 : 2.0;
            case "2": // Palindrome: check for empty string, single char, string with spaces/punctuation
                return (code.contains("replaceAll") && code.contains("!Character.isLetterOrDigit")) ? 4.0 : 2.0;
            default:
                return code.contains("if") && code.contains("else if") ? 3.0 : 1.0;
        }
    }

    private double assessTesting(String code) {
        // TODO: Replace with actual analysis.
        // - Look for JUnit tests, custom main methods with test assertions.
        // - Evaluate test coverage (if external tool integrated).
        // For demo: simple check for common test indicators.
        if (code.contains("import org.junit") || code.contains("@Test")) {
            return 4.0; // Uses a testing framework
        }
        if (code.contains("public static void main") && (code.contains("assert") || code.contains("System.out.println(\"Test\"))"))) {
            return 3.0; // Includes some manual testing
        }
        return 1.0; // No apparent testing
    }

    private Map<String, Double> evaluateCriticalThinking(String code) {
        Map<String, Double> scores = new LinkedHashMap<>();

        scores.put("Debugging", assessDebuggingQuality(code));
        scores.put("Code Optimization", assessOptimizationLevel(code));
        scores.put("Evaluation of Logic", assessLogicEvaluation(code));
        scores.put("Pattern Recognition", assessPatternRecognition(code));
        scores.put("Conditional Thinking", assessConditionalThinking(code));

        return scores;
    }

    private double assessDebuggingQuality(String code) {
        // TODO: Replace with actual analysis.
        // - Check for print statements used for debugging (often removed in final, but presence during dev is indicator).
        // - Evidence of thoughtful error handling (e.g., logging specific error details).
        // - Can't truly assess debugging *process* from static code, but final code quality implies it.
        // For demo: look for common debugging prints or basic logging.
        if (code.contains("logger.debug") || code.contains("printStackTrace") || code.contains("System.err.println")) {
            return 2.0; // Evidence of debugging attempts
        }
        return 1.0; // Little to no evidence
    }

    private double assessOptimizationLevel(String code) {
        // TODO: Replace with actual analysis.
        // - Analyze algorithmic complexity (Big O notation).
        // - Check for efficient data structure usage.
        // - Identify redundant computations or inefficient loops.
        // - For demo: Very simplistic heuristic, e.g., avoiding nested loops where a single pass might suffice (hard to detect).
        if (code.contains("for (int i = 0; i <") && code.contains("for (int j = 0; j <") && code.indexOf("for (int j = 0; j <") > code.indexOf("for (int i = 0; i <")) {
            // Nested loop detected (potential for inefficiency, but depends on problem)
            // This is very rudimentary. A real system would need deep analysis.
            if (code.length() > 500 && code.contains("new StringBuilder()")) { // Implies some thought on efficiency
                return 3.0; // Some optimization attempts
            }
            return 2.0; // Could be optimized
        }
        if (code.contains("StringBuilder") || code.contains("HashMap") || code.contains("HashSet")) {
            return 4.0; // Uses more efficient data structures/techniques
        }
        return 1.0; // Basic or inefficient
    }

    private double assessLogicEvaluation(String code) {
        // TODO: Replace with actual analysis.
        // - Does the code's control flow logically follow the problem requirements?
        // - Are conditions correctly formulated?
        // - Are there logical gaps or contradictions?
        // - Can be partially inferred by successful test cases (high score implies good logic).
        // For demo: Check for complex conditional structures and logical operators.
        if (Pattern.compile("if\\s*\\(.*&&.*\\)").matcher(code).find() || Pattern.compile("if\\s*\\(.*\\|\\|.*\\)").matcher(code).find()) {
            return 3.0; // Uses complex logic
        }
        if (code.split("if").length > 3) { // Multiple if statements
            return 2.0;
        }
        return 1.0;
    }

    private double assessPatternRecognition(String code) {
        // TODO: Replace with actual analysis.
        // - Does the student identify repeatable patterns and abstract them into functions/loops?
        // - Use of recursion for recursive problems.
        // - Generalization of a specific solution.
        // For demo: check for reusable functions or methods.
        if (code.split("public\\s+\\w+\\s+\\w+\\s*\\(").length > 2) { // More than just main method
            return 3.0; // Defines helper methods
        }
        if (code.contains("for") && code.contains("while")) {
            return 2.0; // Utilizes different looping constructs, implying some pattern recognition
        }
        return 1.0;
    }

    private double assessConditionalThinking(String code) {
        // TODO: Replace with actual analysis.
        // - Correct use of if-else, switch, ternary operators.
        // - Handling of different scenarios.
        // - Depth of nested conditionals (sometimes indicates complex thought, sometimes bad design).
        // For demo: check for different conditional structures.
        if (code.contains("switch") || (code.contains("if") && code.contains("else if") && code.contains("else"))) {
            return 4.0; // Sophisticated conditional use
        }
        if (code.contains("if") && code.contains("else")) {
            return 3.0; // Good conditional coverage
        }
        if (code.contains("if")) {
            return 2.0; // Basic conditionals
        }
        return 1.0;
    }


    private Map<String, Double> evaluateProgrammingLogic(String code) {
        Map<String, Double> scores = new LinkedHashMap<>();

        scores.put("Use of Variables", assessVariableUsage(code));
        scores.put("Use of Loops", assessLoopUsage(code));
        scores.put("Use of Conditionals", assessConditionalUsage(code));
        scores.put("Procedures & Functions", assessFunctionUsage(code));
        scores.put("Use of Operators & Expressions", assessOperatorUsage(code));
        scores.put("Program Initialization", assessInitialization(code));
        scores.put("Program Termination", assessTermination(code));

        return scores;
    }

    private double assessVariableUsage(String code) {
        // TODO: Replace with actual analysis.
        // - Meaningful variable names.
        // - Correct data types.
        // - Scope management.
        // - Proper initialization.
        // For demo: check for common variable declarations.
        if (Pattern.compile("int\\s+\\w+\\s*=\\s*").matcher(code).find() && Pattern.compile("String\\s+\\w+\\s*=\\s*").matcher(code).find()) {
            return 3.0; // Uses different types, likely initialized
        }
        if (Pattern.compile("\\w+\\s+\\w+\\s*=\\s*").matcher(code).find()) {
            return 2.0; // Some variable usage
        }
        return 1.0;
    }

    private double assessLoopUsage(String code) {
        // TODO: Replace with actual analysis.
        // - Correct loop termination conditions.
        // - Appropriate loop type (for, while, do-while, for-each).
        // - Handling of loop invariants.
        // For demo: presence of different loop types.
        if (code.contains("for") && code.contains("while")) {
            return 4.0; // Diverse loop usage
        }
        if (code.contains("for") || code.contains("while")) {
            return 3.0; // Uses loops appropriately
        }
        return 1.0;
    }

    private double assessConditionalUsage(String code) {
        // TODO: This is somewhat redundant with assessConditionalThinking, but for logic, we can focus on syntax/correctness.
        // - Correct syntax of if-else.
        // - Proper boolean expressions.
        // For demo: basic presence.
        return assessConditionalThinking(code); // Re-use for consistency
    }

    private double assessFunctionUsage(String code) {
        // TODO: Replace with actual analysis.
        // - Modularization into functions/methods.
        // - Proper parameter passing and return types.
        // - Cohesion and coupling of functions.
        // For demo: check for multiple defined methods.
        if (code.split("public\\s+\\w+\\s+\\w+\\s*\\(").length > 3) { // Multiple distinct methods beyond main
            return 4.0; // Strong modularization
        }
        if (code.split("public\\s+\\w+\\s+\\w+\\s*\\(").length > 2) {
            return 3.0; // Uses functions for modularity
        }
        return 1.0;
    }

    private double assessOperatorUsage(String code) {
        // TODO: Replace with actual analysis.
        // - Correct use of arithmetic, relational, logical, assignment operators.
        // - Operator precedence understanding.
        // For demo: check for various operators.
        if (code.contains("+") && code.contains("-") && code.contains("*") && code.contains("/") && code.contains("=") && code.contains("==") && code.contains("!=") && code.contains(">") && code.contains("<")) {
            return 4.0; // Diverse and correct operator use
        }
        if (code.contains("+") || code.contains("-") || code.contains("=") || code.contains("==")) {
            return 2.0; // Basic operator use
        }
        return 1.0;
    }

    private double assessInitialization(String code) {
        // TODO: Replace with actual analysis.
        // - All variables used are initialized.
        // - Resources (e.g., file streams) are properly initialized.
        // For demo: check for variable declarations with immediate assignments.
        if (Pattern.compile("\\w+\\s+\\w+\\s*=\\s*[^;]+;").matcher(code).find()) {
            return 3.0; // Variables are generally initialized
        }
        return 1.0;
    }

    private double assessTermination(String code) {
        // TODO: Replace with actual analysis.
        // - Program terminates correctly.
        // - No infinite loops.
        // - Resources are closed (e.g., finally blocks for streams).
        // For demo: presence of return/exit statements, or basic program structure implying termination.
        if (code.contains("return") || code.contains("System.exit")) {
            return 3.0; // Explicit termination
        }
        // If it's a simple method, returning implies termination. For full program, it's harder.
        return 2.0;
    }


    private Map<String, Double> evaluateCreativity(String code, String difficulty) {
        Map<String, Double> scores = new LinkedHashMap<>();

        scores.put("Originality", assessOriginality(code));
        scores.put("Innovative Problem Approach", assessInnovation(code, difficulty));
        scores.put("Creative Expansion of Problem", assessProblemExpansion(code));
        scores.put("Code Organization and Clarity", assessCodeOrganization(code));
        scores.put("Exploration of Alternatives", assessAlternativeSolutions(code));

        return scores;
    }

    private double assessOriginality(String code) {
        // TODO: Replace with actual analysis.
        // - Requires comparison against a corpus of common solutions for the problem.
        // - AI/ML could detect novel patterns.
        // For demo: Very hard to assess without context. Assume some "uncommon" keyword usage for demo.
        if (code.contains("interface") || code.contains("abstract class") || code.contains("lambda")) {
            return 3.0; // Uses more advanced or less common (for beginners) features, implying some originality
        }
        return 1.0;
    }

    private double assessInnovation(String code, String difficulty) {
        // TODO: Replace with actual analysis.
        // - Does the solution present a novel or unusually elegant approach?
        // - Is the solution significantly better (performance, readability) than typical?
        // - For advanced levels, higher expectation.
        // For demo: check for complexity or advanced features.
        if ("advanced".equalsIgnoreCase(difficulty) && (code.contains("StreamAPI") || code.contains("concurrency"))) {
            return 4.0; // Innovative for advanced
        }
        if (code.contains("recursion")) {
            return 3.0; // Shows a different approach
        }
        return 1.0;
    }

    private double assessProblemExpansion(String code) {
        // TODO: Replace with actual analysis.
        // - Does the code include features beyond the minimum requirements?
        // - Does it handle additional inputs/outputs or provide extra functionality?
        // For demo: check for extra print statements or user interaction.
        if (code.split("System.out.println").length > 5 && code.contains("Scanner")) { // More output/input than simple problems
            return 3.0; // Attempts to expand interaction
        }
        if (code.contains("featureX") || code.contains("bonusFunction")) { // Placeholder for specific bonus features
            return 4.0;
        }
        return 1.0;
    }

    private double assessCodeOrganization(String code) {
        // TODO: Replace with actual analysis.
        // - Use of comments (meaningful, not just boilerplate).
        // - Consistent indentation and formatting (Checkstyle integration).
        // - Logical grouping of code (methods, classes).
        // - Meaningful variable/method names.
        // For demo: check for comments, blank lines, method separation.
        long commentLines = code.lines().filter(line -> line.trim().startsWith("//") || line.trim().startsWith("/*")).count();
        long blankLines = code.lines().filter(String::isBlank).count();
        double ratio = (double) commentLines / code.lines().count();

        if (ratio > 0.15 && blankLines > 5) {
            return 4.0; // Good comments and spacing
        }
        if (ratio > 0.05) {
            return 3.0; // Some comments
        }
        if (code.contains("{") && code.contains("}")) { // Basic structure
            return 2.0;
        }
        return 1.0;
    }

    private double assessAlternativeSolutions(String code) {
        // TODO: Replace with actual analysis.
        // - Evidence the student considered multiple approaches (e.g., commented out code, alternative algorithm choices).
        // - Can be hard to assess from a single submission. Might require reflection or version history.
        // For demo: check for commented out blocks that look like alternative logic.
        if (Pattern.compile("/\\*\\s*Alternative\\s*solution.*?\\*/", Pattern.DOTALL).matcher(code).find()) {
            return 3.0; // Explicitly shows an alternative
        }
        if (code.contains("if (condition1) { /* approach A */ } else { /* approach B */ }")) { // Suggests branching logic for alternatives
            return 2.0;
        }
        return 1.0;
    }


    // --- Feedback Generation ---

    private String generateFeedback(Map<String, Map<String, Double>> detailedScores) {
        StringBuilder feedback = new StringBuilder();

        feedback.append("Detailed Performance Feedback:\n\n");

        feedback.append("=== Problem-Solving ===\n");
        feedback.append("This section assesses your ability to identify, analyze, and implement solutions for programming challenges.\n\n");
        appendCategoryFeedback(feedback, "Problem-Solving", detailedScores.get("Problem-Solving"));
        feedback.append("\n");

        feedback.append("=== Critical Thinking ===\n");
        feedback.append("This section evaluates your logical reasoning, debugging skills, and code optimization.\n\n");
        appendCategoryFeedback(feedback, "Critical Thinking", detailedScores.get("Critical Thinking"));
        feedback.append("\n");

        feedback.append("=== Understanding Programming Logic ===\n");
        feedback.append("This section assesses your mastery of fundamental programming constructs and logical structures.\n\n");
        appendCategoryFeedback(feedback, "Programming Logic", detailedScores.get("Programming Logic"));
        feedback.append("\n");

        feedback.append("=== Creativity ===\n");
        feedback.append("This section assesses originality, innovative approaches, and the overall quality and uniqueness of your solution.\n\n");
        appendCategoryFeedback(feedback, "Creativity", detailedScores.get("Creativity"));

        return feedback.toString();
    }

    private void appendCategoryFeedback(StringBuilder feedback, String categoryName, Map<String, Double> categoryScores) {
        if (categoryScores == null) {
            feedback.append("No data available for this category.\n");
            return;
        }
        for (Map.Entry<String, Double> entry : categoryScores.entrySet()) {
            String criteria = entry.getKey();
            double score = entry.getValue(); // This is the actual mapped rubric score (0-4)

            feedback.append(String.format("  Criteria: %s | Score: %.1f/4.0\n", criteria, score));
            feedback.append("  Assessment: ").append(getAssessmentForScore(categoryName, criteria, score)).append("\n\n");
        }
    }

    /**
     * Provides a textual assessment for a given score within a specific criteria, strictly aligned with rubric levels.
     *
     * @param categoryName The name of the category.
     * @param criteria The name of the criteria.
     * @param score The actual rubric score for the criteria (expected to be 0-4).
     * @return A descriptive assessment string.
     */
    private String getAssessmentForScore(String categoryName, String criteria, double score) {
        // Round score to the nearest integer to match the level descriptors
        int level = (int) Math.round(score);
        level = Math.max(0, Math.min(4, level)); // Ensure level is within 0-4

        // These descriptions should map directly to your rubric's definitions for each level.
        // Example rubric levels:
        // Level 0 (Lacking): Did not meet expectations. Significant deficiencies.
        // Level 1 (Emerging): Demonstrates basic understanding but requires substantial improvement.
        // Level 2 (Proficient): Meets expectations, clear understanding and application.
        // Level 3 (Exceeding): Exceeds expectations, strong grasp, and effective, thoughtful application.
        // Level 4 (Exceptional): Outstanding performance, highly refined, innovative, or exemplary.

        String specificAssessment = null;

        // --- Problem-Solving ---
        if ("Problem-Solving".equals(categoryName)) {
            switch (criteria) {
                case "Problem Identification":
                    specificAssessment = switch (level) {
                        case 0 -> "Did not correctly identify or misinterpreted the core problem.";
                        case 1 -> "Identified basic elements of the problem but missed key aspects.";
                        case 2 -> "Fully identified the problem and understood its scope accurately.";
                        case 3 -> "Clearly identified and effectively broke down complex problems into manageable sub-problems.";
                        case 4 -> "Demonstrated exceptional insight into problem structure, anticipating potential complexities and nuances.";
                        default -> null;
                    };
                    break;
                case "Solution Implementation":
                    specificAssessment = switch (level) {
                        case 0 -> "No functional solution was attempted, or submitted code does not run.";
                        case 1 -> "Implemented a partial solution addressing only minimal requirements, with significant flaws.";
                        case 2 -> "Implemented a functional solution that meets all specified requirements correctly.";
                        case 3 -> "Implemented a robust and efficient solution, potentially with minor enhancements or improved clarity.";
                        case 4 -> "Developed an elegant, highly optimized, and potentially innovative solution, significantly exceeding expectations.";
                        default -> null;
                    };
                    break;
                case "Error Handling and Validation":
                    specificAssessment = switch (level) {
                        case 0 -> "No significant error handling or input validation present.";
                        case 1 -> "Minimal error handling; issues like invalid input or unexpected states are not robustly addressed.";
                        case 2 -> "Includes basic error handling and input validation for common scenarios.";
                        case 3 -> "Features comprehensive error handling and robust input validation, making the program resilient.";
                        case 4 -> "Exemplary error handling, anticipating rare edge cases and providing clear, informative messages.";
                        default -> null;
                    };
                    break;
                case "Handling Edge Cases":
                    specificAssessment = switch (level) {
                        case 0 -> "Fails on all or most edge cases; program may crash or produce incorrect output.";
                        case 1 -> "Addresses a few obvious edge cases but struggles with less common or complex ones.";
                        case 2 -> "Handles most standard edge cases correctly, such as empty inputs or boundary conditions.";
                        case 3 -> "Demonstrates a thorough consideration of various edge cases, including complex and unusual scenarios.";
                        case 4 -> "Exceptional handling of all anticipated and even some unanticipated edge cases, ensuring robust behavior.";
                        default -> null;
                    };
                    break;
                case "Testing the Solution":
                    specificAssessment = switch (level) {
                        case 0 -> "No evidence of testing the solution; functionality is unverified.";
                        case 1 -> "Includes very rudimentary manual tests or implicit testing from execution.";
                        case 2 -> "Provides basic test cases that cover typical scenarios, demonstrating some verification.";
                        case 3 -> "Features a reasonable set of test cases, including some edge cases, providing good coverage.";
                        case 4 -> "Comprehensive test suite (e.g., using JUnit), covering typical, edge, and potentially stress cases, ensuring high confidence in correctness.";
                        default -> null;
                    };
                    break;
            }
        }
        // --- Critical Thinking ---
        else if ("Critical Thinking".equals(categoryName)) {
            switch (criteria) {
                case "Debugging":
                    specificAssessment = switch (level) {
                        case 0 -> "Code contains obvious bugs; no systematic debugging approach is evident.";
                        case 1 -> "Attempts at debugging are visible (e.g., print statements), but issues persist or are difficult to resolve.";
                        case 2 -> "Demonstrates a basic ability to identify and fix bugs, often through trial and error.";
                        case 3 -> "Applies systematic debugging strategies, effectively isolating and resolving issues with logical steps.";
                        case 4 -> "Exceptional debugging skills, quickly identifying root causes and implementing elegant fixes; code is generally robust.";
                        default -> null;
                    };
                    break;
                case "Code Optimization":
                    specificAssessment = switch (level) {
                        case 0 -> "Code is highly inefficient; performance is severely impacted by poor design choices.";
                        case 1 -> "Minimal consideration for optimization; basic operations might be inefficient.";
                        case 2 -> "Shows awareness of performance; attempts some basic optimizations without sacrificing clarity.";
                        case 3 -> "Implements efficient algorithms and data structures where appropriate, optimizing for common scenarios.";
                        case 4 -> "Highly optimized solution, demonstrating deep understanding of performance trade-offs and algorithmic complexity.";
                        default -> null;
                    };
                    break;
                case "Evaluation of Logic":
                    specificAssessment = switch (level) {
                        case 0 -> "Logical flow is flawed or contradictory, leading to incorrect behavior.";
                        case 1 -> "Logic is partially sound but contains gaps or inconsistencies, leading to unpredictable results.";
                        case 2 -> "The underlying logic of the program is generally sound and correctly implemented.";
                        case 3 -> "Demonstrates strong logical reasoning, with a clear, well-structured, and verifiable flow of control.";
                        case 4 -> "Exceptional logical design; the solution is elegant, concise, and demonstrably correct under all conditions.";
                        default -> null;
                    };
                    break;
                case "Pattern Recognition":
                    specificAssessment = switch (level) {
                        case 0 -> "Repeated code blocks or redundant logic indicate a lack of pattern recognition.";
                        case 1 -> "Some patterns are recognized but not fully abstracted or generalized.";
                        case 2 -> "Identifies and abstracts common patterns into reusable functions or loops, improving code structure.";
                        case 3 -> "Consistently recognizes and effectively applies common design patterns or algorithmic structures.";
                        case 4 -> "Exemplary pattern recognition, abstracting complex behaviors into highly reusable and maintainable components.";
                        default -> null;
                    };
                    break;
                case "Conditional Thinking":
                    specificAssessment = switch (level) {
                        case 0 -> "Ineffective or incorrect use of conditionals; logic branches are not properly controlled.";
                        case 1 -> "Basic use of 'if' statements, but 'else' or complex conditions are absent or misused.";
                        case 2 -> "Appropriately uses 'if-else' structures to control program flow based on conditions.";
                        case 3 -> "Skilfully employs complex conditional logic (e.g., nested conditions, logical operators, switch statements) to handle diverse scenarios.";
                        case 4 -> "Masterful application of conditional logic, resulting in highly readable, efficient, and robust decision-making within the code.";
                        default -> null;
                    };
                    break;
            }
        }
        // --- Programming Logic ---
        else if ("Programming Logic".equals(categoryName)) {
            switch (criteria) {
                case "Use of Variables":
                    specificAssessment = switch (level) {
                        case 0 -> "Variables are undefined, misused, or lead to errors; poor naming conventions.";
                        case 1 -> "Variables are declared but may be poorly named, incorrectly scoped, or uninitialized.";
                        case 2 -> "Variables are generally used correctly, with appropriate types and basic naming conventions.";
                        case 3 -> "Variables are named meaningfully, correctly scoped, and consistently initialized, enhancing readability and maintainability.";
                        case 4 -> "Exceptional variable usage; employs advanced techniques (e.g., immutability, destructuring where applicable) for robust and clear state management.";
                        default -> null;
                    };
                    break;
                case "Use of Loops":
                    specificAssessment = switch (level) {
                        case 0 -> "Loops are absent when needed, or cause infinite loops/incorrect iterations.";
                        case 1 -> "Attempts to use loops are present, but with off-by-one errors or incorrect termination conditions.";
                        case 2 -> "Appropriately uses basic loop structures (for, while) to perform repetitive tasks.";
                        case 3 -> "Effectively employs various loop types (for, while, do-while, for-each) for different iterative needs, with correct termination.";
                        case 4 -> "Masterful use of loops, including complex iterations, nested loops, and stream-based operations where appropriate, for concise and efficient repetition.";
                        default -> null;
                    };
                    break;
                case "Use of Conditionals":
                    specificAssessment = switch (level) {
                        case 0 -> "Conditionals are absent when logic branches are needed, or are incorrectly formed.";
                        case 1 -> "Basic 'if' statements are present, but 'else' branches or complex logical conditions are often missed.";
                        case 2 -> "Correctly uses 'if-else' statements to implement decision-making logic.";
                        case 3 -> "Skilfully uses a range of conditional constructs (if-else if-else, switch, ternary operator) for clear and effective decision paths.";
                        case 4 -> "Exemplary use of conditional logic; code is highly readable, covers all logical paths efficiently, and avoids redundant checks.";
                        default -> null;
                    };
                    break;
                case "Procedures & Functions":
                    specificAssessment = switch (level) {
                        case 0 -> "Code is a monolithic block; no clear separation of concerns into functions/methods.";
                        case 1 -> "Some attempt to use functions, but they are poorly defined, excessively long, or have unclear responsibilities.";
                        case 2 -> "Breaks down code into functions/methods with clear responsibilities, improving modularity.";
                        case 3 -> "Designs functions/methods with high cohesion and low coupling, promoting reusability and maintainability.";
                        case 4 -> "Architects a highly modular solution with well-defined, testable functions/methods, demonstrating strong software engineering principles.";
                        default -> null;
                    };
                    break;
                case "Use of Operators & Expressions":
                    specificAssessment = switch (level) {
                        case 0 -> "Incorrect or missing operators lead to compilation errors or wrong computations.";
                        case 1 -> "Uses basic arithmetic and assignment operators; struggles with relational or logical operators.";
                        case 2 -> "Correctly uses a variety of arithmetic, relational, and logical operators in expressions.";
                        case 3 -> "Demonstrates a solid understanding of operator precedence and effectively constructs complex, correct expressions.";
                        case 4 -> "Masterful application of operators and expressions, leading to concise, efficient, and mathematically sound computations.";
                        default -> null;
                    };
                    break;
                case "Program Initialization":
                    specificAssessment = switch (level) {
                        case 0 -> "Critical variables or resources are not initialized, leading to runtime errors or undefined behavior.";
                        case 1 -> "Some initialization occurs, but it's inconsistent or misses key components.";
                        case 2 -> "Most necessary variables and components are properly initialized before use.";
                        case 3 -> "Ensures all variables and resources are systematically initialized, considering default states and potential external dependencies.";
                        case 4 -> "Exemplary initialization practices, including robust setup for complex objects and careful management of program state from start-up.";
                        default -> null;
                    };
                    break;
                case "Program Termination":
                    specificAssessment = switch (level) {
                        case 0 -> "Program does not terminate cleanly; may crash or run indefinitely (infinite loops).";
                        case 1 -> "Program terminates, but may leave resources open or exit abruptly without cleanup.";
                        case 2 -> "Program terminates as expected upon completion of its task.";
                        case 3 -> "Ensures graceful termination, closing resources (e.g., file streams) and providing appropriate exit codes or messages.";
                        case 4 -> "Demonstrates sophisticated termination handling, including comprehensive resource cleanup, error reporting, and controlled shutdown procedures.";
                        default -> null;
                    };
                    break;
            }
        }
        // --- Creativity ---
        else if ("Creativity".equals(categoryName)) {
            switch (criteria) {
                case "Originality":
                    specificAssessment = switch (level) {
                        case 0 -> "Solution is entirely conventional or directly copied, showing no original thought.";
                        case 1 -> "Solution is mostly conventional, with very minor unique elements or variations.";
                        case 2 -> "Introduces some original elements or a fresh perspective to a common problem.";
                        case 3 -> "Demonstrates a distinctly original approach or implements unique features not typically found in standard solutions.";
                        case 4 -> "Highly original and unique solution, showcasing exceptional creativity and independent thought, potentially inspiring new approaches.";
                        default -> null;
                    };
                    break;
                case "Innovative Problem Approach":
                    specificAssessment = switch (level) {
                        case 0 -> "Uses a standard, uninspired, or inefficient approach to the problem.";
                        case 1 -> "Attempts a slightly different approach, but it lacks innovation or significant benefit.";
                        case 2 -> "Adopts a non-obvious yet effective approach, showing a degree of innovative thinking.";
                        case 3 -> "Employs a genuinely innovative technique or algorithm that significantly improves the solution's elegance, efficiency, or scalability.";
                        case 4 -> "Presents a groundbreaking or highly inventive approach that redefines the problem's solution space, pushing boundaries.";
                        default -> null;
                    };
                    break;
                case "Creative Expansion of Problem":
                    specificAssessment = switch (level) {
                        case 0 -> "Does not address or expand upon the problem beyond minimum requirements.";
                        case 1 -> "Adds minor, superficial features that don't significantly enhance the solution or user experience.";
                        case 2 -> "Includes thoughtful extra features or functionalities that enhance the problem's scope or user interaction.";
                        case 3 -> "Significantly expands the problem's scope, adding valuable, well-implemented features that were not explicitly requested.";
                        case 4 -> "Transforms the problem into a richer, more comprehensive experience with unexpected, highly valuable, and well-integrated creative expansions.";
                        default -> null;
                    };
                    break;
                case "Code Organization and Clarity":
                    specificAssessment = switch (level) {
                        case 0 -> "Code is disorganized, unclear, and lacks comments, making it very difficult to understand.";
                        case 1 -> "Code has minimal organization or comments; difficult to follow without significant effort.";
                        case 2 -> "Code is reasonably organized with some comments and consistent formatting, making it generally understandable.";
                        case 3 -> "Well-organized code with meaningful comments, consistent style, and logical structure, enhancing readability and maintainability.";
                        case 4 -> "Exemplary code organization and clarity; self-documenting code with excellent structure, formatting, and insightful comments, setting a high standard.";
                        default -> null;
                    };
                    break;
                case "Exploration of Alternatives":
                    specificAssessment = switch (level) {
                        case 0 -> "Shows no evidence of considering alternative solutions or approaches.";
                        case 1 -> "Limited evidence of exploring alternatives; may mention but not implement or analyze them.";
                        case 2 -> "Demonstrates awareness of alternative solutions, potentially by implementing or discussing one other approach.";
                        case 3 -> "Explored and analyzed multiple viable alternative solutions, demonstrating a thoughtful selection process for the chosen approach.";
                        case 4 -> "Conducted a thorough exploration of diverse alternative solutions, providing clear justifications for the chosen path, reflecting deep analytical thought.";
                        default -> null;
                    };
                    break;
            }
        }

        // Fallback to generic assessment if no specific one is found for the criteria
        return specificAssessment != null ? specificAssessment :
                switch (level) {
                    case 0 -> "Lacking basic proficiency in this area.";
                    case 1 -> "Emerging proficiency, some understanding but needs improvement.";
                    case 2 -> "Proficient, demonstrates solid understanding and application.";
                    case 3 -> "Exceeding expectations, strong grasp and effective application.";
                    case 4 -> "Exceptional performance, innovative or highly refined.";
                    default -> "Invalid score level or no specific assessment available.";
                };
    }

    /**
     * Classifies the total score into a broad category.
     * @param score The total score (0-100).
     * @return A String classification (e.g., "Excellent", "Good", "Average", "Needs Improvement").
     */
    private String classifyScore(double score) {
        if (score >= 90) return ProficiencyLevel.ADVANCED.name(); // Using enum for consistency
        if (score >= 75) return ProficiencyLevel.INTERMEDIATE.name();
        if (score >= 50) return ProficiencyLevel.BEGINNER.name(); // Changed to Beginner for 50-74
        return "Needs Improvement"; // Below 50
    }


}