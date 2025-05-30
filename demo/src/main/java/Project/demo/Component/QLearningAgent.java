package Project.demo.Component;

import org.springframework.stereotype.Component;

import java.io.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors; // Added for stream operations

@Component
public class QLearningAgent {

    // Hyperparameters
    private static final double LEARNING_RATE = 0.2; // How much new information overrides old. Higher = faster learning.
    private static final double DISCOUNT_FACTOR = 0.9; // Importance of future rewards. Closer to 1 = values future more.
    private static final double EXPLORATION_RATE_INITIAL = 0.2; // Initial chance of choosing a random action (difficulty)
    private static final double EXPLORATION_RATE_DECAY = 0.995; // Rate at which exploration decreases over time
    private static final double EXPLORATION_RATE_MIN = 0.05; // Minimum exploration rate

    // Define valid difficulty levels and map them to numerical states/actions
    private static final List<String> VALID_DIFFICULTIES = Arrays.asList("Beginner", "Intermediate", "Advanced");
    private static final String DEFAULT_DIFFICULTY = "Beginner"; // Used for new users or fallbacks
    private static final int NUM_DIFFICULTIES = VALID_DIFFICULTIES.size();

    // Q-table: Map<userId, double[][]>
    private final ConcurrentHashMap<String, double[][]> qTables;
    // Map to track the *last chosen difficulty (action)* for each user
    // This is needed because the 'update' happens after the action (choosing difficulty) has been made and performance observed.
    private final ConcurrentHashMap<String, Integer> userLastChosenDifficultyIndex;
    // Map to track the user's current exploration rate
    private final ConcurrentHashMap<String, Double> userExplorationRates;

    // File path for Q-table persistence
    private static final String Q_TABLE_FILE = "q_tables.ser";

    // --- NEW: Evaluation Metrics ---
    private long totalCyclesCompleted;
    private long totalExplorationActions;
    private long totalExploitationActions;
    private double totalAccumulatedReward;
    private final ConcurrentHashMap<String, DoubleSummaryStatistics> scoresPerDifficulty; // Tracks avg/min/max scores per difficulty served
    private long totalLikesFeedback;
    private long totalDislikesFeedback;
    private final ConcurrentHashMap<String, int[]> userProgressionCounts; // Tracks how many times each user changed difficulty: [up, down, same]

    public QLearningAgent() {
        this.qTables = loadQTables();
        this.userLastChosenDifficultyIndex = new ConcurrentHashMap<>();
        this.userExplorationRates = new ConcurrentHashMap<>();

        // --- NEW: Initialize Metrics ---
        this.totalCyclesCompleted = 0;
        this.totalExplorationActions = 0;
        this.totalExploitationActions = 0;
        this.totalAccumulatedReward = 0.0;
        this.scoresPerDifficulty = new ConcurrentHashMap<>();
        VALID_DIFFICULTIES.forEach(diff -> scoresPerDifficulty.put(diff, new DoubleSummaryStatistics()));
        this.totalLikesFeedback = 0;
        this.totalDislikesFeedback = 0;
        this.userProgressionCounts = new ConcurrentHashMap<>(); // [0]=up, [1]=down, [2]=same

        System.out.println("QLearningAgent initialized. Loaded " + qTables.size() + " Q-tables.");
    }

    /**
     * Initializes a Q-table for a new user.
     * All Q-values are initialized to 0.0.
     * @return A new Q-table (double[][]) initialized with zeros.
     */
    private double[][] initializeQTable() {
        double[][] qTable = new double[NUM_DIFFICULTIES][NUM_DIFFICULTIES];
        for (int i = 0; i < NUM_DIFFICULTIES; i++) {
            Arrays.fill(qTable[i], 0.0);
        }
        return qTable;
    }

    /**
     * Converts a difficulty string to its corresponding index.
     * @param difficulty The difficulty string (e.g., "Beginner").
     * @return The index (0 for Beginner, 1 for Intermediate, 2 for Advanced) or 0 (Beginner) if invalid.
     */
    private int getDifficultyIndex(String difficulty) {
        int index = VALID_DIFFICULTIES.indexOf(difficulty);
        return index != -1 ? index : VALID_DIFFICULTIES.indexOf(DEFAULT_DIFFICULTY);
    }

    /**
     * Converts a difficulty index to its corresponding string.
     * @param index The index (0, 1, or 2).
     * @return The corresponding difficulty string or DEFAULT_DIFFICULTY if index is out of bounds.
     */
    public String getDifficultyFromIndex(int index) { // Made public for external use like in ExerciseServiceImpl
        if (index >= 0 && index < NUM_DIFFICULTIES) {
            return VALID_DIFFICULTIES.get(index);
        }
        return DEFAULT_DIFFICULTY;
    }

    /**
     * Calculates the reward based on the average score from a cycle.
     * A higher score gives a positive reward, a score of 0 gives a strong negative reward.
     * This is crucial for guiding the learning.
     * @param averageScore The average score (0-100) from the completed cycle.
     * @return The calculated reward.
     */
    private double calculateReward(double averageScore) {
        double normalizedScore = averageScore / 100.0;

        if (normalizedScore >= 0.9) { // Perfect or near-perfect score
            return 10.0; // Strong positive reward
        } else if (normalizedScore >= 0.6) { // Good score (e.g., > 60%)
            return 5.0;  // Moderate positive reward
        } else if (normalizedScore > 0.0) { // Partial score (e.g., 1% - 59%)
            return -2.0; // Small penalty for not mastering
        } else { // Score is 0
            // Apply a significant penalty for incorrect answers.
            return -20.0; // Strong negative penalty to really push down
        }
    }


    /**
     * Determines the *next recommended difficulty* for a user based on their last known state
     * (which is implicitly updated via `updateQValuesAfterCycle`). This method uses
     * an epsilon-greedy policy.
     * This method is called by `ExerciseServiceImpl` when starting a new question cycle.
     *
     * @param userId The ID of the user.
     * @return The recommended difficulty string ("Beginner", "Intermediate", "Advanced").
     */
    public String determineNextDifficulty(String userId) {
        double[][] qTable = qTables.computeIfAbsent(userId, k -> initializeQTable());
        double currentExplorationRate = userExplorationRates.computeIfAbsent(userId, k -> EXPLORATION_RATE_INITIAL);

        // The 'current state' for selection is the last difficulty the agent *recommended*
        // and which the user *experienced*. If new user, start at Beginner state.
        int currentStateIndex = userLastChosenDifficultyIndex.getOrDefault(userId, getDifficultyIndex(DEFAULT_DIFFICULTY));


        int chosenActionIndex; // This is the difficulty we will recommend
        if (Math.random() < currentExplorationRate) {
            // Exploration: Choose a random difficulty
            chosenActionIndex = new Random().nextInt(NUM_DIFFICULTIES);
            totalExplorationActions++; // --- NEW: Metric Update ---
            System.out.println("User " + userId + ": Exploring, chose random difficulty " + getDifficultyFromIndex(chosenActionIndex) + " (Exp. Rate: " + String.format("%.2f", currentExplorationRate) + ")");
        } else {
            // Exploitation: Choose the difficulty with the highest Q-value from the current state
            chosenActionIndex = getMaxAction(qTable, currentStateIndex);
            totalExploitationActions++; // --- NEW: Metric Update ---
            System.out.println("User " + userId + ": Exploiting, chose difficulty " + getDifficultyFromIndex(chosenActionIndex) + " (Exp. Rate: " + String.format("%.2f", currentExplorationRate) + ")");
        }

        // Store this chosen difficulty index. It will be the 'action' in the next update.
        userLastChosenDifficultyIndex.put(userId, chosenActionIndex);

        // Decay exploration rate
        double newExplorationRate = Math.max(EXPLORATION_RATE_MIN, currentExplorationRate * EXPLORATION_RATE_DECAY);
        userExplorationRates.put(userId, newExplorationRate);

        return getDifficultyFromIndex(chosenActionIndex);
    }

    /**
     * Updates the Q-value based on the user's performance for the completed cycle.
     * This method is called *after* a full evaluation cycle (e.g., 2 questions) is completed.
     *
     * @param userId The ID of the user.
     * @param averageScore The average score (0-100) from the completed evaluation cycle.
     * @param difficultyServedInCycle The difficulty level of the questions that were served during this cycle.
     */
    public synchronized void updateQValuesAfterCycle(String userId, double averageScore, String difficultyServedInCycle) {
        double[][] qTable = qTables.computeIfAbsent(userId, k -> initializeQTable());
        totalCyclesCompleted++; // --- NEW: Metric Update ---

        // The state is the difficulty the user was *in* when they were served the questions
        int currentStateIndex = getDifficultyIndex(difficultyServedInCycle);


        int actionTakenIndex = getDifficultyIndex(difficultyServedInCycle);


        double reward = calculateReward(averageScore);
        totalAccumulatedReward += reward;

        // --- NEW: Update scoresPerDifficulty ---
        scoresPerDifficulty.computeIfPresent(difficultyServedInCycle, (k, stats) -> {
            stats.accept(averageScore);
            return stats;
        });


        int nextStateIndex = determineNextStateFromScore(averageScore, currentStateIndex);

        // --- NEW: Update user progression counts ---
        userProgressionCounts.computeIfAbsent(userId, k -> new int[3]); // [up, down, same]
        int[] counts = userProgressionCounts.get(userId);
        if (nextStateIndex > currentStateIndex) {
            counts[0]++; // Upgraded
        } else if (nextStateIndex < currentStateIndex) {
            counts[1]++; // Downgraded
        } else {
            counts[2]++; // Stayed same
        }


        // Q-learning formula:
        // Q(s, a) = Q(s, a) + alpha * [reward + gamma * max(Q(s', a')) - Q(s, a)]
        double oldQValue = qTable[currentStateIndex][actionTakenIndex];
        double maxFutureQ = getMaxQValue(qTable, nextStateIndex); // Max Q-value for the *next* state

        double newQValue = oldQValue + LEARNING_RATE * (reward + DISCOUNT_FACTOR * maxFutureQ - oldQValue);
        qTable[currentStateIndex][actionTakenIndex] = newQValue;

        // After updating Q-values, set the user's *actual new proficiency level* as their
        // last chosen difficulty, so that the next `determineNextDifficulty` call
        // starts from this new adjusted level.
        userLastChosenDifficultyIndex.put(userId, nextStateIndex);


        System.out.println("--- Q-Value Update for User " + userId + " (Performance) ---");
        System.out.println("Current State (Served Difficulty): " + getDifficultyFromIndex(currentStateIndex));
        System.out.println("Action Taken (Served Difficulty): " + getDifficultyFromIndex(actionTakenIndex));
        System.out.println("Average Score: " + averageScore);
        System.out.println("Calculated Reward: " + reward);
        System.out.println("Next State (Determined from Score): " + getDifficultyFromIndex(nextStateIndex));
        System.out.println("Old Q(" + getDifficultyFromIndex(currentStateIndex) + ", " + getDifficultyFromIndex(actionTakenIndex) + "): " + String.format("%.2f", oldQValue));
        System.out.println("Max Future Q(s', a'): " + String.format("%.2f", maxFutureQ));
        System.out.println("New Q(" + getDifficultyFromIndex(currentStateIndex) + ", " + getDifficultyFromIndex(actionTakenIndex) + "): " + String.format("%.2f", newQValue));
        System.out.println("Q-Table for " + userId + " (relevant row after update for " + getDifficultyFromIndex(currentStateIndex) + "): " + Arrays.toString(qTable[currentStateIndex]));

        saveQTables(); // Persist the updated Q-table
    }

    /**
     * **NEW FUNCTION:** Updates the Q-value based on explicit user feedback.
     * This function allows direct reward/punishment of specific state-action pairs
     * based on whether the user liked or disliked the exercise served.
     *
     * @param userId The ID of the user providing feedback.
     * @param exerciseDifficulty The difficulty of the exercise the feedback is about.
     * @param feedbackType "Like" or "Dislike".
     */
    public synchronized void updateQValuesBasedOnFeedback(String userId, String exerciseDifficulty, String feedbackType) {
        double[][] qTable = qTables.computeIfAbsent(userId, k -> initializeQTable());

        // The state is the user's proficiency level when they received the exercise.
        // For simplicity, we use the provided exercise difficulty as the state for feedback.
        int currentStateIndex = getDifficultyIndex(exerciseDifficulty);

        // The action is implicitly "serving an exercise of this difficulty".
        int actionTakenIndex = getDifficultyIndex(exerciseDifficulty);

        double feedbackReward = 0.0;
        if ("Like".equalsIgnoreCase(feedbackType)) {
            feedbackReward = 5.0; // Positive reinforcement for good exercises
            totalLikesFeedback++; // --- NEW: Metric Update ---
            System.out.println("User " + userId + " liked difficulty " + exerciseDifficulty + ". Applying positive feedback reward.");
        } else if ("Dislike".equalsIgnoreCase(feedbackType)) {
            feedbackReward = -5.0; // Negative reinforcement for bad exercises
            totalDislikesFeedback++; // --- NEW: Metric Update ---
            System.out.println("User " + userId + " disliked difficulty " + exerciseDifficulty + ". Applying negative feedback reward.");
        } else {
            System.err.println("Invalid feedback type: " + feedbackType + " for user " + userId);
            return; // Do nothing for invalid feedback
        }

        // Apply Q-learning formula: Q(s, a) = Q(s, a) + alpha * [reward + gamma * max(Q(s', a')) - Q(s, a)]
        // For feedback, we simplify the formula as there's no direct "next state"
        // based on this feedback alone, but rather a direct adjustment to the value
        // of *this specific state-action pair*.
        // We'll treat the 'next state' as the current state (currentStateIndex), essentially reinforcing
        // the value of having provided that difficulty.
        double oldQValue = qTable[currentStateIndex][actionTakenIndex];
        double maxFutureQ = getMaxQValue(qTable, currentStateIndex); // Max Q-value from the *current* state.

        double newQValue = oldQValue + LEARNING_RATE * (feedbackReward + DISCOUNT_FACTOR * maxFutureQ - oldQValue);
        qTable[currentStateIndex][actionTakenIndex] = newQValue;

        System.out.println("--- Q-Value Update for User " + userId + " (Feedback) ---");
        System.out.println("State (Exercise Difficulty): " + getDifficultyFromIndex(currentStateIndex));
        System.out.println("Action Taken (Served Difficulty): " + getDifficultyFromIndex(actionTakenIndex));
        System.out.println("Feedback Reward: " + feedbackReward);
        System.out.println("Old Q(" + getDifficultyFromIndex(currentStateIndex) + ", " + getDifficultyFromIndex(actionTakenIndex) + "): " + String.format("%.2f", oldQValue));
        System.out.println("New Q(" + getDifficultyFromIndex(currentStateIndex) + ", " + getDifficultyFromIndex(actionTakenIndex) + "): " + String.format("%.2f", newQValue));
        System.out.println("Q-Table for " + userId + " (relevant row after feedback for " + getDifficultyFromIndex(currentStateIndex) + "): " + Arrays.toString(qTable[currentStateIndex]));

        saveQTables(); // Persist the updated Q-table
    }


    /**
     * Determines the 'next state' (user's new effective proficiency level) based on the current performance.
     * This dictates whether the user's underlying "skill" level has improved, decreased, or stayed same.
     * @param averageScore The average score for the evaluation cycle.
     * @param currentStateIndex The index of the state (difficulty) the user was in.
     * @return The index of the new state.
     */
    private int determineNextStateFromScore(double averageScore, int currentStateIndex) {
        if (averageScore >= 80) { // High performance: user probably mastered this or is ready for next
            return Math.min(currentStateIndex + 1, NUM_DIFFICULTIES - 1); // Advance to next difficulty
        } else if (averageScore <= 50) { // Low performance: user struggled, needs easier questions
            return Math.max(currentStateIndex - 1, 0); // Step down to easier difficulty
        } else { // Moderate performance: user is at the right level or needs more practice here
            return currentStateIndex; // Stay at same difficulty
        }
    }

    /**
     * Helper to find the maximum Q-value for a given state.
     * @param qTable The Q-table.
     * @param stateIndex The index of the state.
     * @return The maximum Q-value from that state.
     */
    private double getMaxQValue(double[][] qTable, int stateIndex) {
        if (stateIndex < 0 || stateIndex >= NUM_DIFFICULTIES) {
            return 0.0;
        }
        return Arrays.stream(qTable[stateIndex]).max().orElse(0.0);
    }

    /**
     * Helper to find the action (difficulty index) with the maximum Q-value for a given state.
     * @param qTable The Q-table.
     * @param stateIndex The index of the state.
     * @return The index of the action with the highest Q-value.
     */
    private int getMaxAction(double[][] qTable, int stateIndex) {
        if (stateIndex < 0 || stateIndex >= NUM_DIFFICULTIES) {
            return getDifficultyIndex(DEFAULT_DIFFICULTY);
        }
        int bestAction = 0;
        double maxQ = Double.NEGATIVE_INFINITY;
        boolean allZero = true;

        for (int i = 0; i < NUM_DIFFICULTIES; i++) {
            if (qTable[stateIndex][i] != 0.0) {
                allZero = false;
            }
            if (qTable[stateIndex][i] > maxQ) {
                maxQ = qTable[stateIndex][i];
                bestAction = i;
            }
        }
        // If all Q-values are zero for this state, choose a random action to encourage initial exploration
        if (allZero) {
            return new Random().nextInt(NUM_DIFFICULTIES);
        }
        return bestAction;
    }

    /**
     * Persists the Q-tables to a file.
     */
    private void saveQTables() {
        try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(Q_TABLE_FILE))) {
            oos.writeObject(qTables);
            System.out.println("Q-tables saved to " + Q_TABLE_FILE);
        } catch (IOException e) {
            System.err.println("Error saving Q-tables: " + e.getMessage());
        }
    }

    /**
     * Loads the Q-tables from a file.
     * @return Loaded Q-tables, or an empty ConcurrentHashMap if file not found or error occurs.
     */
    private ConcurrentHashMap<String, double[][]> loadQTables() {
        File file = new File(Q_TABLE_FILE);
        if (file.exists()) {
            try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(file))) {
                ConcurrentHashMap<String, double[][]> loadedQTables = (ConcurrentHashMap<String, double[][]>) ois.readObject();
                System.out.println("Q-tables loaded from " + Q_TABLE_FILE);
                return loadedQTables;
            } catch (IOException | ClassNotFoundException e) {
                System.err.println("Error loading Q-tables, starting fresh: " + e.getMessage());
            }
        }
        System.out.println("No existing Q-tables found or unable to load, starting new Q-tables.");
        return new ConcurrentHashMap<>();
    }

    /**
     * Resets the Q-table and related state for a specific user.
     * This is useful for testing or when a user's progress needs to be completely wiped.
     * @param userId The ID of the user to reset.
     */
    public void resetUserQTable(String userId) {
        qTables.remove(userId);
        userLastChosenDifficultyIndex.remove(userId);
        userExplorationRates.remove(userId);
        userProgressionCounts.remove(userId); // --- NEW: Reset Metric ---
        saveQTables();
        System.out.println("Q-table and state for user " + userId + " reset.");
    }

    // For debugging: print a user's Q-table
    public void printUserQTable(String userId) {
        double[][] qTable = qTables.get(userId);
        if (qTable == null) {
            System.out.println("No Q-table found for user: " + userId);
            return;
        }
        System.out.println("\n--- Q-Table for User: " + userId + " ---");
        System.out.print("        ");
        for (String diff : VALID_DIFFICULTIES) {
            System.out.printf("%-15s", diff);
        }
        System.out.println();
        for (int i = 0; i < NUM_DIFFICULTIES; i++) {
            System.out.printf("%-8s", VALID_DIFFICULTIES.get(i));
            for (int j = 0; j < NUM_DIFFICULTIES; j++) {
                System.out.printf("%-15.2f", qTable[i][j]);
            }
            System.out.println();
        }
        System.out.println("Last Chosen Difficulty: " + getDifficultyFromIndex(userLastChosenDifficultyIndex.getOrDefault(userId, -1)));
        System.out.println("Exploration Rate: " + String.format("%.2f", userExplorationRates.getOrDefault(userId, 0.0)));
        System.out.println("-------------------------------------\n");
    }

    /**
     * Evaluates and returns a categorical proficiency level for a user.
     * This function uses the Q-table and the user's `userLastChosenDifficultyIndex`
     * (which represents the current 'state' or 'proficiency level' the Q-agent believes the user is in)
     * as the primary indicator.
     *
     * @param userId The ID of the user.
     * @return A string representing the user's evaluated proficiency (e.g., "Beginner", "Intermediate", "Advanced", "No Data").
     */
    public String evaluateUserProficiency(String userId) {
        if (!qTables.containsKey(userId) || !userLastChosenDifficultyIndex.containsKey(userId)) {
            System.out.println("User " + userId + ": No Q-learning data found, defaulting to " + DEFAULT_DIFFICULTY + " proficiency.");
            return DEFAULT_DIFFICULTY;
        }

        int proficiencyIndex = userLastChosenDifficultyIndex.get(userId);

        String proficiency = getDifficultyFromIndex(proficiencyIndex);
        System.out.println("User " + userId + ": Evaluated proficiency based on Q-Learning state: " + proficiency);
        return proficiency;
    }

    /**
     * Manually sets a user's proficiency level.
     * This method directly updates the agent's internal belief about the user's current proficiency.
     *
     * IMPORTANT: Use this method with caution, as it bypasses the natural learning process of the Q-agent.
     * The agent will continue to learn from this new proficiency level moving forward.
     *
     * @param userId The ID of the AppUser whose proficiency is to be set.
     * @param newProficiency The desired proficiency level as a string (e.g., "Beginner", "Intermediate", "Advanced").
     */
    public synchronized void setUserProficiency(String userId, String newProficiency) {
        // Ensure a Q-table exists for the user, even if we are just setting their proficiency
        qTables.computeIfAbsent(userId, k -> initializeQTable());

        int newProficiencyIndex = getDifficultyIndex(newProficiency);
        userLastChosenDifficultyIndex.put(userId, newProficiencyIndex);

        // Optionally, reset exploration rate when manually setting proficiency,
        // to encourage fresh exploration from this new state.
        userExplorationRates.put(userId, EXPLORATION_RATE_INITIAL);

        saveQTables(); // Persist the change

        System.out.println("Manually set proficiency for user " + userId + " to " + getDifficultyFromIndex(newProficiencyIndex));
    }

    // --- NEW: Public method to retrieve evaluation metrics ---

    /**
     * Retrieves a summary of the Q-Learning Agent's global evaluation metrics.
     * This provides insights into the agent's overall behavior and performance.
     *
     * @return A map containing various evaluation metrics.
     */
    public Map<String, Object> getEvaluationMetrics() {
        Map<String, Object> metrics = new LinkedHashMap<>();

        // 1. Agent Behavior Metrics
        metrics.put("totalCyclesCompleted", totalCyclesCompleted);
        metrics.put("totalExplorationActions", totalExplorationActions);
        metrics.put("totalExploitationActions", totalExploitationActions);
        long totalActions = totalExplorationActions + totalExploitationActions;
        metrics.put("explorationRateCurrent", String.format("%.2f", userExplorationRates.values().stream().mapToDouble(d -> d).average().orElse(EXPLORATION_RATE_INITIAL)));
        metrics.put("explorationRatio", totalActions > 0 ? String.format("%.2f", (double) totalExplorationActions / totalActions) : "N/A");
        metrics.put("exploitationRatio", totalActions > 0 ? String.format("%.2f", (double) totalExploitationActions / totalActions) : "N/A");

        // 2. Performance Metrics
        metrics.put("totalAccumulatedReward", String.format("%.2f", totalAccumulatedReward));
        metrics.put("averageRewardPerCycle", totalCyclesCompleted > 0 ? String.format("%.2f", totalAccumulatedReward / totalCyclesCompleted) : "N/A");

        Map<String, String> avgScores = new LinkedHashMap<>();
        scoresPerDifficulty.forEach((diff, stats) ->
                avgScores.put(diff, String.format("%.2f", stats.getAverage()))
        );
        metrics.put("averageScorePerDifficulty", avgScores);

        // 3. Engagement Metrics
        metrics.put("totalLikesFeedback", totalLikesFeedback);
        metrics.put("totalDislikesFeedback", totalDislikesFeedback);
        long totalFeedback = totalLikesFeedback + totalDislikesFeedback;
        metrics.put("feedbackLikeRatio", totalFeedback > 0 ? String.format("%.2f", (double) totalLikesFeedback / totalFeedback) : "N/A");
        metrics.put("feedbackDislikeRatio", totalFeedback > 0 ? String.format("%.2f", (double) totalDislikesFeedback / totalFeedback) : "N/A");

        // 4. Progression Metrics (Global Summary)
        long totalUpgrades = userProgressionCounts.values().stream().mapToInt(counts -> counts[0]).sum();
        long totalDowngrades = userProgressionCounts.values().stream().mapToInt(counts -> counts[1]).sum();
        long totalStayedSame = userProgressionCounts.values().stream().mapToInt(counts -> counts[2]).sum();

        metrics.put("totalDifficultyUpgrades", totalUpgrades);
        metrics.put("totalDifficultyDowngrades", totalDowngrades);
        metrics.put("totalDifficultyStayedSame", totalStayedSame);

        long totalUserTransitions = totalUpgrades + totalDowngrades + totalStayedSame;
        metrics.put("upgradeRatio", totalUserTransitions > 0 ? String.format("%.2f", (double) totalUpgrades / totalUserTransitions) : "N/A");
        metrics.put("downgradeRatio", totalUserTransitions > 0 ? String.format("%.2f", (double) totalDowngrades / totalUserTransitions) : "N/A");
        metrics.put("staySameRatio", totalUserTransitions > 0 ? String.format("%.2f", (double) totalStayedSame / totalUserTransitions) : "N/A");

        // Current distribution of users by proficiency
        Map<String, Long> userProficiencyDistribution = userLastChosenDifficultyIndex.values().stream()
                .map(this::getDifficultyFromIndex)
                .collect(Collectors.groupingBy(s -> s, Collectors.counting()));
        metrics.put("currentUserProficiencyDistribution", userProficiencyDistribution);

        return metrics;
    }

    /**
     * Resets all global evaluation metrics. Useful for starting a new evaluation period.
     */
    public synchronized void resetEvaluationMetrics() {
        this.totalCyclesCompleted = 0;
        this.totalExplorationActions = 0;
        this.totalExploitationActions = 0;
        this.totalAccumulatedReward = 0.0;
        this.scoresPerDifficulty.forEach((diff, stats) -> scoresPerDifficulty.put(diff, new DoubleSummaryStatistics())); // Reset statistics
        this.totalLikesFeedback = 0;
        this.totalDislikesFeedback = 0;
        this.userProgressionCounts.clear(); // Clear all user-specific progression counts

        System.out.println("All QLearningAgent evaluation metrics have been reset.");
    }
}