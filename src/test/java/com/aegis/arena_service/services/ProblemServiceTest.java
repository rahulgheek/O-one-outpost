package com.aegis.arena_service.services;

import com.aegis.arena_service.model.Problem;
import com.aegis.arena_service.repository.ProblemRepository;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProblemServiceTest {

    private final ProblemRepository repository = mock(ProblemRepository.class);
    private final ProblemService service = new ProblemService(repository);

    @Test
    void validProblemIsSavedWithGeneratedJavaStarter() {
        when(repository.save(any(Problem.class))).thenAnswer(invocation -> invocation.getArgument(0));
        Problem problem = problem();
        problem.setStartingCodeJava("  ");

        Problem saved = service.createProblem(problem);

        assertTrue(saved.getStartingCodeJava().contains("public int[] solve(int[] arg0, int arg1)"));
        assertEquals("EASY", saved.getDifficulty());
        verify(repository).save(problem);
    }

    @Test
    void explicitOverridesAreKeptButBlankEntriesAreDropped() {
        when(repository.save(any(Problem.class))).thenAnswer(invocation -> invocation.getArgument(0));
        Problem problem = problem();
        problem.setStartingCode(Map.of("python", "# custom", "go", "  "));

        Problem saved = service.createProblem(problem);

        assertEquals(Map.of("python", "# custom"), saved.getStartingCode());
    }

    @Test
    void unknownReturnTypeIsRejectedBeforeSaving() {
        Problem problem = problem();
        problem.setReturnType("BigDecimal");

        assertThrows(IllegalArgumentException.class, () -> service.createProblem(problem));
        verify(repository, never()).save(any());
    }

    @Test
    void nonIntArrayParametersAreRejectedBeforeSaving() {
        Problem problem = problem();
        problem.setParameterTypes(List.of("long[]", "int"));

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> service.createProblem(problem));

        assertTrue(error.getMessage().contains("Only int[] arrays"));
        verify(repository, never()).save(any());
    }

    @Test
    void malformedInputLiteralIsRejectedBeforeSaving() {
        Problem problem = problem();
        problem.getTestCases().get(0).setInputs(List.of("[2,7,11,15]", "not-a-number"));

        assertThrows(IllegalArgumentException.class, () -> service.createProblem(problem));
        verify(repository, never()).save(any());
    }

    @Test
    void missingTestCasesAreRejected() {
        Problem problem = problem();
        problem.setTestCases(List.of());

        assertThrows(IllegalArgumentException.class, () -> service.createProblem(problem));
        verify(repository, never()).save(any());
    }

    @Test
    void malformedMethodNameIsRejected() {
        Problem problem = problem();
        problem.setMethodName("2bad");

        assertThrows(IllegalArgumentException.class, () -> service.createProblem(problem));
        verify(repository, never()).save(any());
    }

    @Test
    void updatePreservesStoredOverridesInsteadOfPersistingTheResolvedView() {
        Problem existing = problem();
        existing.setStartingCode(Map.of("python", "# custom"));
        when(repository.findByTitle("Two Sum")).thenReturn(Optional.of(existing));

        Problem updated = problem();
        updated.setDescription("Updated description");
        // This is what the admin UI echoes back after GET resolves the view.
        updated.setStartingCode(Map.of("python", "# generated stub"));

        service.updateProblemByTitle("Two Sum", updated);

        assertEquals("# custom", existing.getStartingCode().get("python"));
        assertEquals("Updated description", existing.getDescription());
        verify(repository).save(existing);
    }

    @Test
    void updateIsValidatedBeforeTheStoredDocumentIsTouched() {
        when(repository.findByTitle("Two Sum")).thenReturn(Optional.of(problem()));
        Problem invalid = problem();
        invalid.setReturnType("Map");

        assertThrows(IllegalArgumentException.class,
                () -> service.updateProblemByTitle("Two Sum", invalid));

        verify(repository, never()).save(any());
    }

    @Test
    void missingProblemTitleIsRejected() {
        Problem problem = problem();
        problem.setTitle(null);

        assertThrows(IllegalArgumentException.class, () -> service.createProblem(problem));
    }

    @Test
    void zeroParameterMethodsAreAccepted() {
        when(repository.save(any(Problem.class))).thenAnswer(invocation -> invocation.getArgument(0));
        Problem problem = problem();
        problem.setMethodName("now");
        problem.setReturnType("long");
        problem.setParameterTypes(List.of());
        Problem.TestCase testCase = new Problem.TestCase();
        testCase.setInputs(List.of());
        testCase.setExpectedOutput("0");
        testCase.setHidden(false);
        problem.setTestCases(List.of(testCase));

        Problem saved = service.createProblem(problem);

        assertNotNull(saved);
        assertNull(saved.getStartingCode());
    }

    private static Problem problem() {
        Problem problem = new Problem();
        problem.setTitle("Two Sum");
        problem.setDifficulty("easy");
        problem.setDescription("Find two numbers.");
        problem.setMethodName("solve");
        problem.setReturnType("int[]");
        problem.setParameterTypes(List.of("int[]", "int"));
        problem.setTimeLimitMs(2000);
        problem.setMemoryLimitKb(262144);

        Problem.TestCase testCase = new Problem.TestCase();
        testCase.setInputs(List.of("[2,7,11,15]", "9"));
        testCase.setExpectedOutput("[0,1]");
        testCase.setHidden(false);
        problem.setTestCases(List.of(testCase));
        return problem;
    }
}
