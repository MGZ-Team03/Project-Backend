package websocket.service;

import lombok.RequiredArgsConstructor;
import websocket.dto.TutorStudentDto;
import websocket.repository.SocketRepository;


@RequiredArgsConstructor
public class SocketService {
    private final SocketRepository socketRepository;

    public void save(TutorStudentDto tutorStudentDto) {
        socketRepository.saveTutorStudent(tutorStudentDto);
    }
}
