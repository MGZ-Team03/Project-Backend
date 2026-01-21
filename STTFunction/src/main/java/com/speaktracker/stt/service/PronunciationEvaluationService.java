package com.speaktracker.stt.service;

import com.speaktracker.stt.model.PronunciationResult;
import com.speaktracker.stt.util.TextSimilarityUtil;

import java.util.*;
import java.util.stream.Collectors;

public class PronunciationEvaluationService {

    // 가중치
    private static final double WORD_ACCURACY_WEIGHT = 0.50;
    private static final double SEQUENCE_MATCH_WEIGHT = 0.30;
    private static final double COMPLETENESS_WEIGHT = 0.20;

    public PronunciationResult evaluate(String original, String transcribed) {
        // 정규화
        String normOriginal = normalize(original);
        String normTranscribed = normalize(transcribed);

        String[] originalWords = normOriginal.split("\\s+");
        String[] transcribedWords = normTranscribed.split("\\s+");

        // 1. Word Accuracy (단어 일치율)
        double wordAccuracy = calculateWordAccuracy(originalWords, transcribedWords);

        // 2. Sequence Match (순서 유사도 - LCS 기반)
        double sequenceMatch = calculateSequenceMatch(originalWords, transcribedWords);

        // 3. Completeness (완전성 - 누락/추가 단어 비율)
        double completeness = calculateCompleteness(originalWords, transcribedWords);

        // 최종 점수
        double finalScore =
            (wordAccuracy * WORD_ACCURACY_WEIGHT) +
            (sequenceMatch * SEQUENCE_MATCH_WEIGHT) +
            (completeness * COMPLETENESS_WEIGHT);

        // 문제 단어 식별
        List<String> missedWords = findMissedWords(originalWords, transcribedWords);
        List<String> extraWords = findExtraWords(originalWords, transcribedWords);

        // 피드백 생성
        String feedback = generateFeedback(finalScore, missedWords, extraWords);
        String grade = determineGrade(finalScore);

        return PronunciationResult.builder()
                .overallScore((int) Math.round(finalScore * 100))
                .wordAccuracy((int) Math.round(wordAccuracy * 100))
                .sequenceScore((int) Math.round(sequenceMatch * 100))
                .completenessScore((int) Math.round(completeness * 100))
                .missedWords(missedWords)
                .extraWords(extraWords)
                .feedback(feedback)
                .grade(grade)
                .build();
    }

    private String normalize(String text) {
        return text.toLowerCase()
                .replaceAll("[^a-z0-9\\s]", "")  // 구두점 제거
                .replaceAll("\\s+", " ")         // 공백 정규화
                .trim();
    }

    private double calculateWordAccuracy(String[] originalWords, String[] transcribedWords) {
        if (originalWords.length == 0) {
            return 1.0;
        }

        Set<String> originalSet = new HashSet<>(Arrays.asList(originalWords));
        Set<String> transcribedSet = new HashSet<>(Arrays.asList(transcribedWords));

        long matchedWords = originalSet.stream()
                .filter(transcribedSet::contains)
                .count();

        return (double) matchedWords / originalSet.size();
    }

    private double calculateSequenceMatch(String[] originalWords, String[] transcribedWords) {
        if (originalWords.length == 0) {
            return 1.0;
        }

        int lcsLength = TextSimilarityUtil.longestCommonSubsequence(originalWords, transcribedWords);
        return (double) lcsLength / originalWords.length;
    }

    private double calculateCompleteness(String[] originalWords, String[] transcribedWords) {
        int missed = findMissedWords(originalWords, transcribedWords).size();
        int extra = findExtraWords(originalWords, transcribedWords).size();
        int total = originalWords.length;

        if (total == 0) {
            return 1.0;
        }

        // 누락과 추가 단어가 적을수록 완전성 높음
        double penalty = (missed + extra) / (double) total;
        return Math.max(0.0, 1.0 - penalty);
    }

    private List<String> findMissedWords(String[] originalWords, String[] transcribedWords) {
        Set<String> originalSet = new HashSet<>(Arrays.asList(originalWords));
        Set<String> transcribedSet = new HashSet<>(Arrays.asList(transcribedWords));

        return originalSet.stream()
                .filter(word -> !transcribedSet.contains(word))
                .collect(Collectors.toList());
    }

    private List<String> findExtraWords(String[] originalWords, String[] transcribedWords) {
        Set<String> originalSet = new HashSet<>(Arrays.asList(originalWords));
        Set<String> transcribedSet = new HashSet<>(Arrays.asList(transcribedWords));

        return transcribedSet.stream()
                .filter(word -> !originalSet.contains(word))
                .collect(Collectors.toList());
    }

    private String generateFeedback(double score, List<String> missed, List<String> extra) {
        if (score >= 0.95) {
            return "Excellent! Your pronunciation is nearly perfect.";
        } else if (score >= 0.85) {
            if (!missed.isEmpty()) {
                return "Great job! Minor improvements needed with: " + String.join(", ", missed);
            }
            return "Great job! Very good pronunciation.";
        } else if (score >= 0.70) {
            if (!missed.isEmpty()) {
                return "Good effort! Focus on these words: " + String.join(", ", missed);
            }
            return "Good effort! Keep practicing.";
        } else {
            if (!missed.isEmpty()) {
                return "Keep practicing! Pay attention to: " + String.join(", ", missed);
            }
            return "Keep practicing! You'll improve with more practice.";
        }
    }

    private String determineGrade(double score) {
        if (score >= 0.95) return "A+";
        if (score >= 0.90) return "A";
        if (score >= 0.85) return "B+";
        if (score >= 0.80) return "B";
        if (score >= 0.70) return "C";
        if (score >= 0.60) return "D";
        return "F";
    }
}
