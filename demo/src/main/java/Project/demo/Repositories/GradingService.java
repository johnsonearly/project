package Project.demo.Repositories;

import Project.demo.DTOs.CodeSubmission;
import Project.demo.DTOs.GradingResult;

public interface GradingService {
    GradingResult grade(CodeSubmission codeSubmission);
}
