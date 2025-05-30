package Project.demo.Service;

import Project.demo.Entity.Pretest;
import Project.demo.Repositories.PretestInterface;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class PretestServiceImplementation {

    @Autowired
    private PretestInterface pretestInterface;

    public void createPretest(Pretest pretest, String userName){
        Optional<Pretest> pretest1 = Optional.ofNullable(pretestInterface.findByUserName(userName));
        if(pretest1.isPresent()){
            pretest1.get().setAverageScore(pretest.getAverageScore());
            pretestInterface.save(pretest1.get());
        }
        else{
            pretest.setUserName(userName);
        }

        pretestInterface.save(pretest);
    }

}
