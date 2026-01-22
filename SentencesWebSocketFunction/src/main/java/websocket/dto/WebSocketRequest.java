package websocket.dto;

import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Data
@Getter
@NoArgsConstructor
public class WebSocketRequest<T> {
    private String action;
    private T data;
}
