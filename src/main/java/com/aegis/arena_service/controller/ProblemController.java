package com.aegis.arena_service.controller;

import com.aegis.arena_service.model.Problem;
import com.aegis.arena_service.repository.ProblemRepository;
import com.aegis.arena_service.services.ProblemService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/problems")
@RequiredArgsConstructor
public class ProblemController {

    private final ProblemRepository problemRepository;
    private final ProblemService problemService;

    @GetMapping("/{problemId}")
    public ResponseEntity<Problem> getProblem(@PathVariable String problemId) {
        return problemRepository.findById(problemId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/all") // or whatever your fetch route is
    public ResponseEntity<List<Problem>> getAllProblems() {
        return ResponseEntity.ok(problemRepository.findAll());
    }

    @PostMapping("/admin/create")
    public ResponseEntity<Problem> createProblem(@RequestBody Problem problem) {
        Problem savedProblem = problemService.createProblem(problem);
        System.out.println("New problem created: " + savedProblem.getTitle());
        return ResponseEntity.ok(savedProblem);
    }

    @PutMapping("/admin/update/{title}")
    public ResponseEntity<String> updateProblemByTitle(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @PathVariable String title,
            @RequestBody Problem updatedProblem) {

        // (Assuming you have a quick token check here like we did for create-admin)

        problemService.updateProblemByTitle(title, updatedProblem);
        return ResponseEntity.ok("Problem '" + title + "' successfully updated!");
    }

    @GetMapping("/admin/search")
    public ResponseEntity<List<Problem>> searchProblems(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestParam String query) {

        List<Problem> results = problemRepository.findTop10ByTitleContainingIgnoreCase(query);
        return ResponseEntity.ok(results);
    }

    @DeleteMapping("/admin/delete/{title}")
    public ResponseEntity<String> deleteProblemByTitle(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @PathVariable String title) {
        return problemRepository.findByTitle(title)
                .map(problem -> {
                    problemRepository.delete(problem);
                    System.out.println("Problem deleted by admin: " + title);
                    return ResponseEntity.ok("Problem '" + title + "' deleted successfully.");
                })
                .orElse(ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body("Problem '" + title + "' not found."));
    }
}