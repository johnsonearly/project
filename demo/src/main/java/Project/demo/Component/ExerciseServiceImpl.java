package Project.demo.Component;

import Project.demo.DTOs.ExerciseDTO;
import Project.demo.Interfaces.ExerciseService;
import Project.demo.Interfaces.QLearningExerciseProvider;
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

    private static final int INITIAL_RANDOM_QUESTIONS = 5;
    private static final Logger logger = LoggerFactory.getLogger(ExerciseServiceImpl.class);
    private static final List<String> VALID_DIFFICULTIES = List.of("Beginner", "Intermediate", "Advanced");

    private final String filePath = "utils/exercises.xlsx";
    private final QLearningAgent qLearningAgent;
    private final Map<String, Integer> userQuestionCounts = new ConcurrentHashMap<>();

    private final Map<String, List<String>> difficultyExerciseCache = new HashMap<>();
    private final Map<String, ExerciseDTO> exerciseCache = new HashMap<>();
    private boolean cacheInitialized = false;


    @Autowired
    public ExerciseServiceImpl(@Lazy QLearningAgent qLearningAgent) {
        this.qLearningAgent = qLearningAgent;
        initializeExerciseCaches();
    }

    private synchronized void initializeExerciseCaches() {
        if (cacheInitialized) return;

        try {
            // Initialize difficulty-based exercise cache
            VALID_DIFFICULTIES.forEach(difficulty -> {
                List<String> exercises = loadExercisesForDifficulty(difficulty);
                if (!exercises.isEmpty()) {
                    difficultyExerciseCache.put(difficulty, exercises);
                }
            });

            // Initialize full exercise cache
            try (InputStream is = new ClassPathResource(filePath).getInputStream();
                 Workbook workbook = new XSSFWorkbook(is)) {

                Sheet sheet = workbook.getSheetAt(0);
                DataFormatter dataFormatter = new DataFormatter();

                for (Row row : sheet) {
                    if (row == null || row.getRowNum() == 0) continue;
                    try {
                        ExerciseDTO exercise = buildExerciseDTO(row, dataFormatter);
                        if (exercise != null && exercise.getQuestionId() != null) {
                            exerciseCache.put(exercise.getQuestionId(), exercise);
                        }
                    } catch (Exception e) {
                        logger.warn("Failed to process row {}: {}", row.getRowNum(), e.getMessage());
                    }
                }
            }
            cacheInitialized = true;
        } catch (Exception e) {
            logger.error("Failed to initialize exercise caches: {}", e.getMessage());
            throw new IllegalStateException("Exercise service initialization failed", e);
        }
    }

    private List<String> loadExercisesForDifficulty(String difficulty) {
        List<String> exercises = new ArrayList<>();
        if (difficulty == null || !VALID_DIFFICULTIES.contains(difficulty)) {
            return exercises;
        }

        try (InputStream is = new ClassPathResource(filePath).getInputStream();
             Workbook workbook = new XSSFWorkbook(is)) {

            Sheet sheet = workbook.getSheetAt(0);
            DataFormatter dataFormatter = new DataFormatter();

            for (Row row : sheet) {
                if (row == null || row.getRowNum() == 0) continue;

                Cell idCell = row.getCell(0);
                Cell difficultyCell = row.getCell(4);

                if (idCell == null || difficultyCell == null) continue;

                String questionId = dataFormatter.formatCellValue(idCell).trim();
                String exerciseDifficulty = dataFormatter.formatCellValue(difficultyCell).trim();

                if (exerciseDifficulty.equalsIgnoreCase(difficulty)) {
                    exercises.add(questionId);
                }
            }
        } catch (IOException e) {
            logger.error("Failed to load exercises for difficulty {}: {}", difficulty, e.getMessage());
        }
        return exercises;
    }


    public List<String> getAvailableActions(String difficultyLevel) {
        String validatedDifficulty = validateDifficulty(difficultyLevel);
        return Collections.unmodifiableList(
                difficultyExerciseCache.getOrDefault(validatedDifficulty, Collections.emptyList())
        );
    }


    public String selectExercise(String userId) {
        if (userId == null || userId.isEmpty()) {
            logger.warn("Null or empty user ID provided");
            return getRandomExerciseId();
        }

        try {
            // Track how many questions this user has received
            int questionCount = userQuestionCounts.getOrDefault(userId, 0) + 1;
            userQuestionCounts.put(userId, questionCount);

            // For the first few questions, select randomly within difficulty level
            if (questionCount <= INITIAL_RANDOM_QUESTIONS) {
                String currentDifficulty = qLearningAgent.getCurrentDifficulty(userId);
                logger.debug("Selecting random initial exercise for user {} (question #{})", userId, questionCount);
                return getRandomExerciseIdForDifficulty(currentDifficulty);
            }

            // After initial questions, use Q-learning to select optimal difficulty
            String recommendedDifficulty = qLearningAgent.determineNextDifficulty(userId);
            logger.debug("Q-learning recommended difficulty: {} for user {}", recommendedDifficulty, userId);

            return getRandomExerciseIdForDifficulty(recommendedDifficulty);
        } catch (Exception e) {
            logger.error("Error selecting exercise for user {}: {}", userId, e.getMessage());
            return getRandomExerciseId();
        }
    }


    public ExerciseDTO getExerciseForUser(String userId) {
        try {
            String exerciseId = selectExercise(userId);
            if (exerciseId == null) {
                logger.warn("Null exercise ID generated for user {}", userId);
                return getRandomExercise();
            }

            ExerciseDTO exercise = exerciseCache.get(exerciseId);
            if (exercise != null) {
                logExerciseServed(userId, exercise);
                return exercise;
            }

            logger.warn("Exercise ID {} not found in cache", exerciseId);
            return getRandomExercise();
        } catch (Exception e) {
            logger.error("Error getting exercise for user {}: {}", userId, e.getMessage());
            return getRandomExercise();
        }
    }
    public void recordExerciseResult(String userId, String exerciseId, double score) {
        try {
            ExerciseDTO exercise = getExerciseById(exerciseId);
            if (exercise == null) {
                logger.warn("Could not record result for unknown exercise ID: {}", exerciseId);
                return;
            }

            // Update Q-values based on this exercise result
            qLearningAgent.updateQValues(userId);

            logger.info("Recorded exercise result for user {}: {} scored {} (difficulty: {})",
                    userId, exerciseId, score, exercise.getDifficulty());
        } catch (Exception e) {
            logger.error("Error recording exercise result for user {}: {}", userId, e.getMessage());
        }
    }




    public String getRandomAction(String difficultyLevel) {
        List<String> availableActions = getAvailableActions(difficultyLevel);
        if (availableActions.isEmpty()) {
            return null;
        }
        return availableActions.get(new Random().nextInt(availableActions.size()));
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
            return dto;
        } catch (Exception e) {
            logger.warn("Error building ExerciseDTO from row {}: {}", row.getRowNum(), e.getMessage());
            return null;
        }
    }

    private String getCellValue(Cell cell, DataFormatter formatter) {
        return cell != null ? formatter.formatCellValue(cell).trim() : "";
    }


    public ExerciseDTO getRandomExercise() {
        if (exerciseCache.isEmpty()) {
            return null;
        }
        List<String> keys = new ArrayList<>(exerciseCache.keySet());
        String randomKey = keys.get(new Random().nextInt(keys.size()));
        return exerciseCache.get(randomKey);
    }

    private String getRandomExerciseId() {
        ExerciseDTO exercise = getRandomExercise();
        return exercise != null ? exercise.getQuestionId() : null;
    }

    private String getRandomExerciseIdForDifficulty(String difficulty) {
        // 1. Get all exercises for the recommended difficulty level
        List<String> exercises = difficultyExerciseCache.get(validateDifficulty(difficulty));

        // 2. Fallback if no exercises exist for this difficulty
        if (exercises == null || exercises.isEmpty()) {
            logger.warn("No exercises available for difficulty: {}", difficulty);
            return getRandomExerciseId(); // Fall back to completely random selection
        }

        // 3. Return a random exercise ID from this difficulty level
        return exercises.get(new Random().nextInt(exercises.size()));
    }

    public List<ExerciseDTO> getPersonalizedExercises(String userId, int count) {
        if (userId == null || count <= 0) {
            return Collections.emptyList();
        }

        try {
            String difficulty = qLearningAgent.getCurrentDifficulty(userId);
            if (difficulty == null) {
                difficulty = "Beginner";
            }

            List<String> exerciseIds = difficultyExerciseCache.get(validateDifficulty(difficulty));
            if (exerciseIds == null || exerciseIds.isEmpty()) {
                return Collections.emptyList();
            }

            // Create a shuffled copy to avoid modifying the original list
            List<String> shuffledIds = new ArrayList<>(exerciseIds);
            Collections.shuffle(shuffledIds);

            return shuffledIds.stream()
                    .limit(Math.min(count, shuffledIds.size()))
                    .map(exerciseCache::get)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());
        } catch (Exception e) {
            logger.error("Error getting personalized exercises for user {}: {}", userId, e.getMessage());
            return Collections.emptyList();
        }
    }

    private void logExerciseServed(String userId, ExerciseDTO exercise) {
        logger.info("Serving exercise to user {}: [{}] {} (Difficulty: {})",
                userId,
                exercise.getQuestionId(),
                exercise.getTitle(),
                exercise.getDifficulty());
    }

    private String validateDifficulty(String difficulty) {
        return difficulty != null && difficultyExerciseCache.containsKey(difficulty)
                ? difficulty
                : "Beginner";
    }
}