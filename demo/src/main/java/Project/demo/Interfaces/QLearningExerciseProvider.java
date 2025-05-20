package Project.demo.Interfaces;

import java.util.List;

public interface QLearningExerciseProvider {
    List<String> getAvailableActions(String difficultyLevel);
    String getRandomAction(String difficultyLevel);
}
