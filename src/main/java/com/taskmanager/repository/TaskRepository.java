package com.taskmanager.repository;

import com.taskmanager.domain.BoardColumn;
import com.taskmanager.domain.Task;
import com.taskmanager.domain.TaskPriority;
import com.taskmanager.domain.TaskStatus;
import com.taskmanager.domain.User;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TaskRepository extends JpaRepository<Task, Long> {

    @Query("""
            SELECT t FROM Task t
            WHERE t.column = :column
              AND t.deletedAt IS NULL
              AND (:assignee IS NULL OR t.assignee = :assignee)
              AND (:priority IS NULL OR t.priority = :priority)
              AND (:q IS NULL OR LOWER(t.title) LIKE LOWER(CONCAT('%', :q, '%')))
            ORDER BY t.createdAt DESC
            """)
    List<Task> findFiltered(
            @Param("column") BoardColumn column,
            @Param("assignee") User assignee,
            @Param("priority") TaskPriority priority,
            @Param("q") String q);

    @Query("""
            SELECT t FROM Task t
            WHERE t.column = :column
              AND t.deletedAt IS NULL
              AND (:assignee IS NULL OR t.assignee = :assignee)
              AND (:priority IS NULL OR t.priority = :priority)
              AND (:status IS NULL OR t.status = :status)
              AND (:q IS NULL OR LOWER(t.title) LIKE LOWER(CONCAT('%', :q, '%')))
            """)
    Page<Task> findFilteredPageable(
            @Param("column") BoardColumn column,
            @Param("assignee") User assignee,
            @Param("priority") TaskPriority priority,
            @Param("status") TaskStatus status,
            @Param("q") String q,
            Pageable pageable);
}
