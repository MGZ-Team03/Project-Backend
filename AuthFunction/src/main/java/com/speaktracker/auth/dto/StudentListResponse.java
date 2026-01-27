package com.speaktracker.auth.dto;

import java.util.List;

/**
 * 학생 목록 조회 응답 DTO
 */
public class StudentListResponse {

    private boolean success;
    private List<StudentInfo> students;
    private PaginationInfo pagination;

    public StudentListResponse() {
    }

    public StudentListResponse(boolean success, List<StudentInfo> students, PaginationInfo pagination) {
        this.success = success;
        this.students = students;
        this.pagination = pagination;
    }

    // Getters and Setters
    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public List<StudentInfo> getStudents() {
        return students;
    }

    public void setStudents(List<StudentInfo> students) {
        this.students = students;
    }

    public PaginationInfo getPagination() {
        return pagination;
    }

    public void setPagination(PaginationInfo pagination) {
        this.pagination = pagination;
    }

    /**
     * 학생 정보
     */
    public static class StudentInfo {
        private String email;
        private String learningLevel;

        public StudentInfo() {
        }

        public StudentInfo(String email, String learningLevel) {
            this.email = email;
            this.learningLevel = learningLevel;
        }

        // Getters and Setters
        public String getEmail() {
            return email;
        }

        public void setEmail(String email) {
            this.email = email;
        }

        public String getLearningLevel() {
            return learningLevel;
        }

        public void setLearningLevel(String learningLevel) {
            this.learningLevel = learningLevel;
        }
    }

    /**
     * 페이지네이션 정보
     */
    public static class PaginationInfo {
        private String nextToken;
        private boolean hasMore;
        private int count;

        public PaginationInfo() {
        }

        public PaginationInfo(String nextToken, boolean hasMore, int count) {
            this.nextToken = nextToken;
            this.hasMore = hasMore;
            this.count = count;
        }

        // Getters and Setters
        public String getNextToken() {
            return nextToken;
        }

        public void setNextToken(String nextToken) {
            this.nextToken = nextToken;
        }

        public boolean isHasMore() {
            return hasMore;
        }

        public void setHasMore(boolean hasMore) {
            this.hasMore = hasMore;
        }

        public int getCount() {
            return count;
        }

        public void setCount(int count) {
            this.count = count;
        }
    }
}
