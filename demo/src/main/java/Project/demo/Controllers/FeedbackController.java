package Project.demo.Controllers;

import Project.demo.Component.QLearningAgent;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("api/v1")
@CrossOrigin(origins = "https://learning-system-back-end-nzlz.vercel.app/")
public class FeedbackController {
    @Autowired
    private QLearningAgent learningAgent;

    @PostMapping("/feedback/{userName}/{difficulty}/{feedback}")
    public void update(@PathVariable String userId, @PathVariable String difficulty, @PathVariable String feedback){
        learningAgent.updateQValuesBasedOnFeedback(userId, difficulty, feedback);
    }
}
