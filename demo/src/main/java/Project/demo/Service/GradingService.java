package Project.demo.Service;

import Project.demo.Component.ProficiencyTracker;
import Project.demo.Component.QLearningAgent;
import Project.demo.Component.RubricLoader;
import Project.demo.DTOs.CodeSubmission;
import Project.demo.DTOs.GradingResult;
import Project.demo.Enums.ProficiencyLevel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

@Service
public class GradingService {

    @Autowired
    private RubricLoader rubricLoader;

    @Autowired
    private QLearningAgent qLearningAgent;

    public GradingResult grade(CodeSubmission submission) {
        ProficiencyTracker proficiencyTracker = new ProficiencyTracker();
        String questionId = submission.getQuestionId();
        String code = submission.getCode();
        String userId = submission.getUserId();

        String difficulty = submission.getDifficultyLevel();
        if (difficulty == null || difficulty.isBlank()) {
            difficulty = "Beginner";
            System.out.println("Warning: difficulty level not provided. Defaulting to 'Beginner'.");
        }

        Map<String, Double> specificRubric = rubricLoader.loadRubric(questionId);
        Map<String, Double> generalRubric = rubricLoader.loadGeneralRubric();

        GradingResult result = evaluateSubmission(code, questionId, difficulty, specificRubric, generalRubric);



        proficiencyTracker.recordScore(userId, result.getScore());

        if (proficiencyTracker.getQuestionCount(userId) >= proficiencyTracker.getWindowSize()) {
            String userLevel = proficiencyTracker.determineUserLevel(userId);
            result.setUserProficiencyLevel(ProficiencyLevel.valueOf(userLevel));
            System.out.println("User " + userId + " classified as: " + userLevel);
        }

        return result;
    }

    private GradingResult evaluateSubmission(String code, String questionId, String difficulty,
                                             Map<String, Double> specificRubric,
                                             Map<String, Double> generalRubric) {
        GradingResult result = new GradingResult();

        // Detailed scoring for each category
        Map<String, Map<String, Double>> detailedScores = new LinkedHashMap<>();

        // Problem Solving
        Map<String, Double> problemSolvingScores = evaluateProblemSolving(code, questionId, specificRubric, generalRubric);
        detailedScores.put("Problem-Solving", problemSolvingScores);
        double problemSolvingScore = problemSolvingScores.values().stream().mapToDouble(Double::doubleValue).average().orElse(0);

        // Critical Thinking
        Map<String, Double> criticalThinkingScores = evaluateCriticalThinking(code, specificRubric, generalRubric);
        detailedScores.put("Critical Thinking", criticalThinkingScores);
        double criticalThinkingScore = criticalThinkingScores.values().stream().mapToDouble(Double::doubleValue).average().orElse(0);

        // Programming Logic
        Map<String, Double> programmingLogicScores = evaluateProgrammingLogic(code, specificRubric, generalRubric);
        detailedScores.put("Programming Logic", programmingLogicScores);
        double programmingLogicScore = programmingLogicScores.values().stream().mapToDouble(Double::doubleValue).average().orElse(0);

        // Creativity
        Map<String, Double> creativityScores = evaluateCreativity(code, difficulty, specificRubric, generalRubric);
        detailedScores.put("Creativity", creativityScores);
        double creativityScore = creativityScores.values().stream().mapToDouble(Double::doubleValue).average().orElse(0);

        // Calculate total score with weights
        boolean isAdvanced = "advanced".equalsIgnoreCase(difficulty);
        double totalScore = problemSolvingScore * 0.3 +
                criticalThinkingScore * 0.3 +
                programmingLogicScore * 0.3 +
                creativityScore * (isAdvanced ? 0.2 : 0.1);
        totalScore = Math.min(100, totalScore * 2);

        result.setScore(totalScore);
        result.setClassification(classifyScore(totalScore));
        result.setFeedback(generateFeedback(detailedScores));
        result.setDetailedScores(detailedScores); // Store detailed scores in the result

        return result;
    }

    private Map<String, Double> evaluateProblemSolving(String code, String questionId,
                                                       Map<String, Double> specificRubric,
                                                       Map<String, Double> generalRubric) {
        Map<String, Double> scores = new LinkedHashMap<>();

        // Problem Identification
        double problemIdentificationScore = identifiesProblemCorrectly(code, questionId) ?
                specificRubric.getOrDefault("Problem Identification",
                        generalRubric.getOrDefault("Problem Identification_3", 2.0)) : 0;
        scores.put("Problem Identification", problemIdentificationScore);

        // Solution Implementation
        double solutionImplementationScore = isProblemSolved(code, questionId) ?
                specificRubric.getOrDefault("Solution Implementation",
                        generalRubric.getOrDefault("Solution Implementation_3", 2.0)) : 0;
        scores.put("Solution Implementation", solutionImplementationScore);

        // Error Handling and Validation
        double errorHandlingScore = hasComprehensiveErrorHandling(code) ?
                specificRubric.getOrDefault("Error Handling and Validation",
                        generalRubric.getOrDefault("Error Handling and Validation_3", 2.0)) : 0;
        scores.put("Error Handling and Validation", errorHandlingScore);

        // Handling Edge Cases
        double edgeCaseScore = handlesEdgeCasesComprehensively(code, questionId) ?
                specificRubric.getOrDefault("Handling Edge Cases",
                        generalRubric.getOrDefault("Handling Edge Cases_3", 2.0)) : 0;
        scores.put("Handling Edge Cases", edgeCaseScore);

        // Solution Testing
        double testingScore = includesTestCases(code) ?
                specificRubric.getOrDefault("Testing the Solution",
                        generalRubric.getOrDefault("Testing the Solution_3", 2.0)) : 0;
        scores.put("Testing the Solution", testingScore);

        return scores;
    }

    private Map<String, Double> evaluateCriticalThinking(String code,
                                                         Map<String, Double> specificRubric,
                                                         Map<String, Double> generalRubric) {
        Map<String, Double> scores = new LinkedHashMap<>();

        scores.put("Debugging", assessDebuggingQuality(code));
        scores.put("Code Optimization", assessOptimizationLevel(code));
        scores.put("Evaluation of Logic", assessLogicEvaluation(code));
        scores.put("Pattern Recognition", assessPatternRecognition(code));
        scores.put("Conditional Thinking", assessConditionalThinking(code));

        // Map raw assessment scores to rubric scores
        scores.replaceAll((k, v) -> mapToRubricScore(v, k, specificRubric, generalRubric));

        return scores;
    }

    private Map<String, Double> evaluateProgrammingLogic(String code,
                                                         Map<String, Double> specificRubric,
                                                         Map<String, Double> generalRubric) {
        Map<String, Double> scores = new LinkedHashMap<>();

        scores.put("Use of Variables", assessVariableUsage(code));
        scores.put("Use of Loops", assessLoopUsage(code));
        scores.put("Use of Conditionals", assessConditionalUsage(code));
        scores.put("Procedures & Functions", assessFunctionUsage(code));
        scores.put("Use of Operators & Expressions", assessOperatorUsage(code));
        scores.put("Program Initialization", assessInitialization(code));
        scores.put("Program Termination", assessTermination(code));

        // Map raw assessment scores to rubric scores
        scores.replaceAll((k, v) -> mapToRubricScore(v, k, specificRubric, generalRubric));

        return scores;
    }

    private Map<String, Double> evaluateCreativity(String code, String difficulty,
                                                   Map<String, Double> specificRubric,
                                                   Map<String, Double> generalRubric) {
        Map<String, Double> scores = new LinkedHashMap<>();

        scores.put("Originality", assessOriginality(code));
        scores.put("Innovative Problem Approach", assessInnovation(code, difficulty));
        scores.put("Creative Expansion of Problem", assessProblemExpansion(code));
        scores.put("Code Organization and Clarity", assessCodeOrganization(code));
        scores.put("Exploration of Alternatives", assessAlternativeSolutions(code));

        // Map raw assessment scores to rubric scores
        scores.replaceAll((k, v) -> mapToRubricScore(v, k, specificRubric, generalRubric));

        return scores;
    }

    private String generateFeedback(Map<String, Map<String, Double>> detailedScores) {
        StringBuilder feedback = new StringBuilder();

        // Problem-Solving Section
        feedback.append("=== Problem-Solving ===\n");
        feedback.append("(Assessing the ability to identify, analyze, and implement solutions for programming challenges)\n\n");
        appendCategoryFeedback(feedback, detailedScores.get("Problem-Solving"));
        feedback.append("\n");

        // Critical Thinking Section
        feedback.append("=== Critical Thinking ===\n");
        feedback.append("(Evaluating logical reasoning, debugging, and code optimization.)\n\n");
        appendCategoryFeedback(feedback, detailedScores.get("Critical Thinking"));
        feedback.append("\n");

        // Programming Logic Section
        feedback.append("=== Understanding Programming Logic ===\n");
        feedback.append("(Assessing mastery of programming constructs and logical structures.)\n\n");
        appendCategoryFeedback(feedback, detailedScores.get("Programming Logic"));
        feedback.append("\n");

        // Creativity Section
        feedback.append("=== Creativity ===\n");
        feedback.append("(Assessing originality, user-centered design, and innovative features.)\n\n");
        appendCategoryFeedback(feedback, detailedScores.get("Creativity"));

        return feedback.toString();
    }

    private void appendCategoryFeedback(StringBuilder feedback, Map<String, Double> categoryScores) {
        for (Map.Entry<String, Double> entry : categoryScores.entrySet()) {
            String criteria = entry.getKey();
            double score = entry.getValue();

            feedback.append(String.format("Criteria: %s | Score: %.1f/4.0\n", criteria, score));
            feedback.append("Assessment: ").append(getAssessmentForScore(score, criteria)).append("\n\n");
        }
    }

    private String getAssessmentForScore(double score, String criteria) {
        // Return appropriate assessment based on score and criteria
        int level = (int) Math.round(score);

        switch (criteria) {
            case "Problem Identification":
                return switch (level) {
                    case 0 -> "Does not attempt to identify or misinterprets the problem";
                    case 1 -> "Identifies basic elements of the problem but lacks clarity";
                    case 2 -> "Fully identifies the problem and understands its scope";
                    case 3 -> "Breaks down complex problems into smaller sub-problems for structured resolution";
                    default -> "Analyzes and breaks down complex problems into solvable components";
                };
            case "Solution Implementation":
                return switch (level) {
                    case 0 -> "No working solution attempted";
                    case 1 -> "Implements a basic solution that partially meets requirements";
                    case 2 -> "Implements a functional solution addressing all requirements";
                    case 3 -> "Implements a robust, optimized solution with additional features beyond requirements";
                    default -> "Creates innovative, optimized solutions beyond the problem's scope";
                };
            // Add similar cases for all criteria...
            default:
                return switch (level) {
                    case 0 -> "Lacking basic proficiency";
                    case 1 -> "Emerging proficiency";
                    case 2 -> "Proficient";
                    case 3 -> "Exceeding grade-level proficiency";
                    default -> "Exceptional performance beyond expectations";
                };
        }
    }





    private String classifyScore(double score) {
        if (score >= 90) return "Excellent";
        if (score >= 70) return "Good";
        if (score >= 50) return "Average";
        return "Needs Improvement";
    }

    private String classificationToState(String classification) {
        return switch (classification) {
            case "Excellent" -> "Advanced";
            case "Good" -> "Intermediate";
            default -> "Beginner";
        };
    }

    private double mapToRubricScore(double assessedLevel, String criteria,
                                    Map<String, Double> specificRubric,
                                    Map<String, Double> generalRubric) {
        String rubricKey = criteria + "_" + (int)assessedLevel;
        Double score = specificRubric.get(rubricKey);
        if (score == null) score = generalRubric.get(rubricKey);
        return score != null ? score : assessedLevel;
    }
    private double assessOriginality(String code) {
        // Check for:
        // 1. Presence of boilerplate code
        // 2. Custom implementations vs standard solutions
        // 3. Unique variable/method naming
        // 4. Presence of creative comments or documentation

        double originalityScore = 0;

        // Basic check for copied code markers
        if (code.contains("// copied from") || code.matches(".*//\\s*taken from.*")) {
            return 0; // Lacking Proficiency
        }

        // Check for standard solution patterns
        if (code.matches(".*public\\s+class\\s+Solution\\s*\\{.*")) {
            originalityScore += 1; // Emerging
        }

        // Check for custom method names
        if (code.matches(".*\\b(custom|my|original)\\w+\\s*\\(.*")) {
            originalityScore += 1; // Developing
        }

        // Check for innovative approaches
        if (code.contains("Optional") || code.contains("Function<") || code.contains("custom collector")) {
            originalityScore += 2; // Proficient/Exceeding
        }

        return Math.min(4, originalityScore);
    }

    private double assessCodeOrganization(String code) {
        // Evaluate:
        // 1. Proper indentation
        // 2. Section comments
        // 3. Method organization
        // 4. File structure

        double organizationScore = 0;

        // Basic indentation check
        if (code.matches("(?s).*\\n\\s{4}\\w+.*")) { // At least some indentation
            organizationScore += 1;
        }

        // Check for section comments
        if (code.contains("// Section:") || code.contains("/* Main logic */")) {
            organizationScore += 1;
        }

        // Check for method separation
        long methodCount = code.split("public\\s+\\w+\\s+\\w+\\s*\\(").length - 1;
        if (methodCount > 1) {
            organizationScore += 1;
        }

        // Check for advanced organization
        if (code.contains("@Override") || code.contains("implements") || code.contains("package ")) {
            organizationScore += 1;
        }

        return Math.min(4, organizationScore);
    }
    private double assessDebuggingQuality(String code) {
        double score = 0;

        // Basic syntax error handling
        if (code.contains("try") || code.contains("catch")) score += 1;

        // Logical error handling
        if (code.contains("log") || code.contains("print") || code.contains("debug")) score += 1;

        // Systematic debugging
        if (code.matches(".*//\\s*TODO:\\s*fix.*") ||
                code.matches(".*//\\s*FIXME.*")) {
            score += 0.5; // Negative points for unresolved issues
        }

        // Advanced debugging
        if (code.contains("logger") || code.contains("Debugger") ||
                code.contains("breakpoint")) {
            score += 2;
        }

        return Math.min(4, score);
    }

    private double assessOptimizationLevel(String code) {
        double score = 0;

        // Basic optimization
        if (!code.contains("for (int i = 0")) score += 1;

        // Use of efficient constructs
        if (code.contains("stream()") || code.contains("mapToObj") ||
                code.contains("collect(Collectors")) {
            score += 1;
        }

        // Algorithmic optimization
        if (code.contains("O(1)") || code.contains("O(n)")) score += 1;
        if (code.contains("O(log n)") || code.contains("O(n log n)")) score += 2;

        // Memory optimization
        if (code.contains("new ArrayList") && code.contains("initialCapacity")) score += 1;

        return Math.min(4, score);
    }

    private double assessLogicEvaluation(String code) {
        double score = 0;

        // Basic logic evaluation
        if (code.contains("// This works because") ||
                code.contains("// Logic explanation")) {
            score += 1;
        }

        // Intermediate evaluation
        if (code.matches(".*//\\s*Alternative approach.*") ||
                code.matches(".*//\\s*Considered.*but.*")) {
            score += 2;
        }

        // Advanced evaluation
        if (code.contains("// Benchmark results") ||
                code.contains("// Compared with")) {
            score += 3;
        }

        return Math.min(4, score);
    }

    private double assessPatternRecognition(String code) {
        double score = 0;

        // Basic pattern recognition
        if (code.contains("for") && code.contains("if")) score += 1;

        // Intermediate patterns
        if (code.contains("Map<") || code.contains("List<")) score += 1;
        if (code.contains("Function<") || code.contains("Predicate<")) score += 2;

        // Advanced patterns
        if (code.contains("Factory") || code.contains("Singleton")) score += 1;
        if (code.contains("Strategy") || code.contains("Observer")) score += 2;

        return Math.min(4, score);
    }

    private double assessConditionalThinking(String code) {
        double score = 0;

        // Basic conditionals
        if (code.contains("if") || code.contains("switch")) score += 1;

        // Nested conditionals
        long nestedIfCount = code.split("if\\s*\\(.*\\)\\s*\\{.*if\\s*\\(").length - 1;
        score += Math.min(2, nestedIfCount);

        // Boolean logic
        if (code.contains("&&") || code.contains("||")) score += 1;
        if (code.contains("!") && code.contains("^")) score += 1;

        // Ternary operator
        if (code.contains("?")) score += 1;

        return Math.min(4, score);
    }
    private double assessVariableUsage(String code) {
        double score = 0;

        // Basic variable usage
        if (code.matches(".*\\w+\\s+\\w+\\s*=.*")) score += 1;

        // Meaningful naming
        if (code.matches(".*\\b(count|total|result)\\b.*")) score += 1;
        if (code.matches(".*\\b([A-Z][a-z]+){2,}\\b.*")) score += 1; // CamelCase

        // Advanced usage
        if (code.contains("final")) score += 1;
        if (code.contains("var ")) score += 1; // Java 10+ type inference

        // Collections
        if (code.contains("List<") || code.contains("Map<")) score += 1;

        return Math.min(4, score);
    }

    private double assessLoopUsage(String code) {
        double score = 0;

        // Basic loops
        if (code.contains("for") || code.contains("while")) score += 1;

        // Enhanced for loop
        if (code.contains("for (") && code.contains(":")) score += 1;

        // Nested loops
        long nestedLoopCount = code.split("for\\s*\\(.*\\)\\s*\\{.*for\\s*\\(").length - 1;
        score += Math.min(2, nestedLoopCount);

        // Streams (functional loops)
        if (code.contains(".stream()")) score += 1;

        return Math.min(4, score);
    }

    private double assessConditionalUsage(String code) {
        // Similar to assessConditionalThinking but focused on proper usage
        double score = 0;

        // Basic conditionals
        if (code.contains("if") || code.contains("switch")) score += 1;

        // Proper structure
        if (code.matches(".*if\\s*\\(.*\\)\\s*\\{.*\\}\\s*else\\s*\\{.*")) score += 1;

        // Complete conditions
        if (code.contains("else if")) score += 1;
        if (code.contains("default:")) score += 1;

        // Boolean expressions
        if (code.matches(".*\\b(true|false)\\b.*")) score += 1;

        return Math.min(4, score);
    }

    private double assessFunctionUsage(String code) {
        double score = 0;

        // Basic function
        if (code.matches(".*\\bvoid\\b.*\\b\\w+\\s*\\(.*\\).*")) score += 1;

        // Parameters
        if (code.matches(".*\\b\\w+\\s*\\(\\s*\\w+\\s+\\w+\\s*\\).*")) score += 1;

        // Return values
        if (code.matches(".*\\b(int|String|boolean)\\b.*\\b\\w+\\s*\\(.*\\).*")) score += 1;

        // Multiple functions
        long functionCount = code.split("\\b(public|private|protected)\\s+\\w+\\s+\\w+\\s*\\(").length - 1;
        score += Math.min(2, functionCount / 2.0);

        // Recursion
        if (code.matches(".*\\breturn\\b.*\\b\\w+\\s*\\(.*\\).*")) score += 1;

        return Math.min(4, score);
    }

    private double assessOperatorUsage(String code) {
        double score = 0;

        // Arithmetic operators
        if (code.contains("+") || code.contains("-") || code.contains("*") || code.contains("/")) score += 1;

        // Relational operators
        if (code.contains(">") || code.contains("<") || code.contains("==")) score += 1;

        // Boolean operators
        if (code.contains("&&") || code.contains("||") || code.contains("!")) score += 1;

        // Bitwise operators
        if (code.contains("&") || code.contains("|") || code.contains("^") || code.contains("~")) score += 1;

        // Compound operators
        if (code.contains("+=") || code.contains("-=") || code.contains("*=")) score += 1;

        return Math.min(4, score);
    }

    private double assessInitialization(String code) {
        // Check for proper initialization patterns
        if (code.matches(".*\\b\\w+\\s+\\w+\\s*=\\s*new\\b.*")) return 3; // Object initialization
        if (code.matches(".*\\b\\w+\\s+\\w+\\s*=\\s*[^;]+;.*")) return 2; // Any initialization
        if (code.matches(".*\\b\\w+\\s+\\w+\\s*;.*")) return 1; // Declaration only
        return 0; // No initialization
    }

    private double assessTermination(String code) {
        // Check for proper program termination
        if (code.contains("System.exit")) return 4; // Explicit termination
        if (code.contains("return") && code.contains("main")) return 3; // Main method return
        if (code.matches("(?s).*}\\s*$")) return 2; // Natural end
        return 1; // No clear termination
    }
    private double assessInnovation(String code, String difficulty) {
        double score = 0;

        // Basic innovation
        if (code.contains("// New approach") || code.contains("// Different from")) score += 1;

        // Algorithm innovation
        if (code.contains("// Original algorithm") ||
                code.matches(".*\\b(custom|my)\\w+\\b.*")) {
            score += 2;
        }

        // Advanced innovation for higher difficulty
        if ("advanced".equalsIgnoreCase(difficulty)) {
            if (code.contains("interface") || code.contains("abstract class")) score += 1;
            if (code.contains("enum") || code.contains("annotation")) score += 1;
        }

        return Math.min(4, score);
    }

    private double assessProblemExpansion(String code) {
        double score = 0;

        // Basic expansion
        if (code.contains("// Extra feature") || code.contains("// Bonus")) score += 1;

        // Intermediate expansion
        if (code.matches(".*//\\s*Handles\\s+additional\\s+case.*")) score += 2;

        // Advanced expansion
        if (code.contains("extends") || code.contains("implements")) score += 1;
        if (code.contains("// Future work") || code.contains("// Potential extension")) score += 1;

        return Math.min(4, score);
    }

    private double assessAlternativeSolutions(String code) {
        double score = 0;

        // Commented alternatives
        long alternativeCount = code.split("// Alternative").length - 1;
        score += Math.min(2, alternativeCount);

        // Implemented alternatives
        if (code.contains("switch") || code.contains("else if")) score += 1;
        if (code.matches(".*//\\s*Compared with.*")) score += 1;

        return Math.min(4, score);
    }
    private boolean identifiesProblemCorrectly(String code, String questionId) {
        // Check if code addresses the core problem requirements
        switch(questionId) {
            case "1": // Example: Count vowels problem
                return code.contains("count") &&
                        (code.contains("vowel") || code.contains("aeiou"));
            case "2": // Example: Palindrome check
                return code.contains("reverse") || code.contains("equalsIgnoreCase");
            default:
                return code.length() > 50; // Basic length check for generic problems
        }
    }

    private boolean hasComprehensiveErrorHandling(String code) {
        // Check for multiple error handling techniques
        int errorHandlingCount = 0;

        if (code.contains("try") && code.contains("catch")) errorHandlingCount++;
        if (code.contains("if") && code.contains("null")) errorHandlingCount++;
        if (code.contains("validate") || code.contains("check")) errorHandlingCount++;
        if (code.contains("throws")) errorHandlingCount++;

        return errorHandlingCount >= 2;
    }

    private boolean handlesEdgeCasesComprehensively(String code, String questionId) {
        // Question-specific edge case checks
        switch(questionId) {
            case "1": // Vowel count
                return code.contains("toLowerCase") ||
                        code.contains("Character.toLowerCase") ||
                        code.contains("Arrays.asList('a','e','i','o','u')");
            case "2": // Palindrome
                return code.contains("trim()") &&
                        (code.contains("length() < 2") || code.contains("== 1"));
            default:
                return code.contains("if") && code.contains("else");
        }
    }

    private boolean includesTestCases(String code) {
        // Check for test cases or assertions
        return code.contains("assert") ||
                code.contains("test") ||
                code.contains("main(String[] args)") ||
                code.matches(".*//\\s*Test case.*");
    }
    private boolean isProblemSolved(String code, String questionId) {
        if ("1".equals(questionId)) {
            return code.contains("chars()") && code.contains("filter") && code.contains("count");
        }
        return true;
    }

}
