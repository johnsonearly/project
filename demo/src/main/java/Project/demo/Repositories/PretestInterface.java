package Project.demo.Repositories;

import Project.demo.Entity.Pretest;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PretestInterface extends JpaRepository<Pretest,Integer> {
    Pretest findByUserName(String userName);
}
