package Project.demo.Repositories;

import Project.demo.Entity.Exercise;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ExerciseInterface extends JpaRepository<Exercise, Integer> {
}
