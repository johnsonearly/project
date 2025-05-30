package Project.demo.Controllers;

import Project.demo.Component.QLearningAgent;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@CrossOrigin(origins = "https://learning-system-back-end-nzlz.vercel.app/")
public class AgentMetricsController {
    private final QLearningAgent qLearningAgent;

    public AgentMetricsController(QLearningAgent qLearningAgent) {
        this.qLearningAgent = qLearningAgent;
    }

    @GetMapping
    public Map<String, Object> getAgentEvaluationMetrics() {
        return qLearningAgent.getEvaluationMetrics();
    }

    @GetMapping("/reset")
    public String resetAgentMetrics() {
        qLearningAgent.resetEvaluationMetrics();
        return "QLearning Agent evaluation metrics reset successfully.";
    }
}
