package com.taskmanager.repository;

import com.taskmanager.domain.BoardColumn;
import com.taskmanager.domain.Task;
import com.taskmanager.domain.TaskPriority;
import com.taskmanager.domain.User;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TaskRepository extends JpaRepository<Task, Long> {

    @Query("""
            SELECT t FROM Task t
            WHERE t.column = :column
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
}
