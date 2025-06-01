package Project.demo.Service;

import Project.demo.Entity.AppUser;
import Project.demo.Entity.Pretest;
import Project.demo.Repositories.AppUserInterface;
import Project.demo.Repositories.PretestInterface;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class PretestServiceImplementation {

    @Autowired
    private PretestInterface pretestInterface;

    @Autowired
    private AppUserInterface appUserInterface;

    public void createPretest(Pretest pretest, String userName) {
        Optional<Pretest> pretest1 = Optional.ofNullable(pretestInterface.findByUserName(userName));
        pretest1.ifPresent(value -> pretestInterface.delete(value));
        pretestInterface.save(pretest);
        float averageScore = pretest.getAverageScore();
        Optional<AppUser> appUser = appUserInterface.findByUserName(userName);
        if (appUser.isPresent()) {
            if (averageScore <= 30) {
                appUser.get().setProficiency("Beginner");
                appUserInterface.save(appUser.get());
            } else if (averageScore > 30 && averageScore <= 50) {
                appUser.get().setProficiency("Intermediate");
                appUserInterface.save(appUser.get());
            }
            appUser.get().setProficiency("Advanced");
            appUserInterface.save(appUser.get());
        }
    }


}
