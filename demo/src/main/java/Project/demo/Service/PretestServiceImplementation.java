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
        pretest1.ifPresent(pretestInterface::delete);  // cleaner syntax

        pretestInterface.save(pretest);

        float averageScore = pretest.getAverageScore();
        Optional<AppUser> appUser = appUserInterface.findByUserName(userName);

        if (appUser.isPresent()) {
            AppUser user = appUser.get();

            if (averageScore <= 30) {
                user.setProficiency("Beginner");
            } else if (averageScore > 30 && averageScore <= 50) {
                user.setProficiency("Intermediate");
            } else  if( averageScore > 50){
                user.setProficiency("Advanced");
            }

            appUserInterface.save(user);
        }
    }



}
