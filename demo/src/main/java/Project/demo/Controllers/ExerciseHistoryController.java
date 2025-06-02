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

    @GetMapping("{userName}")
    public List<ExerciseHistory> getHistory(@PathVariable String userName){
         System.out.println(historyServiceImplementation.fetchHistory(userName));
         return historyServiceImplementation.fetchHistory(userName);

    }
    @PostMapping
    public ExerciseHistory createHistory(@RequestBody ExerciseHistory exerciseHistory){
        return historyServiceImplementation.createExerciseHistory(exerciseHistory);
    }


}
