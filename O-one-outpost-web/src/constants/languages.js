/**
 * The judge languages supported by the sandbox. Keep this list in lockstep
 * with the backend `Language` registry and `HarnessLanguage` enum; the sandbox
 * rejects any id that is not in its closed set.
 *
 * `monaco` is the Monaco/TextMate language id used by the editor. `label` is
 * the human-facing name shown in selectors and badges.
 */
export const LANGUAGES = [
    { id: 'java', label: 'Java', monaco: 'java' },
    { id: 'python', label: 'Python', monaco: 'python' },
    { id: 'cpp', label: 'C++', monaco: 'cpp' },
    { id: 'c', label: 'C', monaco: 'c' },
    { id: 'javascript', label: 'JavaScript', monaco: 'javascript' },
    { id: 'typescript', label: 'TypeScript', monaco: 'typescript' },
    { id: 'go', label: 'Go', monaco: 'go' },
    { id: 'rust', label: 'Rust', monaco: 'rust' },
];

export const DEFAULT_LANGUAGE = 'java';

const LANGUAGE_BY_ID = Object.fromEntries(LANGUAGES.map((language) => [language.id, language]));

/** Returns the registry entry for an id, falling back to the default language. */
export function languageEntry(id) {
    return LANGUAGE_BY_ID[id] || LANGUAGE_BY_ID[DEFAULT_LANGUAGE];
}

export function languageLabel(id) {
    return languageEntry(id).label;
}

export function monacoLanguage(id) {
    return languageEntry(id).monaco;
}

/**
 * Picks the starter code for one language out of a problem payload.
 *
 * The backend resolves `startingCode` for every language on GET. The
 * `startingCodeJava` fallback keeps older cached payloads and legacy problems
 * usable; anything else gets a comment-only stub.
 */
export function startingCodeFor(problem, languageId) {
    if (!problem) return '';
    const explicit = problem.startingCode?.[languageId];
    if (typeof explicit === 'string' && explicit.trim() !== '') return explicit;
    if (languageId === DEFAULT_LANGUAGE && typeof problem.startingCodeJava === 'string'
        && problem.startingCodeJava.trim() !== '') {
        return problem.startingCodeJava;
    }
    return '// Write your solution here\n';
}

/** Builds the initial per-language code map for a problem. */
export function starterCodeMap(problem) {
    const map = {};
    LANGUAGES.forEach(({ id }) => {
        map[id] = startingCodeFor(problem, id);
    });
    return map;
}
