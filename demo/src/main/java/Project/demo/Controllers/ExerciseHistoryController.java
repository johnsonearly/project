package Project.demo.Controllers;


import Project.demo.Entity.ExerciseHistory;
import Project.demo.Service.ExerciseHistoryServiceImplementation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("api/v1/history/")
@CrossOrigin(origins = "https://learning-system-back-end-nzlz.vercel.app/")
public class ExerciseHistoryController {
    @Autowired
    ExerciseHistoryServiceImplementation historyServiceImplementation;

    @GetMapping("{userId}")
    public List<ExerciseHistory> getHistory(@PathVariable String userId){
         System.out.println(historyServiceImplementation.fetchHistory(userId));
         return historyServiceImplementation.fetchHistory(userId);

    }
    @PostMapping
    public ExerciseHistory createHistory(@RequestBody ExerciseHistory exerciseHistory){
        return historyServiceImplementation.createExerciseHistory(exerciseHistory);
    }


}
