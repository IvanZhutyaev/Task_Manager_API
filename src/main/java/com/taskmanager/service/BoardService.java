package com.taskmanager.service;

import com.taskmanager.domain.Board;
import com.taskmanager.domain.Project;
import com.taskmanager.domain.User;
import com.taskmanager.repository.BoardRepository;
import com.taskmanager.security.CurrentUserService;
import com.taskmanager.web.api.dto.BoardRequest;
import com.taskmanager.web.api.dto.BoardResponse;
import com.taskmanager.web.exception.ApiException;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BoardService {

    private static final Logger log = LoggerFactory.getLogger(BoardService.class);

    private final BoardRepository boardRepository;
    private final ProjectService projectService;
    private final ProjectAccessService projectAccessService;
    private final CurrentUserService currentUserService;

    public BoardService(
            BoardRepository boardRepository,
            ProjectService projectService,
            ProjectAccessService projectAccessService,
            CurrentUserService currentUserService) {
        this.boardRepository = boardRepository;
        this.projectService = projectService;
        this.projectAccessService = projectAccessService;
        this.currentUserService = currentUserService;
    }

    @Transactional(readOnly = true)
    public List<BoardResponse> listBoards(Long projectId) {
        Project project = projectService.getProjectOrThrow(projectId);
        projectAccessService.requireCanRead(project, currentUserService.getCurrentUser());
        return boardRepository.findByProjectOrderByCreatedAtAsc(project).stream()
                .map(BoardResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public BoardResponse getBoard(Long boardId) {
        Board board = getBoardOrThrow(boardId);
        projectAccessService.requireCanRead(board.getProject(), currentUserService.getCurrentUser());
        return BoardResponse.from(board);
    }

    @Transactional
    public BoardResponse createBoard(Long projectId, BoardRequest request) {
        Project project = projectService.getProjectOrThrow(projectId);
        projectAccessService.requireCanWriteContent(project, currentUserService.getCurrentUser());

        Board board = new Board();
        board.setProject(project);
        board.setName(request.name());
        board = boardRepository.save(board);

        log.info("Board created: id={}, projectId={}", board.getId(), projectId);
        return BoardResponse.from(board);
    }

    @Transactional
    public BoardResponse updateBoard(Long boardId, BoardRequest request) {
        Board board = getBoardOrThrow(boardId);
        projectAccessService.requireCanWriteContent(board.getProject(), currentUserService.getCurrentUser());

        board.setName(request.name());
        board = boardRepository.save(board);
        return BoardResponse.from(board);
    }

    @Transactional
    public void deleteBoard(Long boardId) {
        Board board = getBoardOrThrow(boardId);
        projectAccessService.requireCanWriteContent(board.getProject(), currentUserService.getCurrentUser());
        boardRepository.delete(board);
        log.info("Board deleted: id={}", boardId);
    }

    public Board getBoardOrThrow(Long boardId) {
        return boardRepository.findById(boardId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND.value(), "Board not found"));
    }
}
