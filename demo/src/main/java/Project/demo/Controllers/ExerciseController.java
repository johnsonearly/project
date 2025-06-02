package Project.demo.Controllers;

import Project.demo.Component.ExerciseServiceImpl;
import Project.demo.DTOs.ExerciseDTO;
import Project.demo.DTOs.ExerciseResultDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/exercises")
@CrossOrigin(origins = "https://learning-system-back-end-nzlz.vercel.app/") // Ensure your frontend's origin is allowed
public class ExerciseController {

    private static final Logger logger = LoggerFactory.getLogger(ExerciseController.class);

    private final ExerciseServiceImpl exerciseService;

    @Autowired
    public ExerciseController(ExerciseServiceImpl exerciseService) {
        this.exerciseService = exerciseService;
    }

    /**
     * Endpoint to get a completely random exercise from any difficulty level.
     * This serves as a fallback or for users who explicitly don't want personalized content.
     * GET /api/v1/exercises/random
     * @return ResponseEntity containing a random ExerciseDTO or a 404/500 status.
     */
    @GetMapping("/random")
    public ResponseEntity<ExerciseDTO> getRandomExercise() {
        try {
            logger.info("Request received for random exercise.");
            ExerciseDTO exercise = exerciseService.getRandomExercise();
            if (exercise != null) {
                logger.info("Serving random exercise: {}", exercise.getQuestionId());
                return ResponseEntity.ok(exercise);
            } else {
                logger.warn("No random exercise found. Exercise cache might be empty or issues during initialization.");
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build(); // 404 Not Found
            }
        } catch (Exception e) {
            logger.error("Error retrieving random exercise: {}", e.getMessage(), e);
            // Return 500 Internal Server Error with no body to prevent leaking internal details
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }


    /**
     * Endpoint to get a single personalized exercise for a specific user.
     * This leverages the Q-learning agent to determine the most suitable difficulty
     * for the *current question within a two-question evaluation cycle*.
     *
     * IMPORTANT: The frontend should call this endpoint *twice* for a given user
     * before calling the /submit endpoint, to complete one evaluation cycle.
     *
     * GET /api/v1/exercises/personalized/{userId}
     * @param userName The ID of the user for whom to get the exercise.
     * @return ResponseEntity containing a personalized ExerciseDTO or a 400/404/500 status.
     */
    @GetMapping("/personalized/{userName}")
    public ResponseEntity<ExerciseDTO> getPersonalizedExercise(@PathVariable String userName) {
        if (userName == null || userName.trim().isEmpty()) {
            logger.warn("Bad request: Personalized exercise requested with null or empty userId.");
            return ResponseEntity.badRequest().build(); // 400 Bad Request
        }

        try {
            logger.info("Request received for personalized exercise for user: {}", userName);
            // This method in service layer handles the Q-learning recommendation for the cycle
            ExerciseDTO exercise = exerciseService.getExerciseForUser(userName);
            if (exercise != null) {
                logger.info("Serving personalized exercise {} (Difficulty: {}) for user {}.",
                        exercise.getQuestionId(), exercise.getDifficulty(), userName);
                return ResponseEntity.ok(exercise);
            } else {
                logger.warn("Failed to retrieve personalized exercise for user {}. No exercise found even after fallbacks. Consider checking exercise data.", userName);
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build(); // 404 Not Found if no exercise can be retrieved
            }
        } catch (Exception e) {
            logger.error("Error retrieving personalized exercise for user {}: {}", userName, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build(); // 500 Internal Server Error
        }
    }



    @GetMapping("/personalized-list/{userId}/{count}")
    public ResponseEntity<List<ExerciseDTO>> getPersonalizedExerciseList(@PathVariable String userId,
                                                                         @PathVariable int count) {
        if (userId == null || userId.trim().isEmpty() || count <= 0) {
            logger.warn("Bad request: Personalized exercise list requested with invalid userId or count. userId: {}, count: {}", userId, count);
            return ResponseEntity.badRequest().build();
        }

        try {
            logger.info("Request received for personalized exercise list (count: {}) for user: {}", count, userId);
            List<ExerciseDTO> exercises = exerciseService.getPersonalizedExercises(userId, count);
            if (exercises.isEmpty()) {
                logger.info("No personalized exercises found for user {} with count {}. Returning 204 No Content.", userId, count);
                return ResponseEntity.status(HttpStatus.NO_CONTENT).build(); // 204 No Content
            }
            logger.info("Returning {} personalized exercises for user {}.", exercises.size(), userId);
            return ResponseEntity.ok(exercises); // 200 OK with the list of exercises
        } catch (Exception e) {
            logger.error("Error retrieving personalized exercise list for user {}: {}", userId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }


    /**
     * Endpoint to submit the result of a completed exercise.
     * This endpoint is crucial for the Q-learning process. The `ExerciseServiceImpl`
     * will track how many questions in the current cycle have been answered.
     * When the `QUESTIONS_PER_EVALUATION_CYCLE` (e.g., 2) questions are submitted,
     * the Q-learning agent will update its model based on the average performance
     * for that cycle and then determine the next difficulty level.
     *
     * IMPORTANT: The frontend should call this endpoint *after each question*
     * retrieved via `/personalized/{userId}`. The backend will handle when to trigger
     * the Q-learning update.
     *
     * POST /api/v1/exercises/submit
     * @param result An ExerciseResultDTO containing userId, exerciseId, and score.
     * @return ResponseEntity indicating success (200 OK) or failure (400 Bad Request, 500 Internal Server Error).
     */
    @PostMapping("/submit")
    public ResponseEntity<Void> recordExerciseResult(@RequestBody ExerciseResultDTO result) {
        // Basic validation for the incoming result data
        if (result == null || result.getUserId() == null || result.getUserId().trim().isEmpty() ||
                result.getExerciseId() == null || result.getExerciseId().trim().isEmpty() || result.getScore() < 0 || result.getScore() > 100) { // Added score range validation
            logger.warn("Bad request: Invalid exercise result submission. Result: {}", result);
            return ResponseEntity.badRequest().build(); // 400 Bad Request
        }

        try {
            logger.info("Exercise result submission received for user {}: Exercise ID '{}', Score: {}",
                    result.getUserId(), result.getExerciseId(), result.getScore());

            exerciseService.recordExerciseResult(
                    result.getUserId(),
                    result.getExerciseId(),
                    result.getScore()
            );
            logger.info("Exercise result recorded successfully for user {}. Service layer handled Q-learning update if cycle complete.", result.getUserId());
            return ResponseEntity.ok().build(); // 200 OK
        } catch (Exception e) {
            logger.error("Error recording exercise result for user {}: {}", result.getUserId(), e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build(); // 500 Internal Server Error
        }
    }
}