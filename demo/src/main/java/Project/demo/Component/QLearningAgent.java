package Project.demo.Component;

import Project.demo.Entity.ExerciseHistory;
import Project.demo.Service.ExerciseHistoryServiceImplementation;
import lombok.Getter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class QLearningAgent {
    // Difficulty levels
    public static final String BEGINNER = "Beginner";
    public static final String INTERMEDIATE = "Intermediate";
    public static final String ADVANCED = "Advanced";
    private static final String[] DIFFICULTY_LEVELS = {BEGINNER, INTERMEDIATE, ADVANCED};

    // Performance levels (aligned with state representation)
    private static final String LOW_PERFORMANCE = "Low";
    private static final String MEDIUM_PERFORMANCE = "Medium";
    private static final String HIGH_PERFORMANCE = "High";

    // Q-table: Map<State, Map<Action, Q-value>>
    private final Map<String, Map<String, Double>> qTable = new HashMap<>();

    // Learning parameters
    private final double alpha = 0.1; // Learning rate
    private final double gamma = 0.9; // Discount factor

    @Getter
    private double epsilon = 1.0; // Exploration rate
    private final double minEpsilon = 0.05;
    private final double decayRate = 0.995;

    // Thresholds
    private static final int MIN_QUESTIONS_FOR_EVALUATION = 5;
    private static final double HIGH_SCORE_THRESHOLD = 80.0;
    private static final double LOW_SCORE_THRESHOLD = 60.0;

    private final ProficiencyTracker proficiencyTracker;
    private final ExerciseHistoryServiceImplementation exerciseHistoryService;
    private final Map<String, String> userDifficultyMap = new HashMap<>();

    @Autowired
    public QLearningAgent(ProficiencyTracker proficiencyTracker,
                          ExerciseHistoryServiceImplementation exerciseHistoryService) {
        this.proficiencyTracker = proficiencyTracker;
        this.exerciseHistoryService = exerciseHistoryService;
        initializeQTable();
    }

    private void initializeQTable() {
        // Initialize Q-table with all possible state-action pairs
        String[] performanceLevels = {LOW_PERFORMANCE, MEDIUM_PERFORMANCE, HIGH_PERFORMANCE};

        for (String performance : performanceLevels) {
            for (String difficulty : DIFFICULTY_LEVELS) {
                String state = createState(difficulty, performance);
                qTable.put(state, new HashMap<>());

                // Initialize all possible actions (difficulty levels) with small random values
                for (String action : DIFFICULTY_LEVELS) {
                    // Small random initialization to encourage exploration
                    qTable.get(state).put(action, Math.random() * 0.1);
                }
            }
        }
    }

    public String determineNextDifficulty(String userId) {
        List<ExerciseHistory> history = exerciseHistoryService.fetchHistory(userId);
        if (history.isEmpty()) {
            return BEGINNER;
        }

        String currentDifficulty = userDifficultyMap.getOrDefault(userId, BEGINNER);
        double lastScore = history.get(history.size() - 1).getScore();
        String performance = evaluatePerformance(lastScore);
        String currentState = createState(currentDifficulty, performance);

        // Choose action using ε-greedy policy
        String action;
        if (Math.random() < epsilon) {
            // Exploration: random action
            action = DIFFICULTY_LEVELS[(int) (Math.random() * DIFFICULTY_LEVELS.length)];
        } else {
            // Exploitation: best known action
            action = getBestAction(currentState);
        }

        // Decay epsilon
        decayEpsilon();

        return action;
    }

    public void updateQValues(String userId) {
        List<ExerciseHistory> history = exerciseHistoryService.fetchHistory(userId);
        if (history.size() < 2) return; // Need at least two exercises for a transition

        ExerciseHistory currentExercise = history.get(history.size() - 1);
        ExerciseHistory previousExercise = history.get(history.size() - 2);

        String previousDifficulty = userDifficultyMap.getOrDefault(userId, BEGINNER);
        String previousPerformance = evaluatePerformance(previousExercise.getScore());
        String previousState = createState(previousDifficulty, previousPerformance);

        String currentDifficulty = determineNextDifficulty(userId);
        String currentPerformance = evaluatePerformance(currentExercise.getScore());
        String currentState = createState(currentDifficulty, currentPerformance);

        // Calculate reward based on performance change and difficulty adjustment
        double reward = calculateReward(
                previousExercise.getScore(),
                currentExercise.getScore(),
                previousDifficulty,
                currentDifficulty
        );

        // Get current Q-value for the previous state-action pair
        double currentQ = qTable.get(previousState).getOrDefault(currentDifficulty, 0.0);

        // Get maximum Q-value for current state
        double maxNextQ = qTable.get(currentState).values().stream()
                .max(Double::compare)
                .orElse(0.0);

        // Apply Q-learning update rule: Q(s,a) ← Q(s,a) + α[r + γ max Q(s',a') - Q(s,a)]
        double updatedQ = currentQ + alpha * (reward + gamma * maxNextQ - currentQ);
        qTable.get(previousState).put(currentDifficulty, updatedQ);

        // Update user's current difficulty
        userDifficultyMap.put(userId, currentDifficulty);
    }

    private double calculateReward(double previousScore, double currentScore,
                                   String previousDifficulty, String currentDifficulty) {
        double scoreChange = currentScore - previousScore;
        int previousDiffValue = getDifficultyValue(previousDifficulty);
        int currentDiffValue = getDifficultyValue(currentDifficulty);
        int difficultyChange = currentDiffValue - previousDiffValue;

        // Normalize score to [0,1] range
        double normalizedScore = currentScore / 100.0;

        // Reward components
        double performanceReward = normalizedScore; // Higher scores are better
        double difficultyReward = 0;

        // Reward appropriate difficulty adjustments
        if (normalizedScore > 0.8 && difficultyChange >= 0) {
            // Good performance, encourage maintaining or increasing difficulty
            difficultyReward = 0.5 * difficultyChange;
        } else if (normalizedScore < 0.6 && difficultyChange <= 0) {
            // Poor performance, encourage maintaining or decreasing difficulty
            difficultyReward = 0.5 * -difficultyChange;
        }

        // Combine rewards with weights
        return (0.7 * performanceReward) + (0.3 * difficultyReward);
    }

    // Helper methods remain the same
    private String createState(String difficulty, String performance) {
        return difficulty + "-" + performance;
    }

    private String evaluatePerformance(double score) {
        if (score >= HIGH_SCORE_THRESHOLD) return HIGH_PERFORMANCE;
        if (score <= LOW_SCORE_THRESHOLD) return LOW_PERFORMANCE;
        return MEDIUM_PERFORMANCE;
    }

    private String getBestAction(String state) {
        return qTable.getOrDefault(state, new HashMap<>()).entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(BEGINNER);
    }

    private int getDifficultyValue(String difficulty) {
        return switch (difficulty) {
            case BEGINNER -> 0;
            case INTERMEDIATE -> 1;
            case ADVANCED -> 2;
            default -> 0;
        };
    }

    private void decayEpsilon() {
        epsilon = Math.max(minEpsilon, epsilon * decayRate);
    }

    public String getCurrentDifficulty(String userId) {
        return userDifficultyMap.getOrDefault(userId, BEGINNER);
    }
    /**
     * Evaluates user proficiency and returns the recommended difficulty level
     * @param userId The user to evaluate
     * @return Recommended difficulty level (BEGINNER, INTERMEDIATE, or ADVANCED)
     */
    public String determineRecommendedProficiencyLevel(String userId) {
        List<ExerciseHistory> history = exerciseHistoryService.fetchHistory(userId);

        // Default to beginner if no history
        if (history.isEmpty()) {
            return BEGINNER;
        }

        String currentDifficulty = userDifficultyMap.getOrDefault(userId, BEGINNER);
        double lastScore = history.get(history.size() - 1).getScore();
        String performance = evaluatePerformance(lastScore);
        String currentState = createState(currentDifficulty, performance);

        // Get Q-learning's suggested action
        String qLearningSuggestedLevel = determineNextDifficulty(userId);

        // Calculate performance metrics
        double averageScore = calculateWeightedAverageScore(history);
        double consistencyScore = calculateConsistencyScore(history);
        double recentImprovement = calculateRecentImprovement(history);

        // Decision making process
        if (shouldIncreaseDifficulty(currentDifficulty, qLearningSuggestedLevel, averageScore, consistencyScore, recentImprovement)) {
            return getNextDifficultyLevel(currentDifficulty);
        }
        else if (shouldDecreaseDifficulty(currentDifficulty, qLearningSuggestedLevel, averageScore, consistencyScore, recentImprovement)) {
            return getPreviousDifficultyLevel(currentDifficulty);
        }

        // Default to maintaining current level
        return currentDifficulty;
    }

    private boolean shouldIncreaseDifficulty(String currentLevel, String qSuggestedLevel,
                                             double avgScore, double consistency, double improvement) {
        // Must meet all these conditions to step up
        return qSuggestedLevel.equals(getNextDifficultyLevel(currentLevel)) &&
                avgScore >= HIGH_SCORE_THRESHOLD &&
                consistency >= 0.7 &&
                improvement >= 0;
    }

    private boolean shouldDecreaseDifficulty(String currentLevel, String qSuggestedLevel,
                                             double avgScore, double consistency, double improvement) {
        // Never step down beginners
        if (currentLevel.equals(BEGINNER)) return false;

        // Conditions for stepping down
        return qSuggestedLevel.equals(getPreviousDifficultyLevel(currentLevel)) &&
                avgScore <= LOW_SCORE_THRESHOLD &&
                consistency >= 0.6 &&
                improvement <= 0;
    }

    // Helper method to calculate weighted average (recent scores weighted higher)
    private double calculateWeightedAverageScore(List<ExerciseHistory> history) {
        double sum = 0;
        double weightSum = 0;

        for (int i = 0; i < history.size(); i++) {
            double weight = (i + 1) * 1.0 / history.size(); // Linear weighting
            sum += history.get(i).getScore() * weight;
            weightSum += weight;
        }

        return sum / weightSum;
    }

    // Helper method to calculate consistency (0-1 range)
    private double calculateConsistencyScore(List<ExerciseHistory> history) {
        int windowSize = Math.min(3, history.size());
        if (windowSize < 2) return 1.0;

        double min = Double.MAX_VALUE;
        double max = Double.MIN_VALUE;

        for (int i = history.size() - windowSize; i < history.size(); i++) {
            double score = history.get(i).getScore();
            min = Math.min(min, score);
            max = Math.max(max, score);
        }

        // Normalize consistency (1 = perfect consistency)
        return 1.0 - (max - min) / 100.0;
    }

    // Helper method to calculate recent improvement trend
    private double calculateRecentImprovement(List<ExerciseHistory> history) {
        if (history.size() < 2) return 0;

        int windowSize = Math.min(3, history.size());
        double sumImprovement = 0;

        for (int i = history.size() - windowSize; i < history.size() - 1; i++) {
            sumImprovement += history.get(i + 1).getScore() - history.get(i).getScore();
        }

        return sumImprovement / (windowSize - 1);
    }

    // Existing helper methods (unchanged)
    private String getNextDifficultyLevel(String currentLevel) {
        switch (currentLevel) {
            case BEGINNER: return INTERMEDIATE;
            case INTERMEDIATE: return ADVANCED;
            default: return currentLevel;
        }
    }

    private String getPreviousDifficultyLevel(String currentLevel) {
        switch (currentLevel) {
            case ADVANCED:
                return INTERMEDIATE;
            case INTERMEDIATE:
                return BEGINNER;
            default:
                return currentLevel;
        }
    }
}