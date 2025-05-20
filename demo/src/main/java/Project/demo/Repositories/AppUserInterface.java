package Project.demo.Repositories;

import Project.demo.Entity.AppUser;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AppUserInterface extends JpaRepository<AppUser,Integer> {
    Optional<AppUser> findByUserId(String userId);
}
