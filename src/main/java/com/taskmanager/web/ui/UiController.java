package com.taskmanager.web.ui;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class UiController {

    @GetMapping("/")
    public String home() {
        return "redirect:/projects";
    }

    @GetMapping("/login")
    public String login() {
        return "login";
    }

    @GetMapping("/register")
    public String register() {
        return "register";
    }

    @GetMapping("/projects")
    public String projects() {
        return "projects";
    }

    @GetMapping("/projects/{projectId}")
    public String projectDetail() {
        return "project-detail";
    }

    @GetMapping("/boards/{boardId}")
    public String board() {
        return "board";
    }
}
