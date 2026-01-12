package helloworld;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.google.gson.Gson;

/**
 * Handler for requests to Lambda function.
 */
public class App implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {
    private final Gson gson = new Gson();

    public APIGatewayProxyResponseEvent handleRequest(final APIGatewayProxyRequestEvent input, final Context context) {
        Map<String, String> headers = new HashMap<>();
        Map<String, String> responseBody = new HashMap<>();
        headers.put("Content-Type", "application/json");

        responseBody.put("users", System.getenv("USERS_TABLE"));
        responseBody.put("sessions", System.getenv("SESSIONS_TABLE"));
        responseBody.put("sentences", System.getenv("SENTENCES_TABLE"));

        APIGatewayProxyResponseEvent response = new APIGatewayProxyResponseEvent()
                .withHeaders(headers);
        try {

            responseBody.put("message", "Hello World cd 테스트###!@#$???");
            responseBody.put("cd!!", "hellow world");

            return response
                    .withStatusCode(200)
                    .withBody(gson.toJson(responseBody));
        } catch (Exception e) {
           responseBody.put("message", e.getMessage());
            return response
                    .withBody(gson.toJson(responseBody))
                    .withStatusCode(400);
        }
    }

    private String getPageContents(String address) throws IOException{
        URL url = new URL(address);
        try(BufferedReader br = new BufferedReader(new InputStreamReader(url.openStream()))) {
            return br.lines().collect(Collectors.joining(System.lineSeparator()));
        }
    }
}
