package Project.demo.DTOs;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor

public class ResourcesDTO {
    private String title;
    private String url;
    private String level;
}
