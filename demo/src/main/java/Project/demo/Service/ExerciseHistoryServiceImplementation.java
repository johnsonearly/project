package Project.demo.Service;

import Project.demo.Entity.ExerciseHistory;
import Project.demo.Repositories.ExerciseHistoryInterface;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ExerciseHistoryServiceImplementation {
    @Autowired
    private ExerciseHistoryInterface exerciseHistoryInterface;

    public ExerciseHistory createExerciseHistory(ExerciseHistory history){
        exerciseHistoryInterface.save(history);
        return history;
    }
    public List<ExerciseHistory> fetchHistory(String userId){
        return exerciseHistoryInterface.findAllByUserId(userId);
    }

    public double averageScore(String userId){
        List<ExerciseHistory> history = fetchHistory(userId);
        double sum = 0;
        for (ExerciseHistory exerciseHistory : history) {
            sum += exerciseHistory.getScore();
        }
        return sum / history.size();
    }

}
