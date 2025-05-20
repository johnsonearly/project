package Project.demo.Entity;

import Project.demo.Enums.ProficiencyLevel;
import jakarta.persistence.*;
import lombok.Data;

@Entity
@Data
public class Exercise {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int id;
    private String exerciseQuestion;
    private ProficiencyLevel proficiencyLevel;
    private String solutionApproach;




}
