package com.taskmanager.repository;

import com.taskmanager.domain.BoardColumn;
import com.taskmanager.domain.Label;
import com.taskmanager.domain.Project;
import com.taskmanager.domain.Sprint;
import com.taskmanager.domain.Task;
import com.taskmanager.domain.TaskPriority;
import com.taskmanager.domain.TaskStatus;
import com.taskmanager.domain.TaskType;
import com.taskmanager.domain.User;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TaskRepository extends JpaRepository<Task, Long> {

    long countByColumnAndDeletedAtIsNull(BoardColumn column);

    List<Task> findBySprintAndDeletedAtIsNull(Sprint sprint);

    @Query("""
            SELECT t FROM Task t
            WHERE t.column = :column
              AND t.deletedAt IS NULL
              AND (:assignee IS NULL OR t.assignee = :assignee)
              AND (:priority IS NULL OR t.priority = :priority)
              AND (:status IS NULL OR t.status = :status)
              AND (:taskType IS NULL OR t.taskType = :taskType)
              AND (:label IS NULL OR :label MEMBER OF t.labels)
              AND (:q IS NULL OR LOWER(t.title) LIKE LOWER(CONCAT('%', :q, '%')))
            """)
    Page<Task> findFilteredPageable(
            @Param("column") BoardColumn column,
            @Param("assignee") User assignee,
            @Param("priority") TaskPriority priority,
            @Param("status") TaskStatus status,
            @Param("taskType") TaskType taskType,
            @Param("label") Label label,
            @Param("q") String q,
            Pageable pageable);

    @Query("""
            SELECT t FROM Task t
            WHERE t.column.board.project = :project
              AND t.deletedAt IS NULL
              AND (:assignee IS NULL OR t.assignee = :assignee)
              AND (:priority IS NULL OR t.priority = :priority)
              AND (:status IS NULL OR t.status = :status)
              AND (:taskType IS NULL OR t.taskType = :taskType)
              AND (:label IS NULL OR :label MEMBER OF t.labels)
              AND (:q IS NULL OR LOWER(t.title) LIKE LOWER(CONCAT('%', :q, '%'))
                   OR LOWER(COALESCE(t.description, '')) LIKE LOWER(CONCAT('%', :q, '%')))
            """)
    Page<Task> findInProject(
            @Param("project") Project project,
            @Param("assignee") User assignee,
            @Param("priority") TaskPriority priority,
            @Param("status") TaskStatus status,
            @Param("taskType") TaskType taskType,
            @Param("label") Label label,
            @Param("q") String q,
            Pageable pageable);
}
