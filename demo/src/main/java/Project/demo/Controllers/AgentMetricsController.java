package Project.demo.Controllers;

import Project.demo.Component.QLearningAgent;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Objects;

@RestController
@CrossOrigin(origins = "https://learning-system-back-end-nzlz.vercel.app/")
@RequestMapping("api/v1")
public class AgentMetricsController {
    private final QLearningAgent qLearningAgent;

    public AgentMetricsController(QLearningAgent qLearningAgent) {
        this.qLearningAgent = qLearningAgent;
    }

    @GetMapping("/getmetrics")
    public Map<String, Object> getAgentEvaluationMetrics() {

            return qLearningAgent.getEvaluationMetrics();

    }

    @GetMapping("/reset")
    public String resetAgentMetrics() {
        qLearningAgent.resetEvaluationMetrics();
        return "QLearning Agent evaluation metrics reset successfully.";
    }
}
