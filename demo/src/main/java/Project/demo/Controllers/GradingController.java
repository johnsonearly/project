package Project.demo.Controllers;

import Project.demo.DTOs.CodeSubmission;
import Project.demo.DTOs.GradingResult;
import Project.demo.Entity.ExerciseHistory;
import Project.demo.Responses.SuccessMessage;
import Project.demo.Service.ExerciseHistoryServiceImplementation;
import Project.demo.Service.GradingService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("api/v1/grade-submission")
@CrossOrigin(origins = "http://localhost:5173")
public class GradingController {

    @Autowired
    private GradingService gradingService;

    @Autowired
    private ExerciseHistoryServiceImplementation historyServiceImplementation;
    @PostMapping("/submit-code")
    public ResponseEntity<SuccessMessage<GradingResult>> gradeUser(@RequestBody CodeSubmission codeSubmission) {
        ExerciseHistory history = new ExerciseHistory();
        history.setUserId(codeSubmission.getUserId());
        history.setTimeDone(codeSubmission.getTimeSpent());
        history.setExerciseId(Integer.parseInt(codeSubmission.getQuestionId()));
        history.setScore(gradingService.grade(codeSubmission).getScore());
        history.setDifficulty(codeSubmission.getDifficulty());
        historyServiceImplementation.createExerciseHistory(history);

        SuccessMessage<GradingResult> response = new SuccessMessage<>(
                "User successfully graded",
                "200",
                gradingService.grade(codeSubmission)
        );

        return ResponseEntity.ok(response);
    }
}