import React, { useState, useEffect, useRef, useCallback } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import Editor from '@monaco-editor/react';
import { candidateApi } from '../services/candidateApi';
import { getApiErrorMessage } from '../utils/errors';
import useTournamentTimer from '../hooks/useTournamentTimer';

// ─── CONSTANTS ────────────────────────────────────────────────
const BLUR_GRACE_SECONDS = 5;   // Seconds before blur triggers termination
const TAB_GRACE_SECONDS = 3;    // Slightly shorter for tab-switch (more deliberate)

const DEFAULT_CODE = '// Write your solution here\nclass Solution {\n\n}';

export default function TournamentWorkspace() {
    const { assessmentId } = useParams();
    const navigate = useNavigate();

    const [assessmentData, setAssessmentData] = useState(null);
    const [problems, setProblems] = useState([]);
    const [activeIdx, setActiveIdx] = useState(0);
    const [isLoading, setIsLoading] = useState(true);
    const [loadError, setLoadError] = useState('');
    const [codeStore, setCodeStore] = useState({});
    const [consoleOutput, setConsoleOutput] = useState([]);
    const [isSubmitting, setIsSubmitting] = useState(false);
    const [isFullscreenInit, setIsFullscreenInit] = useState(false);

    // ── Anti-Cheat State ──────────────────────────────────────
    const [isTerminated, setIsTerminated] = useState(false);
    const [terminationReason, setTerminationReason] = useState('');
    const [warning, setWarning] = useState(null); // { reason, countdown }
    const warningIntervalRef = useRef(null);
    const isTerminatedRef = useRef(false);
    const expiryHandledRef = useRef(false);

    const deadline = assessmentData?.deadline || assessmentData?.endTime;
    const { timeLeft, isExpired } = useTournamentTimer(deadline);

    // ─── Lock the session when the personal deadline expires ──
    useEffect(() => {
        if (!isExpired || isTerminatedRef.current || expiryHandledRef.current) return;
        expiryHandledRef.current = true;
        candidateApi.finishAssessment(assessmentId, 'COMPLETED').catch(() => {});
    }, [isExpired, assessmentId]);

    // ==========================================
    // ANTI-CHEAT ENGINE (with grace period)
    // ==========================================

    const terminateSession = useCallback((reason) => {
        if (isTerminatedRef.current) return;
        isTerminatedRef.current = true;
        clearInterval(warningIntervalRef.current);
        setWarning(null);
        setIsTerminated(true);
        setTerminationReason(reason);
        candidateApi.finishAssessment(assessmentId, 'TERMINATED').catch(() => {});
    }, [assessmentId]);

    const startWarning = useCallback((reason, graceSecs) => {
        if (isTerminatedRef.current || isExpired) return;
        if (warningIntervalRef.current) return;

        setWarning({ reason, countdown: graceSecs });

        let remaining = graceSecs;
        warningIntervalRef.current = setInterval(() => {
            remaining -= 1;
            if (remaining <= 0) {
                clearInterval(warningIntervalRef.current);
                warningIntervalRef.current = null;
                terminateSession(reason);
            } else {
                setWarning((previous) => (previous ? { ...previous, countdown: remaining } : null));
            }
        }, 1000);
    }, [isExpired, terminateSession]);

    const dismissWarning = useCallback(() => {
        if (warningIntervalRef.current) {
            clearInterval(warningIntervalRef.current);
            warningIntervalRef.current = null;
        }
        setWarning(null);
    }, []);

    useEffect(() => {
        // 1. Tab switch / minimize (3-sec grace)
        const handleVisibilityChange = () => {
            if (document.hidden) {
                startWarning(
                    'You switched tabs or minimized the window. This is a proctored session.',
                    TAB_GRACE_SECONDS,
                );
            } else {
                dismissWarning();
            }
        };

        // 2. Window blur (5-sec grace)
        const handleBlur = () => startWarning('You clicked outside the secure workspace.', BLUR_GRACE_SECONDS);
        const handleFocus = () => dismissWarning();

        // 3. Block Copy / Paste / Cut globally (including Monaco)
        const preventCopyPaste = (event) => {
            event.preventDefault();
        };

        // 4. Block clipboard and DevTools shortcuts
        const preventShortcuts = (event) => {
            if ((event.ctrlKey || event.metaKey) && ['c', 'v', 'x', 's'].includes(event.key.toLowerCase())) {
                event.preventDefault();
            }
            if (event.key === 'F12'
                || (event.ctrlKey && event.shiftKey && ['i', 'j', 'c'].includes(event.key.toLowerCase()))) {
                event.preventDefault();
            }
        };

        // 5. Block right-click
        const preventRightClick = (event) => {
            event.preventDefault();
        };

        // 6. Warn before accidental refresh / close
        const handleBeforeUnload = (event) => {
            event.preventDefault();
            event.returnValue = 'You are in an active tournament. Leaving will lock your session forever.';
        };

        // 7. Fullscreen enforcement
        const handleFullscreenChange = () => {
            if (!document.fullscreenElement && isTerminatedRef.current === false) {
                startWarning('You exited full-screen mode. This is a proctored session.', 5);
            }
        };

        window.addEventListener('visibilitychange', handleVisibilityChange);
        window.addEventListener('blur', handleBlur);
        window.addEventListener('focus', handleFocus);
        window.addEventListener('copy', preventCopyPaste, true);
        window.addEventListener('paste', preventCopyPaste, true);
        window.addEventListener('cut', preventCopyPaste, true);
        window.addEventListener('keydown', preventShortcuts, true);
        window.addEventListener('contextmenu', preventRightClick);
        window.addEventListener('beforeunload', handleBeforeUnload);
        document.addEventListener('fullscreenchange', handleFullscreenChange);

        return () => {
            window.removeEventListener('visibilitychange', handleVisibilityChange);
            window.removeEventListener('blur', handleBlur);
            window.removeEventListener('focus', handleFocus);
            window.removeEventListener('copy', preventCopyPaste, true);
            window.removeEventListener('paste', preventCopyPaste, true);
            window.removeEventListener('cut', preventCopyPaste, true);
            window.removeEventListener('keydown', preventShortcuts, true);
            window.removeEventListener('contextmenu', preventRightClick);
            window.removeEventListener('beforeunload', handleBeforeUnload);
            document.removeEventListener('fullscreenchange', handleFullscreenChange);
            clearInterval(warningIntervalRef.current);
        };
    }, [startWarning, dismissWarning]);

    // ==========================================
    // WORKSPACE INITIALIZATION
    // ==========================================
    useEffect(() => {
        let active = true;

        const fetchWorkspace = async () => {
            try {
                // Start (or resume) the candidate's personal clock first so the
                // details response carries the correct deadline.
                await candidateApi.startWorkspace(assessmentId);
                const details = await candidateApi.getAssessmentDetails(assessmentId);
                const loadedProblems = await candidateApi.getAssessmentProblems(assessmentId);

                if (!active) return;

                setAssessmentData(details);
                setProblems(Array.isArray(loadedProblems) ? loadedProblems : []);

                const initialCode = {};
                (loadedProblems || []).forEach((problem) => {
                    initialCode[problem.id] = problem.startingCodeJava || DEFAULT_CODE;
                });
                setCodeStore(initialCode);
                setIsLoading(false);
            } catch (error) {
                if (!active) return;
                setLoadError(
                    getApiErrorMessage(error, 'Unable to load the secure workspace.'),
                );
                setIsLoading(false);
            }
        };

        fetchWorkspace();
        return () => {
            active = false;
        };
    }, [assessmentId]);

    const activeProblem = problems[activeIdx] || null;

    const handleEditorChange = (value) => {
        if (!activeProblem) return;
        setCodeStore((previous) => ({ ...previous, [activeProblem.id]: value }));
    };

    const addLog = (sender, message, color) => {
        const timestamp = new Date().toLocaleTimeString('en-US', { hour12: false });
        setConsoleOutput((previous) => [...previous, { ts: timestamp, sender, message, color }]);
    };

    const handleSubmit = async () => {
        if (!activeProblem || isSubmitting || isExpired || isTerminatedRef.current) return;
        setIsSubmitting(true);
        addLog('SYSTEM', 'Dispatching code to secure sandbox...', '#eab308');

        try {
            const result = await candidateApi.submitCode(
                assessmentId,
                activeProblem.id,
                codeStore[activeProblem.id] || '',
            );

            if (result.executionStatus === 'ALL_PASSED') {
                addLog('SANDBOX', 'ALL TESTS PASSED', '#00ffb4');
                addLog('SCORING', `Points awarded: +${result.pointsAwarded} pts`, '#00ffb4');
            } else {
                addLog(
                    'SANDBOX',
                    `${result.executionStatus} (${result.testCasesPassed}/${result.totalTestCases} passed)`,
                    '#ef4444',
                );
                if (result.pointsAwarded > 0) {
                    addLog('SCORING', `Partial credit: +${result.pointsAwarded} pts`, '#eab308');
                }
            }
        } catch (error) {
            addLog(
                'ERROR',
                `Execution failed: ${getApiErrorMessage(error, 'Sandbox execution failed.')}`,
                '#ef4444',
            );
        } finally {
            setIsSubmitting(false);
        }
    };

    const handleFinish = () => {
        if (!window.confirm('End the assessment now? You cannot rejoin this session.')) return;
        candidateApi
            .finishAssessment(assessmentId, 'COMPLETED')
            .then(() => navigate(`/workspace/${assessmentId}/leaderboard`))
            .catch((error) => addLog(
                'ERROR',
                getApiErrorMessage(error, 'Could not close the assessment.'),
                '#ef4444',
            ));
    };

    const leaveWorkspace = () => navigate('/candidate/dashboard');

    // ==========================================
    // TERMINATION SCREEN
    // ==========================================
    if (isTerminated) {
        return (
            <div style={{
                display: 'flex', flexDirection: 'column', height: '100vh',
                background: '#0a0205', alignItems: 'center', justifyContent: 'center',
                textAlign: 'center', padding: '32px', fontFamily: '"Share Tech Mono", monospace',
                border: '1px solid #7f1d1d',
            }}>
                <div style={{
                    width: '80px', height: '80px', borderRadius: '50%',
                    background: 'rgba(220,38,38,0.15)', border: '2px solid #dc2626',
                    display: 'flex', alignItems: 'center', justifyContent: 'center',
                    marginBottom: '32px',
                    boxShadow: '0 0 40px rgba(220,38,38,0.4), inset 0 0 20px rgba(220,38,38,0.1)',
                    animation: 'pulse 2s ease-in-out infinite',
                }}>
                    <i className="bi bi-shield-x" style={{ color: '#ef4444', fontSize: '2.2rem' }} aria-hidden="true" />
                </div>

                <div style={{ fontSize: '0.65rem', letterSpacing: '6px', color: '#7f1d1d', marginBottom: '16px' }}>
                    AEGIS PROCTORING SYSTEM
                </div>

                <h1 style={{ fontSize: '2.5rem', fontWeight: 900, color: '#fff', letterSpacing: '4px', marginBottom: '16px' }}>
                    SESSION TERMINATED
                </h1>

                <div style={{
                    background: 'rgba(220,38,38,0.08)', border: '1px solid rgba(220,38,38,0.2)',
                    borderRadius: '6px', padding: '20px 32px', maxWidth: '560px', marginBottom: '40px',
                }}>
                    <p style={{ color: '#fca5a5', lineHeight: 1.8, fontSize: '0.9rem' }}>
                        {terminationReason}
                    </p>
                </div>

                <div style={{ fontSize: '0.65rem', color: '#3f3f46', letterSpacing: '2px', marginBottom: '32px' }}>
                    This incident has been logged and flagged for review.
                </div>

                <button
                    type="button"
                    onClick={leaveWorkspace}
                    style={{
                        background: 'transparent', border: '1px solid #3f3f46',
                        color: '#71717a', padding: '12px 32px', borderRadius: '4px',
                        fontFamily: 'inherit', fontSize: '0.75rem', letterSpacing: '2px',
                        cursor: 'pointer', transition: 'all 0.2s',
                    }}
                    onMouseEnter={(event) => { event.currentTarget.style.borderColor = '#71717a'; event.currentTarget.style.color = '#a1a1aa'; }}
                    onMouseLeave={(event) => { event.currentTarget.style.borderColor = '#3f3f46'; event.currentTarget.style.color = '#71717a'; }}
                >
                    RETURN TO DASHBOARD
                </button>

                <style>{'@keyframes pulse { 0%,100% { opacity:1; } 50% { opacity:0.6; } }'}</style>
            </div>
        );
    }

    // ==========================================
    // LOADING / ERROR / EMPTY STATES
    // ==========================================
    if (isLoading) {
        return (
            <div style={{
                height: '100vh', display: 'flex', flexDirection: 'column',
                alignItems: 'center', justifyContent: 'center',
                background: '#050508', gap: '20px',
            }}>
                <div style={{
                    width: '40px', height: '40px', border: '2px solid #1a1a1f',
                    borderTop: '2px solid #ff7a00', borderRadius: '50%',
                    animation: 'spin 1s linear infinite',
                }} />
                <span style={{
                    color: '#ff7a00', fontFamily: '"Share Tech Mono", monospace',
                    letterSpacing: '4px', fontSize: '0.8rem',
                }}>
                    INITIALIZING SECURE WORKSPACE...
                </span>
                <style>{'@keyframes spin { to { transform: rotate(360deg); } }'}</style>
            </div>
        );
    }

    if (loadError) {
        return (
            <div style={{
                minHeight: '100vh', display: 'flex', alignItems: 'center', justifyContent: 'center',
                background: '#0a0503', padding: '24px', fontFamily: '"Inter", sans-serif',
            }}>
                <div style={{
                    maxWidth: '520px', textAlign: 'center', background: 'rgba(239,68,68,0.06)',
                    border: '1px solid rgba(239,68,68,0.3)', borderRadius: '16px', padding: '40px',
                }}>
                    <i className="bi bi-exclamation-octagon" style={{ fontSize: '2.4rem', color: '#ef4444' }} aria-hidden="true" />
                    <h2 style={{ color: '#fff', margin: '18px 0 10px', fontSize: '1.35rem' }}>
                        Workspace unavailable
                    </h2>
                    <p style={{ color: '#fca5a5', lineHeight: 1.7, fontSize: '0.92rem', marginBottom: '26px' }}>
                        {loadError}
                    </p>
                    <button
                        type="button"
                        onClick={leaveWorkspace}
                        style={{
                            background: 'transparent', border: '1px solid rgba(255,255,255,0.2)',
                            color: '#d4d4d8', padding: '11px 22px', borderRadius: '10px',
                            cursor: 'pointer', fontFamily: 'inherit',
                        }}
                    >
                        Back to dashboard
                    </button>
                </div>
            </div>
        );
    }

    if (problems.length === 0) {
        return (
            <div style={{
                minHeight: '100vh', display: 'flex', alignItems: 'center', justifyContent: 'center',
                background: '#0a0503', padding: '24px', fontFamily: '"Inter", sans-serif',
            }}>
                <div style={{
                    maxWidth: '520px', textAlign: 'center', background: 'rgba(255,255,255,0.02)',
                    border: '1px dashed rgba(255,255,255,0.15)', borderRadius: '16px', padding: '40px',
                }}>
                    <i className="bi bi-clipboard-x" style={{ fontSize: '2.4rem', color: '#71717a' }} aria-hidden="true" />
                    <h2 style={{ color: '#fff', margin: '18px 0 10px', fontSize: '1.35rem' }}>
                        No problems configured
                    </h2>
                    <p style={{ color: '#a1a1aa', lineHeight: 1.7, fontSize: '0.92rem', marginBottom: '26px' }}>
                        This assessment does not contain any problems yet. Contact the recruiter before
                        continuing.
                    </p>
                    <button
                        type="button"
                        onClick={leaveWorkspace}
                        style={{
                            background: 'transparent', border: '1px solid rgba(255,255,255,0.2)',
                            color: '#d4d4d8', padding: '11px 22px', borderRadius: '10px',
                            cursor: 'pointer', fontFamily: 'inherit',
                        }}
                    >
                        Back to dashboard
                    </button>
                </div>
            </div>
        );
    }

    if (!isFullscreenInit && !isTerminatedRef.current) {
        return (
            <div style={{
                minHeight: '100vh', display: 'flex', flexDirection: 'column',
                alignItems: 'center', justifyContent: 'center',
                background: '#0a0503', padding: '20px', fontFamily: '"Inter", sans-serif',
            }}>
                <i className="bi bi-shield-lock" style={{ fontSize: '2.6rem', color: '#ff7a00', marginBottom: '18px' }} aria-hidden="true" />
                <h1 style={{ color: '#fff', fontSize: '2.3rem', fontWeight: 800, fontFamily: '"Outfit", sans-serif', marginBottom: '16px', textAlign: 'center' }}>
                    Enter the <span style={{ color: '#ff7a00' }}>Arena</span>
                </h1>
                <p style={{ color: '#a1a1aa', maxWidth: '520px', textAlign: 'center', marginBottom: '34px', lineHeight: 1.7 }}>
                    This is a strictly proctored environment. Exiting full-screen, minimizing the window,
                    or clicking outside the workspace will result in session termination. Global copy and
                    paste are disabled.
                </p>
                <button
                    type="button"
                    onClick={() => {
                        document.documentElement.requestFullscreen().catch(() => {});
                        setIsFullscreenInit(true);
                    }}
                    style={{
                        background: 'linear-gradient(45deg, #ff3d00, #ff7a00)',
                        border: 'none', color: '#fff', padding: '16px 32px',
                        borderRadius: '12px', fontSize: '1rem', fontWeight: 700,
                        cursor: 'pointer', letterSpacing: '1px', textTransform: 'uppercase',
                        boxShadow: '0 10px 25px rgba(255,122,0,0.3)', transition: 'all 0.3s',
                        display: 'inline-flex', alignItems: 'center', gap: '10px',
                    }}
                >
                    <i className="bi bi-fullscreen" aria-hidden="true" />
                    Initialize secure workspace
                </button>
            </div>
        );
    }

    const diffColor = { EASY: '#00ffb4', MEDIUM: '#eab308', HARD: '#ef4444' }[activeProblem?.difficulty] || '#a1a1aa';
    const isLocked = isExpired || isTerminated;

    return (
        <div style={{
            display: 'flex', flexDirection: 'column', height: '100vh',
            background: '#050508', overflow: 'hidden', fontFamily: '"Rajdhani", sans-serif',
        }}>

            {/* WARNING OVERLAY (grace period) */}
            {warning && (
                <div style={{
                    position: 'fixed', inset: 0, zIndex: 9999,
                    background: 'rgba(0,0,0,0.88)', backdropFilter: 'blur(6px)',
                    display: 'flex', alignItems: 'center', justifyContent: 'center',
                }}>
                    <div style={{
                        background: '#0c0a00', border: '2px solid #eab308',
                        borderRadius: '8px', padding: '48px', textAlign: 'center',
                        maxWidth: '420px', boxShadow: '0 0 60px rgba(234,179,8,0.2)',
                    }}>
                        <div style={{
                            fontSize: '5rem', color: '#eab308', fontFamily: '"Share Tech Mono", monospace',
                            lineHeight: 1, marginBottom: '24px', textShadow: '0 0 20px rgba(234,179,8,0.5)',
                        }}>
                            {warning.countdown}
                        </div>
                        <h2 style={{ color: '#fbbf24', margin: '0 0 12px', fontSize: '1.05rem', letterSpacing: '3px', display: 'flex', alignItems: 'center', justifyContent: 'center', gap: '10px' }}>
                            <i className="bi bi-exclamation-triangle-fill" aria-hidden="true" />
                            SECURITY WARNING
                        </h2>
                        <p style={{ color: '#a16207', fontSize: '0.85rem', lineHeight: 1.7, margin: 0 }}>
                            {warning.reason}
                            <br />
                            <strong style={{ color: '#fbbf24' }}>Return to this window now</strong> or your
                            session will be permanently terminated.
                        </p>
                    </div>
                </div>
            )}

            {/* TOP NAV */}
            <header style={{
                height: '50px', background: 'rgba(5,5,8,0.98)',
                borderBottom: '1px solid rgba(255,255,255,0.05)',
                display: 'flex', alignItems: 'center', justifyContent: 'space-between',
                padding: '0 20px', flexShrink: 0,
            }}>
                <div style={{ display: 'flex', gap: '2px', height: '100%' }}>
                    {problems.map((problem, index) => {
                        const color = { EASY: '#00ffb4', MEDIUM: '#eab308', HARD: '#ef4444' }[problem.difficulty] || '#a1a1aa';
                        const isActive = activeIdx === index;
                        return (
                            <button
                                type="button"
                                key={problem.id}
                                onClick={() => { setActiveIdx(index); setConsoleOutput([]); }}
                                style={{
                                    height: '100%', padding: '0 20px',
                                    background: isActive ? `${color}08` : 'transparent',
                                    border: 'none',
                                    borderBottom: isActive ? `2px solid ${color}` : '2px solid transparent',
                                    color: isActive ? color : '#52525b',
                                    fontSize: '0.72rem', letterSpacing: '2px',
                                    fontFamily: '"Share Tech Mono", monospace',
                                    cursor: 'pointer', transition: 'all 0.2s',
                                }}
                            >
                                P{index + 1} · <span style={{ color: isActive ? color : '#3f3f46' }}>{problem.difficulty || 'UNRATED'}</span>
                            </button>
                        );
                    })}
                </div>

                <div style={{ display: 'flex', alignItems: 'center', gap: '16px' }}>
                    <div style={{
                        color: isExpired ? '#ef4444' : '#00ffb4',
                        fontFamily: '"Share Tech Mono", monospace',
                        fontSize: '1rem', letterSpacing: '3px',
                        display: 'flex', alignItems: 'center', gap: '8px',
                    }}>
                        <div style={{
                            width: '6px', height: '6px', borderRadius: '50%',
                            background: isExpired ? '#ef4444' : '#00ffb4',
                            boxShadow: isExpired ? 'none' : '0 0 6px #00ffb4',
                            animation: isExpired ? 'none' : 'navPulse 2s infinite',
                        }} />
                        {timeLeft || '--:--:--'}
                    </div>

                    {isExpired || isTerminated ? (
                        <button
                            type="button"
                            onClick={() => navigate(`/workspace/${assessmentId}/leaderboard`)}
                            style={{
                                background: 'rgba(0,255,180,0.08)',
                                border: '1px solid rgba(0,255,180,0.3)',
                                color: '#00ffb4', padding: '5px 14px', borderRadius: '3px',
                                cursor: 'pointer', fontSize: '0.68rem', letterSpacing: '1px',
                                fontFamily: '"Share Tech Mono", monospace',
                                display: 'inline-flex', alignItems: 'center', gap: '7px',
                            }}
                        >
                            <i className="bi bi-bar-chart-line" aria-hidden="true" />
                            VIEW LEADERBOARD
                        </button>
                    ) : (
                        <button
                            type="button"
                            onClick={handleFinish}
                            style={{
                                background: 'transparent',
                                border: '1px solid #ef4444',
                                color: '#ef4444', padding: '5px 14px', borderRadius: '3px',
                                cursor: 'pointer', fontSize: '0.68rem', letterSpacing: '1px',
                                fontFamily: '"Share Tech Mono", monospace', transition: 'all 0.2s',
                                display: 'inline-flex', alignItems: 'center', gap: '7px',
                            }}
                            onMouseEnter={(event) => { event.currentTarget.style.background = 'rgba(239,68,68,0.1)'; }}
                            onMouseLeave={(event) => { event.currentTarget.style.background = 'transparent'; }}
                        >
                            <i className="bi bi-box-arrow-right" aria-hidden="true" />
                            END ASSESSMENT
                        </button>
                    )}
                </div>
            </header>

            {/* MAIN SPLIT */}
            <div style={{ flex: 1, display: 'flex', overflow: 'hidden' }}>

                {/* LEFT PANE — Problem */}
                <div style={{
                    width: '42%', minWidth: '360px',
                    display: 'flex', flexDirection: 'column',
                    background: '#07070b',
                    borderRight: '1px solid rgba(255,255,255,0.04)',
                    overflow: 'hidden',
                }}>
                    <div style={{
                        padding: '18px 24px',
                        borderBottom: '1px solid rgba(255,255,255,0.04)',
                        flexShrink: 0,
                    }}>
                        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', gap: '12px' }}>
                            <div>
                                <div style={{ color: '#3f3f46', fontSize: '0.6rem', letterSpacing: '3px', fontFamily: '"Share Tech Mono", monospace', marginBottom: '6px' }}>
                                    PROBLEM {String(activeIdx + 1).padStart(2, '0')}
                                </div>
                                <h2 style={{ margin: 0, color: '#e4e4e7', fontSize: '1.2rem', fontWeight: 600, lineHeight: 1.3 }}>
                                    {activeProblem?.title}
                                </h2>
                            </div>
                            <span style={{
                                color: diffColor, padding: '4px 10px', borderRadius: '3px',
                                fontSize: '0.62rem', letterSpacing: '1px',
                                background: `${diffColor}10`, border: `1px solid ${diffColor}20`,
                                fontFamily: '"Share Tech Mono", monospace', flexShrink: 0, marginTop: '18px',
                            }}>
                                {activeProblem?.difficulty || 'UNRATED'}
                            </span>
                        </div>
                    </div>

                    <div style={{ flex: 1, overflowY: 'auto', padding: '20px 24px' }}>
                        <p style={{ color: '#a1a1aa', fontSize: '0.92rem', lineHeight: '1.85', marginBottom: '28px', whiteSpace: 'pre-wrap' }}>
                            {activeProblem?.description}
                        </p>

                        <div style={{ color: '#2d2d32', fontSize: '0.62rem', letterSpacing: '3px', fontFamily: '"Share Tech Mono", monospace', marginBottom: '12px' }}>
                            SAMPLE TEST CASES
                        </div>

                        {(!activeProblem?.testCases || activeProblem.testCases.length === 0) ? (
                            <div style={{ color: '#52525b', fontSize: '0.8rem', fontFamily: '"Share Tech Mono", monospace' }}>
                                No public test cases were provided for this problem.
                            </div>
                        ) : activeProblem.testCases.map((testCase, index) => (
                            <div key={index} style={{
                                background: 'rgba(255,255,255,0.012)',
                                border: '1px solid rgba(255,255,255,0.05)',
                                borderRadius: '5px', padding: '14px 16px', marginBottom: '10px',
                                fontFamily: '"Share Tech Mono", monospace', fontSize: '0.78rem',
                            }}>
                                <div style={{ color: '#3f3f46', fontSize: '0.6rem', letterSpacing: '2px', marginBottom: '10px' }}>
                                    CASE {String(index + 1).padStart(2, '0')}
                                </div>
                                <div style={{ marginBottom: '6px' }}>
                                    <span style={{ color: '#3f3f46' }}>Input</span>
                                    <span style={{ color: '#a1a1aa', marginLeft: '8px' }}>{testCase.inputs?.join(', ')}</span>
                                </div>
                                <div>
                                    <span style={{ color: '#3f3f46' }}>Expected</span>
                                    <span style={{ color: '#00ffb4', marginLeft: '8px' }}>{testCase.expectedOutput}</span>
                                </div>
                            </div>
                        ))}
                    </div>
                </div>

                {/* RIGHT PANE — Editor + Console */}
                <div style={{ flex: 1, display: 'flex', flexDirection: 'column', minWidth: 0, overflow: 'hidden' }}>

                    <div style={{
                        padding: '9px 20px',
                        background: '#07070b',
                        borderBottom: '1px solid rgba(255,255,255,0.04)',
                        display: 'flex', justifyContent: 'space-between', alignItems: 'center',
                        flexShrink: 0,
                    }}>
                        <div style={{ display: 'flex', alignItems: 'center', gap: '12px' }}>
                            <span style={{ color: '#27272a', fontSize: '0.6rem', letterSpacing: '2px', fontFamily: '"Share Tech Mono", monospace' }}>
                                JAVA 17
                            </span>
                            {isLocked && (
                                <span style={{ color: '#ef4444', fontSize: '0.6rem', letterSpacing: '2px', fontFamily: '"Share Tech Mono", monospace' }}>
                                    · READ ONLY
                                </span>
                            )}
                        </div>

                        <button
                            type="button"
                            onClick={handleSubmit}
                            disabled={isSubmitting || isLocked}
                            style={{
                                background: (isSubmitting || isLocked) ? 'transparent' : 'rgba(0,255,180,0.06)',
                                border: `1px solid ${(isSubmitting || isLocked) ? 'rgba(255,255,255,0.04)' : 'rgba(0,255,180,0.25)'}`,
                                color: (isSubmitting || isLocked) ? '#3f3f46' : '#00ffb4',
                                padding: '7px 22px', borderRadius: '3px', fontWeight: 600,
                                cursor: (isSubmitting || isLocked) ? 'not-allowed' : 'pointer',
                                fontSize: '0.7rem', letterSpacing: '2px',
                                fontFamily: '"Share Tech Mono", monospace', transition: 'all 0.2s',
                                display: 'inline-flex', alignItems: 'center', gap: '7px',
                            }}
                        >
                            <i className="bi bi-play-fill" aria-hidden="true" />
                            {isExpired ? 'TIME EXPIRED' : isSubmitting ? 'EXECUTING...' : 'SUBMIT SOLUTION'}
                        </button>
                    </div>

                    <div style={{ flex: 6, minHeight: 0 }}>
                        <Editor
                            height="100%"
                            language="java"
                            theme="vs-dark"
                            value={activeProblem ? (codeStore[activeProblem.id] || '') : ''}
                            onChange={handleEditorChange}
                            options={{
                                readOnly: isLocked,
                                minimap: { enabled: false },
                                fontSize: 13,
                                lineHeight: 21,
                                fontFamily: '"Share Tech Mono", monospace',
                                padding: { top: 14 },
                                scrollBeyondLastLine: false,
                                renderLineHighlight: 'gutter',
                                overviewRulerLanes: 0,
                                hideCursorInOverviewRuler: true,
                                contextmenu: false,
                            }}
                        />
                    </div>

                    <div style={{
                        flex: 3, minHeight: 0, display: 'flex', flexDirection: 'column',
                        background: '#030305', borderTop: '1px solid rgba(255,255,255,0.04)',
                    }}>
                        <div style={{
                            padding: '7px 16px', borderBottom: '1px solid rgba(255,255,255,0.03)',
                            display: 'flex', alignItems: 'center', justifyContent: 'space-between',
                            flexShrink: 0,
                        }}>
                            <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                                <div style={{ width: '4px', height: '4px', borderRadius: '50%', background: '#00ffb4', boxShadow: '0 0 4px #00ffb4' }} />
                                <span style={{ color: '#1c1c21', fontSize: '0.6rem', letterSpacing: '3px', fontFamily: '"Share Tech Mono", monospace' }}>
                                    EXECUTION CONSOLE
                                </span>
                            </div>
                            {consoleOutput.length > 0 && (
                                <button
                                    type="button"
                                    onClick={() => setConsoleOutput([])}
                                    style={{ background: 'none', border: 'none', color: '#27272a', cursor: 'pointer', fontSize: '0.6rem', fontFamily: 'inherit', letterSpacing: '1px' }}
                                >
                                    CLEAR
                                </button>
                            )}
                        </div>

                        <div style={{
                            flex: 1, overflowY: 'auto', padding: '10px 16px',
                            fontFamily: '"Share Tech Mono", monospace', fontSize: '0.78rem', minHeight: 0,
                        }}>
                            {consoleOutput.length === 0 ? (
                                <div style={{ color: '#18181b', fontSize: '0.7rem', paddingTop: '8px' }}>
                                    Awaiting execution...
                                </div>
                            ) : (
                                consoleOutput.map((log, index) => (
                                    <div key={index} style={{ marginBottom: '5px', display: 'flex', gap: '10px', lineHeight: 1.6 }}>
                                        <span style={{ color: '#1c1c21', flexShrink: 0 }}>{log.ts}</span>
                                        <span style={{ color: '#27272a', flexShrink: 0 }}>[{log.sender}]</span>
                                        <span style={{ color: log.color, whiteSpace: 'pre-wrap', wordBreak: 'break-word' }}>{log.message}</span>
                                    </div>
                                ))
                            )}
                        </div>
                    </div>

                </div>
            </div>

            <style>{`
                @keyframes navPulse {
                    0%, 100% { opacity: 1; }
                    50% { opacity: 0.3; }
                }
            `}</style>
        </div>
    );
}
