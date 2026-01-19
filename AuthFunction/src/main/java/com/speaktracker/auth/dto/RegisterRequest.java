package com.speaktracker.auth.dto;

/**
 * 회원가입 요청 DTO
 */
public class RegisterRequest {
    
    private String email;
    private String password;
    private String name;
    private String role;
    
    public RegisterRequest() {
    }
    
    public RegisterRequest(String email, String password, String name, String role) {
        this.email = email;
        this.password = password;
        this.name = name;
        this.role = role != null ? role : "student";
    }
    
    // Getters and Setters
    public String getEmail() {
        return email;
    }
    
    public void setEmail(String email) {
        this.email = email;
    }
    
    public String getPassword() {
        return password;
    }
    
    public void setPassword(String password) {
        this.password = password;
    }
    
    public String getName() {
        return name;
    }
    
    public void setName(String name) {
        this.name = name;
    }
    
    public String getRole() {
        return role != null ? role : "student";
    }
    
    public void setRole(String role) {
        this.role = role != null ? role : "student";
    }
}
