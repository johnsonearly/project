package Project.demo.Service;

import Project.demo.Component.QLearningAgent;
import Project.demo.DTOs.LoginDTO;
import Project.demo.Entity.AppUser;
import Project.demo.Repositories.AppUserInterface;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.List;
import java.util.Optional;

@Service
public class AppUserServiceImplementation {

    @Autowired
    private AppUserInterface appUserInterface;
    @Autowired
    private QLearningAgent learningAgent;

    public AppUser createUser(AppUser appUser){
        appUserInterface.save(appUser);
        return  appUser;
    }

    public List<AppUser> getAllUsers() {
        return appUserInterface.findAll();
    }

    public Optional<AppUser> getAppUserById(int id) {
        return appUserInterface.findById(id);
    }
    public ResponseEntity<String> logUserIn(LoginDTO loginDTO) {
        Optional<AppUser> appUser = appUserInterface.findByUserId(loginDTO.getUserId());
        return appUser.map(user -> ResponseEntity.ok(user.getUserId())).orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).body("User not found with ID: " + loginDTO.getUserId()));
    }
    public void updateUser(String userId){
        String recommendedLevel = learningAgent.determineRecommendedProficiencyLevel(userId);
        Optional<AppUser> user = appUserInterface.findByUserId(userId);
        user.ifPresent(appUser -> appUser.setProficiency(recommendedLevel));
        System.out.println(recommendedLevel);
    }
    public AppUser getUser(String userId){
        Optional<AppUser> appUser = appUserInterface.findByUserId(userId);
        return appUser.get();
    }

}
