package Project.demo.Interfaces;

import Project.demo.DTOs.ExerciseDTO;

import java.util.List;

public interface ExerciseService {
    List<String> getAvailableActions(String difficultyLevel);
    ExerciseDTO getExerciseById(String questionId);
    String selectExercise(String userState);

    String getEffectiveDifficulty(String userId);

    String getRandomAction(String difficultyLevel);

    ExerciseDTO getRandomExercise();

    ExerciseDTO getAndLogExercise(String userState);
}
