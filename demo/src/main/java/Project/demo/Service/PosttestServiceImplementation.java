package Project.demo.Service;


import Project.demo.Entity.AppUser;
import Project.demo.Entity.PostTest;
import Project.demo.Entity.Pretest;
import Project.demo.Repositories.AppUserInterface;
import Project.demo.Repositories.PosttestInterface;
import Project.demo.Repositories.PretestInterface;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class PosttestServiceImplementation {
    @Autowired
    private PosttestInterface posttestInterface;

    @Autowired
    private PretestInterface pretestInterface;

    @Autowired
    private AppUserInterface appUserInterface;

    public void createPosttest( String userName,PostTest pretest) {
        Optional<PostTest> pretest1 = Optional.ofNullable(posttestInterface.findByUserName(userName));
        pretest1.ifPresent(posttestInterface::delete);  // cleaner syntax

        posttestInterface.save(pretest);

        float averageScore = pretest.getAverageScore();
        Optional<AppUser> appUser = appUserInterface.findByUserName(userName);

        if (appUser.isPresent()) {
            AppUser user = appUser.get();

            if (averageScore <= 30) {
                user.setProficiency("Beginner");
            } else if (averageScore > 30 && averageScore <= 50) {
                user.setProficiency("Intermediate");
            } else {
                user.setProficiency("Advanced");
            }

            appUserInterface.save(user);
        }
    }

    public String evaluatePosttest(String userName){
        PostTest postTest = posttestInterface.findByUserName(userName);
        Pretest pretest = pretestInterface.findByUserName(userName);
        float averageScore1 = postTest.getAverageScore();
        float averageScore2 = pretest.getAverageScore();

        if(averageScore1 > averageScore2){
            return " I can see someone has improved";

        }
        return "Aww you can do better";
    }

}

