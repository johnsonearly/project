package Project.demo.Component;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

@Component
public class RubricLoader {
    private static final Logger logger = LoggerFactory.getLogger(RubricLoader.class);
    private final ResourceLoader resourceLoader;

    // Constants for file paths
    private static final String EXERCISES_PATH = "classpath:utils/exercises.xlsx"; // Changed to xlsx
    private static final String RUBRIC_PATH = "classpath:utils/Rubric.xlsx";

    public RubricLoader(ResourceLoader resourceLoader) {
        this.resourceLoader = resourceLoader;
    }

    public Map<String, Double> loadRubric(String questionId) {
        Map<String, Double> rubric = new HashMap<>();

        try {
            Resource resource = resourceLoader.getResource(EXERCISES_PATH);
            logger.info("Loading exercises from: {}", resource.getURI());

            try (InputStream is = resource.getInputStream();
                 Workbook workbook = new XSSFWorkbook(is)) {

                Sheet sheet = workbook.getSheetAt(0); // First sheet

                // Find the row for the question
                for (Row row : sheet) {
                    if (row.getRowNum() == 0) continue; // Skip header

                    Cell questionIdCell = row.getCell(0); // First column - Question #
                    if (questionIdCell != null && getCellString(questionIdCell).trim().equals(questionId)) {
                        // Add rubric criteria based on difficulty
                        String difficulty = getCellString(row.getCell(4)); // 5th column - Difficulty
                        switch (difficulty.toLowerCase()) {
                            case "advanced":
                                rubric.put("problem_solving", 3.0);
                                rubric.put("critical_thinking", 3.0);
                                rubric.put("creativity", 3.0);
                                rubric.put("understanding_programming_logic", 3.0);
                                break;
                            case "intermediate":
                                rubric.put("problem_solving", 2.0);
                                rubric.put("critical_thinking", 2.0);
                                rubric.put("creativity", 2.0);
                                rubric.put("understanding_programming_logic", 2.0);
                                break;
                            default: // beginner
                                rubric.put("problem_solving", 1.0);
                                rubric.put("critical_thinking", 1.0);
                                rubric.put("creativity", 1.0);
                                rubric.put("understanding_programming_logic", 1.0);
                        }
                        break;
                    }
                }
            }
        } catch (IOException e) {
            logger.error("Error reading exercises Excel for question {}: {}", questionId, e.getMessage());
            return loadGeneralRubric();
        }

        if (rubric.isEmpty()) {
            return loadGeneralRubric();
        }
        return rubric;
    }

    public Map<String, Double> loadGeneralRubric() {
        Map<String, Double> rubric = new HashMap<>();

        try {
            Resource resource = resourceLoader.getResource(RUBRIC_PATH);
            logger.info("Loading general rubric from: {}", resource.getURI());

            try (InputStream is = resource.getInputStream();
                 Workbook workbook = new XSSFWorkbook(is)) {

                Sheet sheet = workbook.getSheetAt(0); // First sheet

                // We'll look for the header row to find the score columns
                Row headerRow = sheet.getRow(0);
                if (headerRow == null) {
                    logger.warn("No header row found in rubric Excel");
                    return getDefaultRubric();
                }

                // Find the column indices for the score levels
                int[] scoreColumns = new int[4]; // 0-3 scale
                for (Cell cell : headerRow) {
                    String header = getCellString(cell).toLowerCase();
                    if (header.contains("lacking")) {
                        scoreColumns[0] = cell.getColumnIndex();
                    } else if (header.contains("emerging")) {
                        scoreColumns[1] = cell.getColumnIndex();
                    } else if (header.contains("proficient")) {
                        scoreColumns[2] = cell.getColumnIndex();
                    } else if (header.contains("exceeding")) {
                        scoreColumns[3] = cell.getColumnIndex();
                    }
                }

                // Process each criteria row
                for (Row row : sheet) {
                    if (row.getRowNum() == 0) continue; // Skip header

                    Cell criteriaCell = row.getCell(0); // First column - Criteria
                    if (criteriaCell != null) {
                        String criteria = getCellString(criteriaCell).trim();
                        if (!criteria.isEmpty()) {
                            // Add all score levels for this criteria
                            for (int i = 0; i < scoreColumns.length; i++) {
                                rubric.put(criteria + "_" + i, (double) i);
                            }
                        }
                    }
                }
            }
        } catch (IOException e) {
            logger.error("Error reading general rubric Excel: {}", e.getMessage());
            return getDefaultRubric();
        }

        return rubric;
    }

    private Map<String, Double> getDefaultRubric() {
        Map<String, Double> defaultRubric = new HashMap<>();
        defaultRubric.put("problem_solving", 1.0);
        defaultRubric.put("critical_thinking", 1.0);
        defaultRubric.put("creativity", 1.0);
        defaultRubric.put("understanding_programming_logic", 1.0);
        return defaultRubric;
    }

    private String getCellString(Cell cell) {
        if (cell == null) return "";

        DataFormatter formatter = new DataFormatter();
        return formatter.formatCellValue(cell).trim();
    }
}