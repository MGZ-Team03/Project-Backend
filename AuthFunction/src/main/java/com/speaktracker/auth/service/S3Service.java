package com.speaktracker.auth.service;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

/**
 * S3 프로필 이미지 업로드 서비스
 */
public class S3Service {
    
    private final S3Presigner s3Presigner;
    private final String bucketName;
    
    public S3Service(String bucketName) {
        this.bucketName = bucketName;
        this.s3Presigner = S3Presigner.create();
    }
    
    /**
     * 프로필 이미지 업로드용 Presigned URL 생성
     * @param email 사용자 이메일
     * @return uploadUrl과 imageUrl을 포함한 Map
     */
    public Map<String, String> generateUploadUrl(String email) {
        // 파일 키 생성 (이메일 해시 + 타임스탬프로 유니크하게)
        String fileKey = "profiles/" + Math.abs(email.hashCode()) + "_" + System.currentTimeMillis() + ".jpg";
        
        // S3 PutObject 요청 생성
        PutObjectRequest putObjectRequest = PutObjectRequest.builder()
            .bucket(bucketName)
            .key(fileKey)
            .contentType("image/jpeg")
            .build();
        
        // Presigned URL 요청 생성 (5분 유효)
        PutObjectPresignRequest presignRequest = PutObjectPresignRequest.builder()
            .signatureDuration(Duration.ofMinutes(5))
            .putObjectRequest(putObjectRequest)
            .build();
        
        // Presigned URL 생성
        PresignedPutObjectRequest presignedRequest = s3Presigner.presignPutObject(presignRequest);
        String uploadUrl = presignedRequest.url().toString();
        
        // 이미지 접근 URL (Public Read 설정됨)
        String imageUrl = "https://" + bucketName + ".s3.amazonaws.com/" + fileKey;
        
        Map<String, String> result = new HashMap<>();
        result.put("uploadUrl", uploadUrl);
        result.put("imageUrl", imageUrl);
        
        return result;
    }
}
