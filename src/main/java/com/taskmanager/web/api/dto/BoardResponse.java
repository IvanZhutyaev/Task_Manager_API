package com.taskmanager.web.api.dto;

import com.taskmanager.domain.Board;

public record BoardResponse(Long id, Long projectId, String name) {

    public static BoardResponse from(Board board) {
        return new BoardResponse(board.getId(), board.getProject().getId(), board.getName());
    }
}
