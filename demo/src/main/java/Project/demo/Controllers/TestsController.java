package Project.demo.Controllers;

import Project.demo.Entity.PostTest;
import Project.demo.Entity.Pretest;
import Project.demo.Service.PosttestServiceImplementation;
import Project.demo.Service.PretestServiceImplementation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("api/v1")
@CrossOrigin(origins = "https://learning-system-back-end-nzlz.vercel.app/")
public class TestsController {
    @Autowired
    private PretestServiceImplementation pretestServiceImplementation;

    @Autowired
    private PosttestServiceImplementation posttestServiceImplementation;

    @GetMapping("/evaluateScores/{userName}")
    public String evaluateScores(@PathVariable String userName){
        return posttestServiceImplementation.evaluatePosttest(userName);

    }

    // To create the Post test entity

    @PostMapping("/createPostTest/{userName}")
    public void createPostTest(@RequestBody float averageScore, @PathVariable String userName){
        PostTest postTest = new PostTest();
        postTest.setAverageScore(averageScore);
        postTest.setUserName(userName);
        posttestServiceImplementation.createPosttest(userName, postTest);
        posttestServiceImplementation.updateUserProficiency(userName);
    }

    @PostMapping("/createPreTest/{userName}")
    public void createPreTest(@RequestBody float averageScore, @PathVariable String userName){
        Pretest pretest = new Pretest();
        pretest.setAverageScore(averageScore);
        pretest.setUserName(userName);
        pretestServiceImplementation.createPretest(pretest,userName);
        pretestServiceImplementation.updateUserProficiency(userName);
    }



}
