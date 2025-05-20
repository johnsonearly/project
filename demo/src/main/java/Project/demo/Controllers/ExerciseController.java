package Project.demo.Controllers;

import Project.demo.Component.ExerciseServiceImpl;
import Project.demo.DTOs.ExerciseDTO;
import Project.demo.DTOs.ExerciseResultDTO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/exercises")
@CrossOrigin(origins = "http://localhost:5173")
public class ExerciseController {

    @Autowired
    private ExerciseServiceImpl exerciseService;

    // Get completely random exercise from any difficulty
    @GetMapping("/random")
    public ResponseEntity<ExerciseDTO> getRandomExercise() {
        try {
            ExerciseDTO exercise = exerciseService.getRandomExercise();
            return exercise != null ?
                    ResponseEntity.ok(exercise) :
                    ResponseEntity.notFound().build();
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    // Get a personalized exercise for a user (uses Q-learning)
    @GetMapping("/personalized/{userId}")
    public ResponseEntity<ExerciseDTO> getPersonalizedExercise(@PathVariable String userId) {
        try {
            ExerciseDTO exercise = exerciseService.getExerciseForUser(userId);
            return exercise != null ?
                    ResponseEntity.ok(exercise) :
                    ResponseEntity.notFound().build();
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    // Get multiple personalized exercises for a user
    @GetMapping("/personalized/{userId}/batch")
    public ResponseEntity<List<ExerciseDTO>> getPersonalizedExercises(
            @PathVariable String userId,
            @RequestParam(defaultValue = "5") int count) {
        try {
            List<ExerciseDTO> exercises = exerciseService.getPersonalizedExercises(userId, count);
            return !exercises.isEmpty() ?
                    ResponseEntity.ok(exercises) :
                    ResponseEntity.notFound().build();
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    // Get a random exercise by specific difficulty level
//    @GetMapping("/difficulty/{level}")
//    public ResponseEntity<ExerciseDTO> getExerciseByDifficulty(@PathVariable String level) {
//        try {
//            ExerciseDTO exercise = exerciseService.getRandomExerciseByDifficulty(level);
//            return exercise != null ?
//                    ResponseEntity.ok(exercise) :
//                    ResponseEntity.notFound().build();
//        } catch (Exception e) {
//            return ResponseEntity.internalServerError().build();
//        }
//    }

    // Submit exercise results to update Q-learning model
    @PostMapping("/submit")
    public ResponseEntity<Void> recordExerciseResult(@RequestBody ExerciseResultDTO result) {
        try {
            exerciseService.recordExerciseResult(
                    result.getUserId(),
                    result.getExerciseId(),
                    result.getScore()
            );
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    // Get exercise by ID
    @GetMapping("/{exerciseId}")
    public ResponseEntity<ExerciseDTO> getExerciseById(@PathVariable String exerciseId) {
        try {
            ExerciseDTO exercise = exerciseService.getExerciseById(exerciseId);
            return exercise != null ?
                    ResponseEntity.ok(exercise) :
                    ResponseEntity.notFound().build();
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }
}