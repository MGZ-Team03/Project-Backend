package com.speaktracker.studentstatus;


import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.speaktracker.studentstatus.controller.StudentStatusController;
import com.speaktracker.studentstatus.repository.StudentStatusRepository;
import com.speaktracker.studentstatus.service.StudentStatusService;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;


/**
 * Handler for requests to Lambda function.
 */


public class StudentStatusHandler implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

    private final StudentStatusController controller;


    public StudentStatusHandler() {

        // DynamoDB 클라이언트 생성
        DynamoDbClient dynamoDbClient = DynamoDbClient.create();

        // 의존성 수동 주입
        StudentStatusRepository repository = new StudentStatusRepository(
                dynamoDbClient
        );
        StudentStatusService service = new StudentStatusService(repository);
        this.controller = new StudentStatusController(service);
    }

    @Override
    public APIGatewayProxyResponseEvent handleRequest(
            APIGatewayProxyRequestEvent event,
            Context context) {

        return controller.handleRequest(event);
    }
}


