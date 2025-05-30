package Project.demo.Repositories;

import Project.demo.Entity.PostTest;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PosttestInterface extends JpaRepository<PostTest,Integer> {

    PostTest findByUserName(String userName);
}
