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

    public void createPosttest(String userName, PostTest postTest){
        Optional<PostTest> postTest1 = Optional.ofNullable(posttestInterface.findByUserName(userName));
        if(postTest1.isPresent()){
            postTest1.get().setAverageScore(postTest.getAverageScore());
            posttestInterface.save(postTest1.get());
        }
        else {
            postTest.setUserName(userName);
        }
        posttestInterface.save(postTest);
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
    public void updateUserProficiency(String userName) {
        PostTest postTest = posttestInterface.findByUserName(userName);
        float averageScore = postTest.getAverageScore();
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
        }

    }
}

