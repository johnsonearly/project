package Project.demo.DTOs;

import lombok.*;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class CodeSubmission {
    private String userId;
    private String questionId;
    private String code;
    private String difficultyLevel;
    private String timeSpent;
    private String difficulty;

}
