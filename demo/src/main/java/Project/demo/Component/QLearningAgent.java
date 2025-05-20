package Project.demo.Component;

import org.springframework.stereotype.Component;

import java.io.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

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

    public QLearningAgent() {
        this.qTables = loadQTables();
        this.userLastChosenDifficultyIndex = new ConcurrentHashMap<>();
        this.userExplorationRates = new ConcurrentHashMap<>();
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
    private String getDifficultyFromIndex(int index) {
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
            System.out.println("User " + userId + ": Exploring, chose random difficulty " + getDifficultyFromIndex(chosenActionIndex) + " (Exp. Rate: " + String.format("%.2f", currentExplorationRate) + ")");
        } else {
            // Exploitation: Choose the difficulty with the highest Q-value from the current state
            chosenActionIndex = getMaxAction(qTable, currentStateIndex);
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

        // The state is the difficulty the user was *in* when they were served the questions
        int currentStateIndex = getDifficultyIndex(difficultyServedInCycle);

        // The action is the difficulty that was *chosen* for the user to practice (which is `difficultyServedInCycle`)
        // We ensure that the 'action' corresponds to the 'state' that was just experienced.
        int actionTakenIndex = getDifficultyIndex(difficultyServedInCycle);


        double reward = calculateReward(averageScore);

        // Determine the 'next state' based on performance in this cycle
        // This is a crucial part where performance dictates proficiency change.
        int nextStateIndex = determineNextStateFromScore(averageScore, currentStateIndex);


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


        System.out.println("--- Q-Value Update for User " + userId + " ---");
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

    // --- Utility method to reset Q-table for a user (for testing) ---
    public void resetUserQTable(String userId) {
        qTables.remove(userId);
        userLastChosenDifficultyIndex.remove(userId);
        userExplorationRates.remove(userId);
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
}