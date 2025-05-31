package Project.demo.Entity;

import jakarta.persistence.*;
import lombok.Data;

import java.sql.Time;
import java.util.Date;

@Entity
@Data

public class ExerciseHistory {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int id;
    private String userName;
    private int exerciseId;
    private String timeDone;
    private double score;
    private String difficulty;


}
