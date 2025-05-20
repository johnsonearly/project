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

    // QLearningAgent is NOT directly autowired here anymore.
    // The ExerciseServiceImpl will use the score from GradingResult
    // to update the QLearningAgent.

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
        String userId = submission.getUserId(); // Keep userId for logging if needed, but not for direct QL update

        String difficulty = submission.getDifficultyLevel();
        if (difficulty == null || difficulty.isBlank()) {
            difficulty = DEFAULT_DIFFICULTY;
            logger.warn("Difficulty level not provided for submission {}. Defaulting to '{}'. User: {}",
                    questionId, DEFAULT_DIFFICULTY, userId);
        }

        Map<String, Double> specificRubric = rubricLoader.loadRubric(questionId);
        Map<String, Double> generalRubric = rubricLoader.loadGeneralRubric();

        // Evaluate the submission based on rubrics
        GradingResult result = evaluateSubmission(code, questionId, difficulty, specificRubric, generalRubric);

        // For context, we can log the final score and classification.
        logger.info("Graded submission for user {}: Question ID '{}', Score: {:.2f}, Classification: {}",
                userId, questionId, result.getScore(), result.getClassification());

        // The ProficiencyTracker is now removed from here. Its logic, if still needed for *other* purposes,
        // should be separate or integrated directly into the QLearningAgent's state management.
        // QLearningAgent will be updated by ExerciseServiceImpl.recordExerciseResult.

        return result;
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

        // Evaluate each main category and get raw scores (0-4)
        Map<String, Double> problemSolvingRawScores = evaluateProblemSolving(code, questionId);
        Map<String, Double> criticalThinkingRawScores = evaluateCriticalThinking(code);
        Map<String, Double> programmingLogicRawScores = evaluateProgrammingLogic(code);
        Map<String, Double> creativityRawScores = evaluateCreativity(code, difficulty);

        // Map raw scores to actual rubric scores (0-4 for each sub-criteria) and calculate average for category
        double problemSolvingScore = calculateCategoryScore(problemSolvingRawScores, specificRubric, generalRubric, "Problem-Solving", detailedScores);
        double criticalThinkingScore = calculateCategoryScore(criticalThinkingRawScores, specificRubric, generalRubric, "Critical Thinking", detailedScores);
        double programmingLogicScore = calculateCategoryScore(programmingLogicRawScores, specificRubric, generalRubric, "Programming Logic", detailedScores);
        double creativityScore = calculateCategoryScore(creativityRawScores, specificRubric, generalRubric, "Creativity", detailedScores);

        // Calculate total score with weights
        // Weights can be adjusted. Creativity typically has less weight for foundational problems.
        // For Advanced difficulty, creativity could have more weight, as originally intended.
        boolean isAdvanced = "advanced".equalsIgnoreCase(difficulty);
        double totalScore = (problemSolvingScore * 0.25) + // Max 100 * 0.25 = 25
                (criticalThinkingScore * 0.25) +  // Max 100 * 0.25 = 25
                (programmingLogicScore * 0.40) +  // Max 100 * 0.40 = 40 (often most important)
                (creativityScore * (isAdvanced ? 0.10 : 0.05)); // Max 100 * 0.10 = 10 (Advanced) or 5 (Other)

        // Ensure total score is within 0-100 range.
        totalScore = Math.max(0, Math.min(100, totalScore));

        result.setScore(totalScore);
        result.setClassification(classifyScore(totalScore));
        result.setFeedback(generateFeedback(detailedScores));
        result.setDetailedScores(detailedScores);

        return result;
    }

    /**
     * Calculates the average score for a category and populates the detailedScores map.
     *
     * @param rawScores Raw assessment scores (e.0-4 for each sub-criteria).
     * @param specificRubric Question-specific rubric.
     * @param generalRubric General rubric.
     * @param categoryName The name of the category (e.g., "Problem-Solving").
     * @param detailedScores The map to populate with criterion-level scores.
     * @return The averaged score for the category (scaled to 0-100).
     */
    private double calculateCategoryScore(Map<String, Double> rawScores,
                                          Map<String, Double> specificRubric,
                                          Map<String, Double> generalRubric,
                                          String categoryName,
                                          Map<String, Map<String, Double>> detailedScores) {
        Map<String, Double> mappedScores = rawScores.entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        entry -> mapToRubricScore(entry.getValue(), entry.getKey(), specificRubric, generalRubric),
                        (oldValue, newValue) -> newValue, // Merge function for collect
                        LinkedHashMap::new // Maintain insertion order
                ));
        detailedScores.put(categoryName, mappedScores); // Store criterion-level scores

        // Calculate average of the mapped scores for the category
        double averageRawScore = mappedScores.values().stream()
                .mapToDouble(Double::doubleValue)
                .average()
                .orElse(0.0);

        // Scale the average score from a 0-4 range to a 0-100 range
        // If your rubric values are consistently 0-4, then averageRawScore will be 0-4.
        // Scale it: (averageRawScore / 4.0) * 100
        return (averageRawScore / 4.0) * 100;
    }


    // --- EVALUATION METHODS (Return raw scores, typically 0-4) ---

    // Note: These methods are simplified based on string matching.
    // A real system would use AST, test execution, etc.

    private Map<String, Double> evaluateProblemSolving(String code, String questionId) {
        Map<String, Double> scores = new LinkedHashMap<>(); // Use LinkedHashMap for predictable order

        // Scores are raw 0-4 based on internal assessment logic.
        scores.put("Problem Identification", identifiesProblemCorrectly(code, questionId));
        scores.put("Solution Implementation", isProblemSolved(code, questionId));
        scores.put("Error Handling and Validation", hasComprehensiveErrorHandling(code));
        scores.put("Handling Edge Cases", handlesEdgeCasesComprehensively(code, questionId));
        scores.put("Testing the Solution", includesTestCases(code));

        return scores;
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

    private Map<String, Double> evaluateCreativity(String code, String difficulty) {
        Map<String, Double> scores = new LinkedHashMap<>();

        scores.put("Originality", assessOriginality(code));
        scores.put("Innovative Problem Approach", assessInnovation(code, difficulty));
        scores.put("Creative Expansion of Problem", assessProblemExpansion(code));
        scores.put("Code Organization and Clarity", assessCodeOrganization(code));
        scores.put("Exploration of Alternatives", assessAlternativeSolutions(code));

        return scores;
    }

    // --- Feedback Generation ---

    private String generateFeedback(Map<String, Map<String, Double>> detailedScores) {
        StringBuilder feedback = new StringBuilder();

        // Problem-Solving Section
        feedback.append("=== Problem-Solving ===\n");
        feedback.append("Assessing the ability to identify, analyze, and implement solutions for programming challenges.\n\n");
        appendCategoryFeedback(feedback, detailedScores.get("Problem-Solving"));
        feedback.append("\n");

        // Critical Thinking Section
        feedback.append("=== Critical Thinking ===\n");
        feedback.append("Evaluating logical reasoning, debugging, and code optimization.\n\n");
        appendCategoryFeedback(feedback, detailedScores.get("Critical Thinking"));
        feedback.append("\n");

        // Programming Logic Section
        feedback.append("=== Understanding Programming Logic ===\n");
        feedback.append("Assessing mastery of programming constructs and logical structures.\n\n");
        appendCategoryFeedback(feedback, detailedScores.get("Programming Logic"));
        feedback.append("\n");

        // Creativity Section
        feedback.append("=== Creativity ===\n");
        feedback.append("Assessing originality, user-centered design, and innovative features.\n\n");
        appendCategoryFeedback(feedback, detailedScores.get("Creativity"));

        return feedback.toString();
    }

    private void appendCategoryFeedback(StringBuilder feedback, Map<String, Double> categoryScores) {
        if (categoryScores == null) {
            feedback.append("No data available for this category.\n");
            return;
        }
        for (Map.Entry<String, Double> entry : categoryScores.entrySet()) {
            String criteria = entry.getKey();
            double score = entry.getValue(); // This score is already mapped from rubric (0-4)

            feedback.append(String.format("Criteria: %s | Score: %.1f/4.0\n", criteria, score));
            feedback.append("Assessment: ").append(getAssessmentForScore(score, criteria)).append("\n\n");
        }
    }

    /**
     * Provides a textual assessment for a given score within a specific criteria.
     * This needs to be carefully aligned with your rubric levels (e.g., 0=Lacking, 1=Emerging, 2=Proficient, 3=Exceeding, 4=Exceptional).
     *
     * @param score The score for the criteria (expected to be 0-4).
     * @param criteria The name of the criteria.
     * @return A descriptive assessment string.
     */
    private String getAssessmentForScore(double score, String criteria) {
        // Round score to nearest integer to match rubric levels
        int level = (int) Math.round(score);

        // Generic descriptions based on levels 0-4
        String genericAssessment = switch (level) {
            case 0 -> "Lacking basic proficiency in this area.";
            case 1 -> "Emerging proficiency, some understanding but needs improvement.";
            case 2 -> "Proficient, demonstrates solid understanding and application.";
            case 3 -> "Exceeding expectations, strong grasp and effective application.";
            case 4 -> "Exceptional performance, innovative or highly refined.";
            default -> "Invalid score level."; // Should not happen with score capping
        };

        // You can add more specific feedback per criteria here if needed,
        // but the generic one covers a lot and keeps the method cleaner.
        // For example, for "Problem Identification":
        if ("Problem Identification".equals(criteria)) {
            return switch (level) {
                case 0 -> "Did not correctly identify the core problem or misinterpreted it.";
                case 1 -> "Identified basic elements of the problem but missed key aspects.";
                case 2 -> "Fully identified the problem and understood its scope.";
                case 3 -> "Clearly identified and broke down complex problems into manageable sub-problems.";
                case 4 -> "Demonstrated exceptional insight into problem structure, anticipating complexities.";
                default -> genericAssessment;
            };
        } else if ("Solution Implementation".equals(criteria)) {
            return switch (level) {
                case 0 -> "No functional solution attempted or submitted code does not run.";
                case 1 -> "Implemented a partial solution that addresses some basic requirements.";
                case 2 -> "Implemented a functional solution that meets all specified requirements.";
                case 3 -> "Implemented a robust and efficient solution, potentially with some minor enhancements.";
                case 4 -> "Developed an elegant, highly optimized, and potentially innovative solution, exceeding expectations.";
                default -> genericAssessment;
            };
        }
        // ... add more specific cases for other criteria as desired ...

        return genericAssessment; // Fallback to generic if no specific feedback
    }

    /**
     * Classifies the total score into a broad category.
     * @param score The total score (0-100).
     * @return A String classification (e.g., "Excellent", "Good", "Average", "Needs Improvement").
     */
    private String classifyScore(double score) {
        if (score >= 90) return "Excellent";
        if (score >= 75) return "Good"; // Slightly adjusted threshold for 'Good'
        if (score >= 50) return "Average";
        return "Needs Improvement";
    }

    /**
     * Maps a raw assessment level (e.g., 0-4 from evaluate methods) to a rubric score.
     * It first tries to find a specific rubric score for the criteria and level,
     * then a general one, falling back to the raw assessed level if no rubric entry exists.
     *
     * @param assessedLevel The raw level assessed by the internal methods (e.g., 0.0 to 4.0).
     * @param criteria The name of the criteria (e.g., "Debugging").
     * @param specificRubric Question-specific rubric.
     * @param generalRubric General rubric.
     * @return The mapped rubric score (expected 0.0 to 4.0).
     */
    private double mapToRubricScore(double assessedLevel, String criteria,
                                    Map<String, Double> specificRubric,
                                    Map<String, Double> generalRubric) {
        // Round the assessedLevel to the nearest integer to match rubric keys (e.g., "Debugging_3")
        int roundedLevel = (int) Math.round(assessedLevel);
        String rubricKey = criteria.replace(" ", "_") + "_" + roundedLevel; // Sanitize key for matching

        Double score = specificRubric.get(rubricKey);
        if (score == null) {
            score = generalRubric.get(rubricKey);
        }
        // Fallback: if no rubric entry, return the assessed level itself.
        // This means the assess methods are effectively defining the scores if no rubric override exists.
        return score != null ? score : assessedLevel;
    }

    // --- SIMPLISTIC CODE ANALYSIS METHODS (FOR DEMO PURPOSES ONLY) ---
    // These methods should be replaced with robust static/dynamic analysis tools in a real application.

    // Problem-Solving
    private double identifiesProblemCorrectly(String code, String questionId) {
        // Placeholder: Needs actual problem parsing or test case evaluation
        // A real system would use test cases to verify problem understanding.
        switch(questionId) {
            case "1": // Example: Count vowels problem
                return (code.contains("count") && (code.contains("vowel") || code.contains("aeiou") || code.contains("char"))) ? 4.0 : 1.0;
            case "2": // Example: Palindrome check
                return (code.contains("reverse") || code.contains("equalsIgnoreCase") || code.contains("isPalindrome")) ? 4.0 : 1.0;
            default:
                // For generic problems, check if it looks like a solution attempt
                return code.length() > 50 && (code.contains("main") || code.contains("public class")) ? 2.0 : 0.0;
        }
    }

    private double hasComprehensiveErrorHandling(String code) {
        // Check for common Java error handling constructs
        int indicators = 0;
        if (code.contains("try") && code.contains("catch")) indicators++;
        if (code.contains("throws")) indicators++;
        if (code.contains("if") && (code.contains("null") || code.contains("isEmpty()") || code.contains("isValid"))) indicators++; // Basic input validation checks

        if (indicators >= 3) return 4.0; // Excellent
        if (indicators == 2) return 3.0; // Good
        if (indicators == 1) return 2.0; // Basic
        return 1.0; // Lacking
    }

    private double handlesEdgeCasesComprehensively(String code, String questionId) {
        // This is highly dependent on the question.
        // For demo, very basic checks.
        int indicators = 0;
        switch(questionId) {
            case "1": // Vowel count: empty string, string with no vowels, string with only caps
                if (code.contains("toLowerCase") || code.contains("Character.toLowerCase")) indicators++;
                if (code.contains("isEmpty") || code.contains("length() == 0")) indicators++;
                break;
            case "2": // Palindrome: empty string, single char, string with spaces/punctuation
                if (code.contains("trim()") || code.contains("replaceAll")) indicators++;
                if (code.contains("length() <= 1") || code.contains("equals(\"\")")) indicators++;
                break;
            default: // Generic checks for edge cases
                if (code.contains("if (") && code.contains("== 0")) indicators++; // Check for zero
                if (code.contains("if (") && code.contains("null")) indicators++;   // Check for null
                if (code.contains("if (") && code.contains(".length() <")) indicators++; // Check for empty/min length
                break;
        }
        if (indicators >= 2) return 4.0;
        if (indicators == 1) return 2.0;
        return 1.0;
    }

    private double includesTestCases(String code) {
        int indicators = 0;
        if (code.contains("main(String[] args)") && (code.contains("System.out.println") || code.contains("assert"))) indicators++;
        if (code.contains("@Test") || code.contains("JUnit") || code.contains("org.junit")) indicators+=2; // For proper testing frameworks
        if (Pattern.compile("//\\s*Test\\s+case", Pattern.CASE_INSENSITIVE).matcher(code).find()) indicators++; // Comments indicating test cases

        if (indicators >= 3) return 4.0;
        if (indicators == 2) return 3.0;
        if (indicators == 1) return 2.0;
        return 1.0;
    }

    private double isProblemSolved(String code, String questionId) {
        // This method implies the solution is "correct". In a real system,
        // this would involve running hidden test cases and asserting outputs.
        // For this demo, it's a very weak indicator.
        // You would typically call an external grading tool here.
        switch(questionId) {
            case "1": // Vowel count
                // Requires a more robust check like actual execution
                return (code.contains("for") && code.contains("char") && code.contains("count")) ? 3.0 : 1.0;
            case "2": // Palindrome
                // Requires a more robust check like actual execution
                return (code.contains("for") && code.contains("charAt") && code.contains("==")) ? 3.0 : 1.0;
            default:
                // Assume it's solved if it compiles and has some structure
                return (code.contains("public class") && code.contains("main")) ? 2.0 : 0.0;
        }
    }


    // Critical Thinking
    private double assessDebuggingQuality(String code) {
        int indicators = 0;
        if (code.contains("System.out.println") || code.contains("logger.")) indicators++; // Basic logging/printing
        if (code.contains("// Debug: ") || code.contains("/* Debugging section */")) indicators++; // Explicit debugging comments
        if (code.contains("try") && code.contains("catch") && code.contains("Exception")) indicators++; // General exception handling as a debugging measure

        if (indicators >= 2) return 3.0; // Good
        if (indicators == 1) return 2.0; // Basic
        return 1.0; // Lacking
    }

    private double assessOptimizationLevel(String code) {
        int indicators = 0;
        if (code.contains(".stream()") && code.contains(".collect(")) indicators++; // Use of Streams
        if (code.contains("StringBuilder") || code.contains("StringBuffer")) indicators++; // Efficient string manipulation
        if (Pattern.compile("for\\s*\\(.*\\)\\s*\\{.*for\\s*\\(.*", Pattern.DOTALL).matcher(code).find()) {
            // Penalize overly nested loops unless justified (hard to tell with regex)
            // This is a very crude proxy. A real check would analyze algorithm complexity.
            // For now, let's look for common optimizations
        }
        if (code.contains("HashMap") || code.contains("HashSet")) indicators++; // Use of efficient data structures

        if (indicators >= 2) return 3.0;
        if (indicators == 1) return 2.0;
        return 1.0;
    }

    private double assessLogicEvaluation(String code) {
        int indicators = 0;
        if (code.contains("// Logic Explanation:") || code.contains("// Why this approach:")) indicators++; // Comments explaining logic
        if (code.contains("switch") && code.contains("case")) indicators++; // Alternative to if-else chains
        if (code.contains("enum")) indicators++; // Structured logic with enums

        if (indicators >= 2) return 3.0;
        if (indicators == 1) return 2.0;
        return 1.0;
    }

    private double assessPatternRecognition(String code) {
        int indicators = 0;
        if (code.contains("for (") && code.contains(":") && code.contains("List<")) indicators++; // Enhanced for-loop with List
        if (code.contains("Map<") && code.contains("put") && code.contains("get")) indicators++; // Common Map pattern
        if (code.contains("interface") || code.contains("abstract class")) indicators++; // Design patterns indicators

        if (indicators >= 2) return 3.0;
        if (indicators == 1) return 2.0;
        return 1.0;
    }

    private double assessConditionalThinking(String code) {
        int indicators = 0;
        if (code.contains("if") || code.contains("else")) indicators++;
        if (code.contains("&&") || code.contains("||")) indicators++; // Complex conditions
        if (code.contains("? :")) indicators++; // Ternary operator

        if (indicators >= 2) return 3.0;
        if (indicators == 1) return 2.0;
        return 1.0;
    }


    // Programming Logic
    private double assessVariableUsage(String code) {
        int indicators = 0;
        if (code.contains("int ") || code.contains("String ") || code.contains("boolean ")) indicators++; // Basic types
        if (code.contains("final ")) indicators++; // Immutability
        if (code.contains("static ")) indicators++; // Class vs instance

        if (indicators >= 2) return 3.0;
        if (indicators == 1) return 2.0;
        return 1.0;
    }

    private double assessLoopUsage(String code) {
        int indicators = 0;
        if (code.contains("for (") || code.contains("while (")) indicators++;
        if (code.contains("break") || code.contains("continue")) indicators++; // Control flow
        if (code.contains(".forEach(")) indicators++; // Functional loops

        if (indicators >= 2) return 3.0;
        if (indicators == 1) return 2.0;
        return 1.0;
    }

    private double assessConditionalUsage(String code) {
        int indicators = 0;
        if (code.contains("if (") || code.contains("switch (")) indicators++;
        if (code.contains("else if")) indicators++; // Chained conditions
        if (code.contains("default:")) indicators++; // Switch default

        if (indicators >= 2) return 3.0;
        if (indicators == 1) return 2.0;
        return 1.0;
    }

    private double assessFunctionUsage(String code) {
        int indicators = 0;
        if (code.contains("public static void main")) indicators++; // Main method
        if (Pattern.compile("\\b(public|private|protected)\\s+\\w+\\s+\\w+\\s*\\(", Pattern.DOTALL).matcher(code).find()) indicators++; // Custom methods
        if (code.contains("return ")) indicators++; // Return values
        if (code.contains("void ")) indicators++; // Procedures

        if (indicators >= 3) return 4.0;
        if (indicators == 2) return 3.0;
        if (indicators == 1) return 2.0;
        return 1.0;
    }

    private double assessOperatorUsage(String code) {
        int indicators = 0;
        if (code.contains("+") || code.contains("-") || code.contains("*") || code.contains("/")) indicators++; // Arithmetic
        if (code.contains("&&") || code.contains("||")) indicators++; // Logical
        if (code.contains("==") || code.contains("!=")) indicators++; // Comparison
        if (code.contains("++") || code.contains("--")) indicators++; // Unary/Increment

        if (indicators >= 3) return 4.0;
        if (indicators == 2) return 3.0;
        if (indicators == 1) return 2.0;
        return 1.0;
    }

    private double assessInitialization(String code) {
        int indicators = 0;
        if (code.contains("int ") && code.contains(" = ")) indicators++; // Primitive initialization
        if (code.contains("new ") && (code.contains("(") || code.contains("["))) indicators++; // Object/array instantiation
        if (code.contains("= null")) indicators++; // Explicit null assignment

        if (indicators >= 2) return 3.0;
        if (indicators == 1) return 2.0;
        return 1.0;
    }

    private double assessTermination(String code) {
        int indicators = 0;
        if (code.contains("return;")) indicators++; // Method return
        if (code.contains("System.exit(0)")) indicators++; // Explicit program exit
        if (code.contains("throw new")) indicators++; // Exception for termination

        if (indicators >= 2) return 3.0;
        if (indicators == 1) return 2.0;
        return 1.0;
    }


    // Creativity
    private double assessOriginality(String code) {
        int indicators = 0;
        // Looking for less common patterns, custom utility methods
        if (code.contains("private static") && Pattern.compile("public\\s+class\\s+\\w+\\s*\\{[^}]*private\\s+static\\s+\\w+\\s+\\w+\\s*\\(", Pattern.DOTALL).matcher(code).find()) indicators++; // Custom helper methods
        if (code.contains("Comparator") || code.contains("Comparable")) indicators++; // Custom sorting
        if (code.contains("// Unique approach") || code.contains("// My custom solution")) indicators++; // Self-declared originality

        if (indicators >= 2) return 3.0;
        if (indicators == 1) return 2.0;
        return 1.0;
    }

    private double assessInnovation(String code, String difficulty) {
        int indicators = 0;
        if (code.contains("design pattern")) indicators++; // Mention of design patterns
        if (code.contains("@Override") || code.contains("implements")) indicators++; // Polymorphism, interfaces
        if ("Advanced".equalsIgnoreCase(difficulty) && (code.contains("lambda") || code.contains("CompletableFuture"))) indicators++; // Advanced Java features

        if (indicators >= 2) return 3.0;
        if (indicators == 1) return 2.0;
        return 1.0;
    }

    private double assessProblemExpansion(String code) {
        int indicators = 0;
        if (code.contains("// Extension:") || code.contains("// Future features:")) indicators++; // Comments about extensions
        if (code.contains("Scanner") && code.contains("System.in")) indicators++; // Basic interactive input (beyond strict problem scope)
        if (code.contains("File") || code.contains("IO")) indicators++; // File I/O for more complex interaction

        if (indicators >= 2) return 3.0;
        if (indicators == 1) return 2.0;
        return 1.0;
    }

    private double assessCodeOrganization(String code) {
        int indicators = 0;
        if (code.contains("package ")) indicators++; // Package declaration
        if (code.contains("import ")) indicators++; // Import statements
        if (code.matches("(?s).*\\n\\s{4}\\w+.*")) indicators++; // Basic indentation (4 spaces)
        if (code.contains("/*") && code.contains("*/")) indicators++; // Multi-line comments for sections
        if (code.contains("//")) indicators++; // Single-line comments

        if (indicators >= 3) return 4.0;
        if (indicators == 2) return 3.0;
        if (indicators == 1) return 2.0;
        return 1.0;
    }

    private double assessAlternativeSolutions(String code) {
        int indicators = 0;
        if (Pattern.compile("//\\s*Alternative\\s+solution", Pattern.CASE_INSENSITIVE).matcher(code).find()) indicators++; // Commented alternatives
        if (code.contains("if-else if") && code.contains("switch")) indicators++; // Using different conditional structures for same logic
        if (code.contains("List") && code.contains("Set")) indicators++; // Demonstrating knowledge of different data structures for a similar problem

        if (indicators >= 2) return 3.0;
        if (indicators == 1) return 2.0;
        return 1.0;
    }
}