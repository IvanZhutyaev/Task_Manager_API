package com.taskmanager.web.api.dto;

import com.taskmanager.domain.BoardColumn;

public record ColumnResponse(Long id, Long boardId, String name, int position) {

    public static ColumnResponse from(BoardColumn column) {
        return new ColumnResponse(column.getId(), column.getBoard().getId(), column.getName(), column.getPosition());
    }
}
