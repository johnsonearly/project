package Project.demo.Repositories;

import Project.demo.Entity.ExerciseHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ExerciseHistoryInterface extends JpaRepository<ExerciseHistory,Integer> {

    List<ExerciseHistory> findAllByUserName(String userId);
}
