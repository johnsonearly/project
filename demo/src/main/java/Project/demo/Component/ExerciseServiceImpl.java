package Project.demo.Component;

import Project.demo.DTOs.ExerciseDTO;
import Project.demo.Entity.AppUser;
import Project.demo.Entity.ExerciseHistory;
import Project.demo.Repositories.AppUserInterface;
import Project.demo.Repositories.ExerciseHistoryInterface;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
public class ExerciseServiceImpl {

    private static final int QUESTIONS_PER_EVALUATION_CYCLE = 2; // Number of questions before evaluating and adjusting difficulty
    private static final Logger logger = LoggerFactory.getLogger(ExerciseServiceImpl.class);
    private static final List<String> VALID_DIFFICULTIES = List.of("Beginner", "Intermediate", "Advanced");

    private final String filePath = "utils/exercises.xlsx";
    private final QLearningAgent qLearningAgent;
    private final ExerciseHistoryInterface exerciseHistoryRepository; // Assuming you still use this
    private final AppUserInterface userRepository; // Assuming you still use this for direct user updates

    // Map to track user's current question cycle progress and the difficulty for that cycle
    // Key: userName, Value: UserSessionState
    private final Map<String, UserSessionState> userSessionStates = new ConcurrentHashMap<>();

    private static final String DEFAULT_DIFFICULTY = "Beginner";

    // Caches for efficient exercise retrieval
    private final Map<String, List<String>> difficultyExerciseCache = new HashMap<>(); // Stores Question IDs grouped by difficulty
    private final Map<String, ExerciseDTO> exerciseCache = new ConcurrentHashMap<>(); // Stores ExerciseDTOs by Question ID
    private boolean cacheInitialized = false;


    @Autowired
    public ExerciseServiceImpl(@Lazy QLearningAgent qLearningAgent,
                               ExerciseHistoryInterface exerciseHistoryRepository,
                               AppUserInterface userRepository) {
        this.qLearningAgent = qLearningAgent;
        this.exerciseHistoryRepository = exerciseHistoryRepository;
        this.userRepository = userRepository;
        initializeExerciseCaches(); // Ensure caches are initialized upon service creation
    }

    /**
     * Represents the state of a user's current exercise session.
     * Tracks how many questions into the current cycle the user is and the
     * recommended difficulty for that cycle.
     */
    private static class UserSessionState {
        int questionsAnsweredInCycle;
        String currentCycleDifficulty; // The difficulty recommended by the Q-agent for this cycle
        List<Double> scoresInCurrentCycle; // To store scores for the current cycle for evaluation

        public UserSessionState(String initialDifficulty) {
            this.questionsAnsweredInCycle = 0;
            this.currentCycleDifficulty = initialDifficulty;
            this.scoresInCurrentCycle = new ArrayList<>();
        }

        public void resetCycle(String newDifficulty) {
            this.questionsAnsweredInCycle = 0;
            this.currentCycleDifficulty = newDifficulty; // Set the new difficulty for the next cycle
            this.scoresInCurrentCycle.clear();
        }

        public void addScore(double score) {
            this.scoresInCurrentCycle.add(score);
            this.questionsAnsweredInCycle++;
        }

        public double getAverageScore() {
            return scoresInCurrentCycle.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
        }
    }

    /**
     * Initializes the in-memory caches for exercises. This method is synchronized
     * to prevent multiple threads from initializing the cache simultaneously.
     */
    private synchronized void initializeExerciseCaches() {
        if (cacheInitialized) {
            return; // Already initialized
        }

        logger.info("Initializing exercise caches from {}", filePath);
        try (InputStream is = new ClassPathResource(filePath).getInputStream();
             Workbook workbook = new XSSFWorkbook(is)) {

            Sheet sheet = workbook.getSheetAt(0);
            DataFormatter dataFormatter = new DataFormatter();

            // Temporary map to build difficulty-based lists before putting into final cache
            Map<String, List<String>> tempDifficultyExerciseMap = new HashMap<>();
            VALID_DIFFICULTIES.forEach(diff -> tempDifficultyExerciseMap.put(diff, new ArrayList<>()));

            for (Row row : sheet) {
                // Skip header row and empty rows
                if (row == null || row.getRowNum() == 0) {
                    continue;
                }
                try {
                    ExerciseDTO exercise = buildExerciseDTO(row, dataFormatter);
                    if (exercise != null && exercise.getQuestionId() != null) {
                        exerciseCache.put(exercise.getQuestionId(), exercise);

                        // Populate difficulty-based cache
                        String difficulty = validateDifficulty(exercise.getDifficulty()); // Ensure it's a valid difficulty
                        tempDifficultyExerciseMap.get(difficulty).add(exercise.getQuestionId());
                    }
                } catch (Exception e) {
                    logger.warn("Failed to process row {} while building caches: {}", row.getRowNum(), e.getMessage());
                }
            }
            // Once all rows are processed, put the temporary map into the final cache
            difficultyExerciseCache.putAll(tempDifficultyExerciseMap);

            cacheInitialized = true;
            logger.info("Exercise caches initialized successfully. Total exercises: {}", exerciseCache.size());
            VALID_DIFFICULTIES.forEach(diff ->
                    logger.info("Exercises in '{}' difficulty: {}", diff, difficultyExerciseCache.get(diff).size())
            );

        } catch (IOException e) {
            logger.error("Failed to load exercise file from {}: {}", filePath, e.getMessage());
            throw new IllegalStateException("Exercise service initialization failed: Cannot read exercise data.", e);
        } catch (Exception e) {
            logger.error("An unexpected error occurred during exercise cache initialization: {}", e.getMessage(), e);
            throw new IllegalStateException("Exercise service initialization failed unexpectedly.", e);
        }
    }

    /**
     * Selects a personalized exercise ID for a user based on a two-question evaluation cycle.
     *
     * @param userId The ID of the user.
     * @return The ID of the selected exercise, or null if no exercise can be found.
     */
    public String selectPersonalizedExerciseId(String userId) {
        if (userId == null || userId.isEmpty()) {
            logger.warn("Null or empty user ID provided for personalized exercise selection. Serving random exercise.");
            return getRandomExerciseId(); // Fallback to a random exercise
        }

        // Initialize or retrieve the user's session state.
        // If a new session, ask Q-agent for initial difficulty.
        UserSessionState sessionState = userSessionStates.computeIfAbsent(userId, k -> {
            String initialDifficulty = qLearningAgent.determineNextDifficulty(userId);
            logger.info("New session started for user {}. Q-agent initial recommended difficulty: {}", userId, initialDifficulty);
            return new UserSessionState(initialDifficulty);
        });

        // Determine the difficulty for the current question based on the cycle state
        String currentCycleDifficulty;
        if (sessionState.questionsAnsweredInCycle < QUESTIONS_PER_EVALUATION_CYCLE) {
            // Within the current cycle, use the difficulty determined at the start of the cycle
            currentCycleDifficulty = sessionState.currentCycleDifficulty;
            logger.debug("User {} (Cycle Q#{}): Continuing with cycle difficulty: {}",
                    userId, sessionState.questionsAnsweredInCycle + 1, currentCycleDifficulty);
        } else {
            // Cycle just completed (or somehow questionsAnsweredInCycle got ahead),
            // The Q-agent has already determined the *next* difficulty when the previous cycle ended.
            // Reset the session state for the new cycle using the difficulty determined by Q-agent
            // from the *last* cycle's performance.
            String nextRecommendedDifficulty = qLearningAgent.determineNextDifficulty(userId); // This will return the difficulty updated by last cycle's performance
            sessionState.resetCycle(nextRecommendedDifficulty);
            currentCycleDifficulty = sessionState.currentCycleDifficulty;
            logger.info("User {} (Cycle Q#1): Starting NEW cycle with Q-agent recommended difficulty: {}",
                    userId, currentCycleDifficulty);
        }


        // Get a random exercise ID from the determined difficulty pool
        String selectedExerciseId = getRandomExerciseIdForDifficulty(currentCycleDifficulty);

        if (selectedExerciseId == null) {
            logger.warn("No exercise found for cycle difficulty '{}' for user {}. Falling back to random.",
                    currentCycleDifficulty, userId);
            selectedExerciseId = getRandomExerciseId(); // Fallback if no exercises for the recommended difficulty
        }

        return selectedExerciseId;
    }

    /**
     * Gets a personalized ExerciseDTO for a user. This is the primary public method
     * for serving exercises.
     *
     * @param userId The ID of the user.
     * @return An ExerciseDTO tailored for the user, or a random one if personalized selection fails.
     */
    public ExerciseDTO getExerciseForUser(String userId) {
        try {
            String exerciseId = selectPersonalizedExerciseId(userId); // Use the personalized selection logic
            if (exerciseId == null) {
                logger.warn("Failed to select personalized exercise ID for user {}. Serving random exercise.", userId);
                return getRandomExercise();
            }

            ExerciseDTO exercise = exerciseCache.get(exerciseId);
            if (exercise == null) {
                logger.warn("Exercise ID {} selected for user {} not found in cache. Serving random exercise.", exerciseId, userId);
                return getRandomExercise(); // Fallback if selected ID somehow isn't in cache
            }

            logExerciseServed(userId, exercise);
            return exercise;
        } catch (Exception e) {
            logger.error("Critical error getting exercise for user {}: {}", userId, e.getMessage(), e);
            return getRandomExercise(); // Robust fallback in case of unexpected errors
        }
    }

    /**
     * Records the result of an exercise and triggers the Q-learning agent to update its model
     * *if* the current evaluation cycle is complete.
     *
     * @param userId The ID of the user.
     * @param exerciseId The ID of the exercise completed.
     * @param score The score achieved by the user for the exercise.
     */
    public void recordExerciseResult(String userId, String exerciseId, double score) {
        if (userId == null || exerciseId == null) {
            logger.warn("Cannot record exercise result: userName or exerciseId is null.");
            return;
        }

        // Save the exercise history immediately
        ExerciseHistory history = new ExerciseHistory();
        history.setUserName(userId);
        history.setExerciseId(Integer.parseInt(exerciseId));
        history.setScore(score);
        history.setTimeDone(String.valueOf(System.currentTimeMillis()));
        exerciseHistoryRepository.save(history);
        logger.debug("Saved exercise history for user {}: QID {}, Score {}", userId, exerciseId, score);


        UserSessionState sessionState = userSessionStates.get(userId);
        if (sessionState == null) {
            logger.warn("No active session found for user {}. Initializing a new session state with default difficulty.", userId);
            // This can happen if a user restarts their application or session state is lost.
            // Re-initialize the session state for robustness.
            String initialDifficulty = qLearningAgent.determineNextDifficulty(userId);
            sessionState = new UserSessionState(initialDifficulty);
            userSessionStates.put(userId, sessionState);
        }

        sessionState.addScore(score);
        logger.info("Recorded result for user {} (Cycle Q#{}/{}): Exercise ID '{}', Score: {}",
                userId, sessionState.questionsAnsweredInCycle, QUESTIONS_PER_EVALUATION_CYCLE, exerciseId, score);

        // Check if the evaluation cycle is complete
        if (sessionState.questionsAnsweredInCycle >= QUESTIONS_PER_EVALUATION_CYCLE) {
            double averageScore = sessionState.getAverageScore();
            String difficultyForCycle = sessionState.currentCycleDifficulty; // This is the difficulty that was *served* in this cycle

            logger.info("Evaluation cycle complete for user {}. Average score: {}. Difficulty served: {}",
                    userId, averageScore, difficultyForCycle);

            try {
                // IMPORTANT: Pass the average score and the difficulty that was *served* for this cycle.
                // The Q-learning agent will use this to update its model and determine the *next* optimal difficulty.
                qLearningAgent.updateQValuesAfterCycle(userId, averageScore, difficultyForCycle);
                logger.info("Q-values updated for user {} based on cycle average score. Next difficulty determined by Q-agent.", userId);

                // Reset the cycle for the user, getting the new recommended difficulty for the NEXT cycle
                // This call to determineNextDifficulty will now reflect the Q-agent's adjustment from the update.
                String nextRecommendedDifficulty = qLearningAgent.determineNextDifficulty(userId);
                sessionState.resetCycle(nextRecommendedDifficulty);
                logger.info("User {} session reset. Next cycle will target difficulty: {}", userId, nextRecommendedDifficulty);

            } catch (Exception e) {
                logger.error("Error updating Q-values or resetting session after cycle for user {}: {}", userId, e.getMessage(), e);
                // In case of error, reset session to default difficulty to prevent stuck state
                sessionState.resetCycle(DEFAULT_DIFFICULTY);
            }
        }
    }

    /**
     * **NEW FUNCTION:** Handles explicit user feedback for a given exercise.
     * Delegates the Q-value update based on feedback to the QLearningAgent.
     *
     * @param userId The ID of the user providing feedback.
     * @param exerciseId The ID of the exercise the user is providing feedback on.
     * @param feedbackType "Like" or "Dislike".
     */
    public void recordUserFeedback(String userId, String exerciseId, String feedbackType) {
        if (userId == null || exerciseId == null || feedbackType == null) {
            logger.warn("Cannot record user feedback: userName, exerciseId, or feedbackType is null.");
            return;
        }

        ExerciseDTO exercise = exerciseCache.get(exerciseId);
        if (exercise == null) {
            logger.error("Exercise with ID {} not found in cache when recording feedback for user {}. Cannot process feedback.", exerciseId, userId);
            throw new IllegalArgumentException("Exercise not found for feedback.");
        }
        String exerciseDifficulty = exercise.getDifficulty(); // Get the difficulty of the exercise

        logger.info("User {}: Received feedback '{}' for exercise {} (Difficulty: {}).",
                userId, feedbackType, exerciseId, exerciseDifficulty);

        // Delegate the feedback processing to the QLearningAgent
        qLearningAgent.updateQValuesBasedOnFeedback(userId, exerciseDifficulty, feedbackType);
        logger.info("User {}: Feedback processed by Q-Learning agent.", userId);
    }


    public ExerciseDTO getExerciseById(String questionId) {
        if (questionId == null) {
            return null;
        }
        return exerciseCache.get(questionId);
    }

    private ExerciseDTO buildExerciseDTO(Row row, DataFormatter formatter) {
        if (row == null) return null;

        try {
            ExerciseDTO dto = new ExerciseDTO();
            dto.setQuestionId(getCellValue(row.getCell(0), formatter));
            dto.setTitle(getCellValue(row.getCell(1), formatter));
            dto.setDescription(getCellValue(row.getCell(2), formatter));
            dto.setHint(getCellValue(row.getCell(3), formatter));
            dto.setDifficulty(getCellValue(row.getCell(4), formatter));
            // Assuming your Excel might have sample input/output, add columns for them if they exist
            // dto.setSampleInput(getCellValue(row.getCell(5), formatter));
            // dto.setSampleOutput(getCellValue(row.getCell(6), formatter));
            return dto;
        } catch (Exception e) {
            logger.warn("Error building ExerciseDTO from row {}: {}", row.getRowNum(), e.getMessage());
            return null;
        }
    }

    /**
     * Extracts a cell value as a trimmed string.
     * @param cell The cell to extract value from.
     * @param formatter The DataFormatter.
     * @return The cell value as a string, or an empty string if cell is null.
     */
    private String getCellValue(Cell cell, DataFormatter formatter) {
        return cell != null ? formatter.formatCellValue(cell).trim() : "";
    }

    /**
     * Retrieves a completely random exercise from the entire cache.
     * Used as a fallback.
     * @return A random ExerciseDTO, or null if cache is empty.
     */
    public ExerciseDTO getRandomExercise() {
        if (exerciseCache.isEmpty()) {
            logger.warn("Exercise cache is empty. Cannot get random exercise.");
            return null;
        }
        List<String> keys = new ArrayList<>(exerciseCache.keySet());
        String randomKey = keys.get(new Random().nextInt(keys.size()));
        return exerciseCache.get(randomKey);
    }

    /**
     * Retrieves a random exercise ID from the entire cache.
     * @return A random exercise ID, or null if cache is empty.
     */
    private String getRandomExerciseId() {
        ExerciseDTO exercise = getRandomExercise();
        return exercise != null ? exercise.getQuestionId() : null;
    }

    /**
     * Retrieves a random exercise ID specifically for a given difficulty.
     * @param difficulty The desired difficulty level.
     * @return A random exercise ID of that difficulty, or a random exercise ID from any difficulty as a fallback.
     */
    private String getRandomExerciseIdForDifficulty(String difficulty) {
        String validatedDifficulty = validateDifficulty(difficulty);
        List<String> exercises = difficultyExerciseCache.get(validatedDifficulty);

        if (exercises == null || exercises.isEmpty()) {
            logger.warn("No exercises available for validated difficulty: {}. Falling back to completely random exercise.", validatedDifficulty);
            return getRandomExerciseId(); // Fall back to completely random selection
        }

        return exercises.get(new Random().nextInt(exercises.size()));
    }

    /**
     * Provides a list of personalized exercises for a user based on their current Q-learning recommended difficulty.
     * This method ensures the returned exercises are distinct and limited by the count.
     *
     * IMPORTANT: This method's interaction with QLearningAgent should be reconsidered.
     * If `getExerciseForUser` is the primary way to get exercises for the Q-learning cycle,
     * then this method might be for a separate "practice" mode that doesn't affect Q-learning state.
     * As currently written, it calls `determineRecommendedProficiencyLevel`, which might be a duplicate
     * or conflicting call if `getExerciseForUser` is also being used.
     *
     * For now, I've left its call to `determineRecommendedProficiencyLevel` as is,
     * assuming it's for a different, non-cycle-based exercise retrieval.
     * Ensure `QLearningAgent` has `determineRecommendedProficiencyLevel` method.
     *
     * @param userId The ID of the user.
     * @param count The number of personalized exercises to retrieve.
     * @return A list of ExerciseDTOs.
     */
    public List<ExerciseDTO> getPersonalizedExercises(String userId, int count) {
        if (userId == null || count <= 0) {
            return Collections.emptyList();
        }

        try {
            // Updated: Using qLearningAgent.evaluateUserProficiency() instead of determineNextDifficulty
            // to get a user's general proficiency for displaying a list, not to advance the cycle.
            String recommendedProficiency = qLearningAgent.evaluateUserProficiency(userId);
            String validatedDifficulty = validateDifficulty(recommendedProficiency);

            List<String> exerciseIdsForDifficulty = difficultyExerciseCache.get(validatedDifficulty);
            if (exerciseIdsForDifficulty == null || exerciseIdsForDifficulty.isEmpty()) {
                logger.warn("No exercises found for recommended proficiency '{}' for user {}. Attempting fallback difficulties.", validatedDifficulty, userId);

                // Fallback logic if the primary recommended difficulty has no exercises
                List<String> orderedDifficulties = new ArrayList<>(VALID_DIFFICULTIES);
                int currentIndex = orderedDifficulties.indexOf(validatedDifficulty);
                if (currentIndex == -1) currentIndex = orderedDifficulties.indexOf(DEFAULT_DIFFICULTY); // Fallback if initial validated difficulty is also invalid

                // Try current, then easier, then harder (or cycle through)
                for (int i = 0; i < VALID_DIFFICULTIES.size(); i++) {
                    String difficultyToTry = orderedDifficulties.get(Math.abs(currentIndex + i) % orderedDifficulties.size());
                    exerciseIdsForDifficulty = difficultyExerciseCache.get(difficultyToTry);
                    if (exerciseIdsForDifficulty != null && !exerciseIdsForDifficulty.isEmpty()) {
                        logger.info("Found exercises from fallback difficulty '{}' for user {}.", difficultyToTry, userId);
                        break;
                    }
                }

                if (exerciseIdsForDifficulty == null || exerciseIdsForDifficulty.isEmpty()) {
                    logger.warn("Still no exercises found for any difficulty for user {}. Returning empty list.", userId);
                    return Collections.emptyList();
                }
            }

            List<String> shuffledIds = new ArrayList<>(exerciseIdsForDifficulty);
            Collections.shuffle(shuffledIds);

            return shuffledIds.stream()
                    .limit(count)
                    .map(exerciseCache::get)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());
        } catch (Exception e) {
            logger.error("Error getting personalized exercises for user {}: {}", userId, e.getMessage(), e);
            return Collections.emptyList();
        }
    }


    /**
     * Updates a user's proficiency string directly based on new score and difficulty.
     * This method is separate from the Q-learning cycle updates and might be used
     * for administrative or direct user profile modifications.
     * The QLearningAgent itself determines the proficiency from the Q-table for its internal logic.
     *
     * @param userId The ID of the user.
     * @param score The score from the latest exercise.
     * @param questionDifficulty The difficulty of the question associated with the score.
     */
    public void updateUserProficiency(String userId, double score, String questionDifficulty) {
        AppUser user = userRepository.findByUserName(userId).orElse(null);
        if (user != null) {
            String currentProficiency = user.getProficiency();
            int currentProficiencyIndex = VALID_DIFFICULTIES.indexOf(validateDifficulty(currentProficiency));

            int newProficiencyIndex;
            if (score >= 80) { // Good score
                newProficiencyIndex = Math.min(currentProficiencyIndex + 1, VALID_DIFFICULTIES.size() - 1);
            } else if (score <= 50) { // Poor score
                newProficiencyIndex = Math.max(currentProficiencyIndex - 1, 0);
            } else { // Moderate score
                newProficiencyIndex = currentProficiencyIndex;
            }

            String newProficiency = VALID_DIFFICULTIES.get(newProficiencyIndex);
            user.setProficiency(newProficiency);
            userRepository.save(user);
            logger.info("User {} proficiency updated from {} to {}", userId, currentProficiency, newProficiency);
        } else {
            logger.warn("User not found: {} for proficiency update.", userId);
        }
    }

    /**
     * Logs the details of an exercise being served to a user.
     * @param userId The ID of the user.
     * @param exercise The ExerciseDTO being served.
     */
    private void logExerciseServed(String userId, ExerciseDTO exercise) {
        logger.info("Serving exercise to user {}: [{}] '{}' (Difficulty: {})",
                userId,
                exercise.getQuestionId(),
                exercise.getTitle(),
                exercise.getDifficulty());
    }

    /**
     * Validates a given difficulty string against the list of VALID_DIFFICULTIES.
     * Returns the original difficulty if valid, otherwise returns DEFAULT_DIFFICULTY.
     * @param difficulty The difficulty string to validate.
     * @return A validated difficulty string.
     */
    private String validateDifficulty(String difficulty) {
        return difficulty != null && VALID_DIFFICULTIES.contains(difficulty)
                ? difficulty
                : DEFAULT_DIFFICULTY;
    }
}