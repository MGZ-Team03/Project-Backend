package websocket.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class WebSocketRequest<T> {
    private String action;
    private T data;
}
