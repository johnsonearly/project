package Project.demo.DTOs;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data

public class ExerciseDTO {
    private String questionId;
    private String title;
    private String description;
    private String hint;
    private String difficulty;
}
