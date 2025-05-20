package Project.demo.Service;

import Project.demo.DTOs.ResourcesDTO;
import jakarta.annotation.PostConstruct;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

@Service
public class ResourcesServiceImpl {

    private final List<ResourcesDTO> resourcesDTOS = new ArrayList<>();
    @PostConstruct
    public void init() throws IOException {
        // Load the Excel file from resources
        ClassPathResource resource = new ClassPathResource("utils/java_exercises_videos.xlsx");
        InputStream inputStream = resource.getInputStream();

        try (Workbook workbook = new XSSFWorkbook(inputStream)) {
            Sheet sheet = workbook.getSheetAt(0); // Get first sheet

            // Skip header row (row 0)
            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row != null) {
                    String title = getCellValue(row.getCell(0));
                    String url = getCellValue(row.getCell(1));
                    String level = getCellValue(row.getCell(2));

                    resourcesDTOS.add(new ResourcesDTO(title, url, level));
                }
            }
        }
    }
    private String getCellValue(Cell cell) {
        if (cell == null) {
            return "";
        }
        switch (cell.getCellType()) {
            case STRING:
                return cell.getStringCellValue();
            case NUMERIC:
                return String.valueOf(cell.getNumericCellValue());
            case BOOLEAN:
                return String.valueOf(cell.getBooleanCellValue());
            default:
                return "";
        }
    }
    public List<ResourcesDTO> getExercisesByLevel(String level) {
        List<ResourcesDTO> result = new ArrayList<>();
        for (ResourcesDTO exercise : resourcesDTOS) {
            if (exercise.getLevel().equalsIgnoreCase(level)) {
                result.add(exercise);
            }
        }
        return result;
    }

    public List<ResourcesDTO> getAllExercises() {
        return new ArrayList<>(resourcesDTOS);
    }

}
