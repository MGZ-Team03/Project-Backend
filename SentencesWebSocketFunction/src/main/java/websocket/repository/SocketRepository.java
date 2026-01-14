package websocket.repository;

import lombok.RequiredArgsConstructor;
import org.joda.time.DateTime;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.*;
import com.amazonaws.services.lambda.runtime.Context;
import websocket.dto.StatusRequest;
import websocket.dto.TutorStudentDto;


import java.util.HashMap;
import java.util.Map;

import static com.amazonaws.services.lambda.runtime.LambdaRuntime.getLogger;

@RequiredArgsConstructor
public class SocketRepository {
    private final DynamoDbClient dynamoDbClient;

    private final String tutorStudentsTableName;

    public void saveTutorStudent(TutorStudentDto tutorStudentDto) {
        getLogger().log("=== Repository 실행 ===");
        getLogger().log("Table: " + tutorStudentsTableName);
        getLogger().log("Tutor: " + tutorStudentDto.getTutorEmail());
        getLogger().log("Student: " + tutorStudentDto.getStudentEmail());


        HashMap<String, AttributeValue> item = new HashMap<>();
        item.put("tutor_email", AttributeValue.fromS(tutorStudentDto.getTutorEmail()));
        item.put("student_email", AttributeValue.fromS(tutorStudentDto.getStudentEmail()));
        item.put("assigned_at",AttributeValue.fromS(DateTime.now().toString()));
        item.put("status", AttributeValue.fromS(tutorStudentDto.getStatus()));

        PutItemRequest build = PutItemRequest.builder()
                .tableName(tutorStudentsTableName)
                .item(item)
                .build();

        dynamoDbClient.putItem(build);
    }

    public boolean existsTutorStudent(String tutorEmail, String studentEmail) {
        try{
            getLogger().log("=== Repository: Check Exists ===");
            getLogger().log("Tutor: " + tutorEmail);
            getLogger().log("Student: " + studentEmail);

            Map<String, AttributeValue> key = new HashMap<>();
            key.put("tutor_email", AttributeValue.fromS(tutorEmail));
            key.put("student_email", AttributeValue.fromS(studentEmail));

            GetItemRequest request = GetItemRequest.builder()
                    .tableName(tutorStudentsTableName)
                    .key(key)
                    .build();

            GetItemResponse response = dynamoDbClient.getItem(request);

            boolean exists = response.hasItem();
            getLogger().log("Exists: " + exists);
            return exists;
        }  catch (DynamoDbException e) {
            getLogger().log("❌ DynamoDB Error: " + e.getMessage());
            // 에러 발생 시 false 반환 (존재하지 않는 것으로 처리)
            return false;
        }
    }

}
