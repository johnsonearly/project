package Project.demo.DTOs;

import Project.demo.Enums.ProficiencyLevel;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class GradingResult {
    private double score;
    private String feedback;
    private String classification;
    private ProficiencyLevel userProficiencyLevel;
    private Map<String, Map<String, Double>> detailedScores;
}
