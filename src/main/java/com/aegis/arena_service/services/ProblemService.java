package com.aegis.arena_service.services;

import com.aegis.arena_service.model.Problem;
import com.aegis.arena_service.repository.ProblemRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ProblemService {
    private final ProblemRepository problemRepository;

    public Problem createProblem(Problem problem){
        return problemRepository.save(problem);
    }

    public List<Problem> getAllProblems(){
        return problemRepository.findAll();
    }

    public void updateProblemByTitle(String title, Problem updatedProblem) {
        // Find the existing problem by its exact title
        Problem existingProblem = problemRepository.findByTitle(title)
                .orElseThrow(() -> new RuntimeException("Problem with title '" + title + "' not found!"));

        // Overwrite the fields
        existingProblem.setDifficulty(updatedProblem.getDifficulty());
        existingProblem.setDescription(updatedProblem.getDescription());
        existingProblem.setMethodName(updatedProblem.getMethodName());
        existingProblem.setReturnType(updatedProblem.getReturnType());
        existingProblem.setParameterTypes(updatedProblem.getParameterTypes());
        existingProblem.setStartingCodeJava(updatedProblem.getStartingCodeJava());
        existingProblem.setTestCases(updatedProblem.getTestCases());

        // Save it back to MongoDB
        problemRepository.save(existingProblem);
    }
}
