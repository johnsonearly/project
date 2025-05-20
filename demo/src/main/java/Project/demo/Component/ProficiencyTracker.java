package Project.demo.Component;

import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class ProficiencyTracker {
    private final Map<String, Queue<Double>> userScores = new ConcurrentHashMap<>();
    private final Map<String, Integer> questionCounts = new ConcurrentHashMap<>();
    private final int WINDOW_SIZE = 5;

    public void recordScore(String userId, double score) {
        userScores.computeIfAbsent(userId, k -> new LinkedList<>());
        questionCounts.merge(userId, 1, Integer::sum);

        Queue<Double> scores = userScores.get(userId);
        if (scores.size() >= WINDOW_SIZE) {
            scores.poll();
        }
        scores.offer(score);
    }
    public String determineUserLevel(String userId) {
        List<Double> scores = (List<Double>) userScores.get(userId);
        if (scores == null || scores.size() < WINDOW_SIZE) {
            return "Undetermined";
        }

        double average = scores.stream().mapToDouble(Double::doubleValue).average().orElse(0);
        double lastTwoAvg = scores.subList(scores.size() - 2, scores.size()).stream()
                .mapToDouble(Double::doubleValue).average().orElse(0);

        if (average >= 85 || (average >= 75 && lastTwoAvg >= 85)) {
            return "Advanced";
        } else if (average >= 65 || (average >= 55 && lastTwoAvg >= 70)) {
            return "Intermediate";
        } else {
            return "Beginner";
        }
    }

    public int getQuestionCount(String userId) {
        return questionCounts.getOrDefault(userId, 0);
    }

    public double getAverageScore(String userId) {
        if (!userScores.containsKey(userId) || userScores.get(userId).isEmpty()) {
            return 0.0;
        }
        return userScores.get(userId).stream()
                .mapToDouble(Double::doubleValue)
                .average()
                .orElse(0.0);
    }

    public void resetUser(String userId) {
        userScores.remove(userId);
        questionCounts.remove(userId);
    }

    public int getWindowSize() {
        return WINDOW_SIZE;
    }
}