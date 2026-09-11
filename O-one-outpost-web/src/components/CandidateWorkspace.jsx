import React, { useState, useEffect, useCallback, useRef } from 'react';
import { useParams } from 'react-router-dom';
import Split from 'react-split';
import Editor from '@monaco-editor/react';
import ReactMarkdown from 'react-markdown';
import { candidateApi } from '../services/candidateApi';
import useTournamentTimer from '../hooks/useTournamentTimer';

const DIFFICULTY_STYLES = {
    EASY: 'bg-emerald-900/40 text-emerald-400 border border-emerald-800/60',
    MEDIUM: 'bg-amber-900/40 text-amber-400 border border-amber-800/60',
    HARD: 'bg-rose-900/40 text-rose-400 border border-rose-800/60',
};

const BOILERPLATE_BY_LANG = {
    java: `class Solution {\n    public void solve() {\n        // Write your code here\n    }\n}`,
    python: `class Solution:\n    def solve(self):\n        # Write your code here\n        pass`,
    javascript: `function solve() {\n    // Write your code here\n}`,
    cpp: `class Solution {\npublic:\n    void solve() {\n        // Write your code here\n    }\n};`,
};

const ProblemStatusDot = ({ status }) => {
    if (status === 'SOLVED') return <span className="w-2 h-2 rounded-full bg-emerald-400 shadow-[0_0_6px_rgba(52,211,153,0.8)]" />;
    if (status === 'ATTEMPTED') return <span className="w-2 h-2 rounded-full bg-amber-400" />;
    return <span className="w-2 h-2 rounded-full bg-gray-600" />;
};

const CandidateWorkspace = () => {
    const { assessmentId } = useParams();

    // Refs
    const videoRef = useRef(null);
    const mediaStreamRef = useRef(null);

    // State
    const [assessmentData, setAssessmentData] = useState(null);
    const [problems, setProblems] = useState([]);
    const [activeProblem, setActiveProblem] = useState(null);
    const [codeMap, setCodeMap] = useState({});
    const [resultMap, setResultMap] = useState({}); 
    const [consoleOutput, setConsoleOutput] = useState(null);
    const [consoleState, setConsoleState] = useState('idle'); 
    const [isSubmitting, setIsSubmitting] = useState(false);
    const [loadError, setLoadError] = useState(null);
    const [isLoading, setIsLoading] = useState(true);

    const [showFinishModal, setShowFinishModal] = useState(false);
    const [isFinishing, setIsFinishing] = useState(false);
    const [isFinished, setIsFinished] = useState(false);

    // Security State
    const [isTerminated, setIsTerminated] = useState(false);
    const [terminationReason, setTerminationReason] = useState("");

    const { timeLeft, isExpired } = useTournamentTimer(assessmentData?.endTime);

    // ==========================================
    // 🛡️ THE ANTI-CHEAT ENGINE (Now highly modular)
    // ==========================================
    const terminateSession = useCallback(async (reason) => {
        if (isTerminated || isFinished) return;
        setIsTerminated(true);
        setTerminationReason(reason);
        
        // Stop the webcam if they get terminated
        if (mediaStreamRef.current) {
            mediaStreamRef.current.getTracks().forEach(track => track.stop());
        }

        try {
            await candidateApi.finishAssessment(assessmentId, 'TERMINATED');
        } catch(e) { console.error(e); }
    }, [assessmentId, isTerminated, isFinished]);

    // 1. Browser Event Listeners
    useEffect(() => {
        const handleVisibilityChange = () => {
            if (document.hidden && !isFinished && !isTerminated) {
                terminateSession("Tab switching or minimizing the window is strictly forbidden.");
            }
        };

        const handleBlur = () => {
            if (!isFinished && !isTerminated) {
                terminateSession("Focus lost. You clicked outside the secure workspace.");
            }
        };

        const preventCopyPaste = (e) => {
            e.preventDefault();
            alert("SECURITY ALERT: Copying and Pasting are disabled in the Arena.");
        };

        const preventShortcuts = (e) => {
            if ((e.ctrlKey || e.metaKey) && (e.key === 'c' || e.key === 'v' || e.key === 'x')) {
                e.preventDefault();
                alert("SECURITY ALERT: Clipboard shortcuts are disabled.");
            }
        };

        const preventRightClick = (e) => e.preventDefault();

        const handleBeforeUnload = (e) => {
            if (!isFinished && !isTerminated) {
                e.preventDefault();
                e.returnValue = "You are in an active tournament. Leaving will lock your session forever.";
            }
        };

        window.addEventListener('visibilitychange', handleVisibilityChange);
        window.addEventListener('blur', handleBlur);
        window.addEventListener('copy', preventCopyPaste, true);
        window.addEventListener('paste', preventCopyPaste, true);
        window.addEventListener('cut', preventCopyPaste, true);
        window.addEventListener('keydown', preventShortcuts, true);
        window.addEventListener('contextmenu', preventRightClick);
        window.addEventListener('beforeunload', handleBeforeUnload);

        return () => {
            window.removeEventListener('visibilitychange', handleVisibilityChange);
            window.removeEventListener('blur', handleBlur);
            window.removeEventListener('copy', preventCopyPaste, true);
            window.removeEventListener('paste', preventCopyPaste, true);
            window.removeEventListener('cut', preventCopyPaste, true);
            window.removeEventListener('keydown', preventShortcuts, true);
            window.removeEventListener('contextmenu', preventRightClick);
            window.removeEventListener('beforeunload', handleBeforeUnload);
        };
    }, [isFinished, isTerminated, terminateSession]);

    // 2. Auto-Submit on Timer Expiry
    useEffect(() => {
        if (isExpired && !isFinished && !isTerminated) {
            candidateApi.finishAssessment(assessmentId, 'TIME_EXPIRED')
                .catch(() => {})
                .finally(() => setIsFinished(true));
        }
    }, [isExpired, isFinished, isTerminated, assessmentId]);

    // 3. 🚨 NEW: Webcam Proctoring Initialization
    useEffect(() => {
        const enableWebcam = async () => {
            try {
                if (!navigator.mediaDevices || !navigator.mediaDevices.getUserMedia) {
                    throw new Error("Browser does not support webcam access.");
                }
                const stream = await navigator.mediaDevices.getUserMedia({ video: true, audio: false });
                mediaStreamRef.current = stream;
                if (videoRef.current) {
                    videoRef.current.srcObject = stream;
                }
            } catch (err) {
                terminateSession("Webcam access is strictly required for proctoring. Please ensure your camera is connected and permissions are granted.");
            }
        };

        if (!isTerminated && !isFinished) {
            enableWebcam();
        }

        // Cleanup camera on unmount
        return () => {
            if (mediaStreamRef.current) {
                mediaStreamRef.current.getTracks().forEach(track => track.stop());
            }
        };
    }, [isTerminated, isFinished, terminateSession]);


    // ==========================================
    // ⚙️ WORKSPACE INITIALIZATION
    // ==========================================
    useEffect(() => {
        const initializeArena = async () => {
            setIsLoading(true);
            setLoadError(null);
            try {
                await candidateApi.startWorkspace(assessmentId);

                const problemsData = await candidateApi.getAssessmentProblems(assessmentId);
                setProblems(problemsData);

                if (problemsData.length > 0) {
                    setActiveProblem(problemsData[0]);
                    const initialCodes = {};
                    problemsData.forEach((p) => {
                        const lang = (p.preferredLanguage || 'java').toLowerCase();
                        initialCodes[p.id] = BOILERPLATE_BY_LANG[lang] || BOILERPLATE_BY_LANG.java;
                    });
                    setCodeMap(initialCodes);
                }

                const assessmentDetails = await candidateApi.getAssessmentDetails(assessmentId);
                setAssessmentData(assessmentDetails);
            } catch (error) {
                setLoadError(error.response?.data || 'Could not load this workspace. Make sure you are registered.');
            } finally {
                setIsLoading(false);
            }
        };
        initializeArena();
    }, [assessmentId]);


    // ==========================================
    // 🎮 ACTIONS
    // ==========================================
    const handleCodeChange = (newCode) => {
        if (!activeProblem) return;
        setCodeMap((prev) => ({ ...prev, [activeProblem.id]: newCode }));
    };

    const handleSubmit = useCallback(async () => {
        if (isExpired || isFinished || isTerminated || !activeProblem) return;

        setIsSubmitting(true);
        setConsoleState('running');
        setConsoleOutput('Executing in isolated sandbox… please wait.');

        try {
            const res = await candidateApi.submitCode(assessmentId, activeProblem.id, codeMap[activeProblem.id]);

            const { executionStatus, pointsAwarded, testCasesPassed, totalTestCases } = res;
            const fullyPassed = testCasesPassed === totalTestCases && totalTestCases > 0;

            setResultMap((prev) => ({
                ...prev,
                [activeProblem.id]: {
                    status: fullyPassed ? 'SOLVED' : 'ATTEMPTED',
                    points: pointsAwarded,
                    passed: testCasesPassed,
                    total: totalTestCases,
                },
            }));

            setConsoleState(fullyPassed ? 'success' : 'partial');
            setConsoleOutput(
                `Result: ${executionStatus}\nPoints awarded: +${pointsAwarded} pts\nTest cases passed: ${testCasesPassed}/${totalTestCases}`
            );
        } catch (error) {
            setConsoleState('error');
            setConsoleOutput(`Execution failed: ${error.response?.data || error.message}`);
            setResultMap((prev) => ({
                ...prev,
                [activeProblem.id]: { ...(prev[activeProblem.id] || {}), status: 'ATTEMPTED' },
            }));
        } finally {
            setIsSubmitting(false);
        }
    }, [isExpired, isFinished, isTerminated, activeProblem, codeMap, assessmentId]);

    const handleConfirmFinish = async () => {
        setIsFinishing(true);
        try {
            await candidateApi.finishAssessment(assessmentId, 'USER_SUBMITTED');
            setIsFinished(true);
            setShowFinishModal(false);
        } catch (error) {
            setConsoleState('error');
            setConsoleOutput(`Could not submit test: ${error.response?.data || error.message}`);
            setShowFinishModal(false);
        } finally {
            setIsFinishing(false);
        }
    };

    const solvedCount = Object.values(resultMap).filter((r) => r.status === 'SOLVED').length;
    const lowTime = typeof timeLeft === 'string' && /^0?[0-4]:/.test(timeLeft.split(':').length === 3 ? timeLeft.slice(timeLeft.indexOf(':') + 1) : timeLeft);

    // ==========================================
    // 🖥️ RENDER STATES
    // ==========================================
    if (isTerminated) {
        return (
            <div className="flex flex-col items-center justify-center h-screen bg-rose-950 text-center px-6 border-8 border-rose-600">
                <div className="w-20 h-20 bg-rose-600 rounded-full flex items-center justify-center animate-pulse mb-8 shadow-[0_0_50px_rgba(225,29,72,0.8)]">
                    <span className="text-white text-4xl font-black">!</span>
                </div>
                <h1 className="text-5xl font-black text-white tracking-widest mb-4 font-mono">SECURITY LOCKDOWN</h1>
                <p className="text-rose-300 text-xl max-w-2xl leading-relaxed mb-10">
                    {terminationReason}
                </p>
                <button 
                    onClick={() => window.location.href = '/candidate/dashboard'}
                    className="border-2 border-rose-500 text-rose-400 hover:bg-rose-600 hover:text-white px-8 py-3 rounded font-bold tracking-widest transition-all font-mono"
                >
                    RETURN TO DASHBOARD
                </button>
            </div>
        );
    }

    if (isLoading) {
        return (
            <div className="flex items-center justify-center h-screen bg-gray-900 text-gray-300 font-mono">
                <div className="flex flex-col items-center gap-3">
                    <div className="w-8 h-8 border-2 border-gray-600 border-t-blue-500 rounded-full animate-spin" />
                    <span>Loading secure arena…</span>
                </div>
            </div>
        );
    }

    if (loadError) {
        return (
            <div className="flex items-center justify-center h-screen bg-gray-900 text-white px-6">
                <div className="max-w-md text-center bg-gray-800 border border-gray-700 rounded-lg p-8">
                    <div className="text-rose-400 text-3xl mb-3">⚠</div>
                    <h2 className="text-lg font-semibold mb-2">Workspace Error</h2>
                    <p className="text-gray-400 text-sm">{loadError}</p>
                </div>
            </div>
        );
    }

    if (isFinished) {
        return (
            <div className="flex items-center justify-center h-screen bg-gray-900 text-white px-6">
                <div className="max-w-md text-center bg-gray-800 border border-gray-700 rounded-lg p-10 shadow-2xl">
                    <div className="text-emerald-400 text-4xl mb-3">✓</div>
                    <h2 className="text-xl font-semibold mb-2">Assessment Locked</h2>
                    <p className="text-gray-400 text-sm">
                        Your workspace has been permanently sealed. You successfully solved {solvedCount} of {problems.length} algorithms.
                        The global leaderboard will be unlocked once the tournament concludes.
                    </p>
                    <button 
                        onClick={() => window.location.href = '/candidate/dashboard'}
                        className="mt-6 border border-gray-600 text-gray-300 hover:text-white px-6 py-2 rounded font-bold text-sm tracking-widest transition-all font-mono"
                    >
                        EXIT ARENA
                    </button>
                </div>
            </div>
        );
    }

    if (!activeProblem) {
        return <div className="flex items-center justify-center h-screen bg-gray-900 text-gray-300 font-mono">No problems found for this assessment.</div>;
    }

    const activeResult = resultMap[activeProblem.id];

    return (
        <div className="flex flex-col h-screen w-screen overflow-hidden bg-gray-900 text-white font-sans relative">

            {/* 🚨 NEW: THE FLOATING PROCTORING CAM */}
            <div className="fixed bottom-6 right-6 w-48 h-36 bg-black rounded-lg border-2 border-gray-700 overflow-hidden shadow-2xl z-50 pointer-events-none group">
                <video 
                    ref={videoRef} 
                    autoPlay 
                    playsInline 
                    muted 
                    className="w-full h-full object-cover opacity-80"
                />
                <div className="absolute top-2 left-2 flex items-center gap-1.5 bg-black/60 px-2 py-0.5 rounded backdrop-blur-sm border border-white/10">
                    <div className="w-1.5 h-1.5 rounded-full bg-rose-500 animate-pulse" />
                    <span className="text-[0.6rem] font-mono tracking-widest text-gray-300">REC</span>
                </div>
            </div>

            {/* --- TOP NAVIGATION BAR --- */}
            <div className="flex justify-between items-center bg-gray-800 px-4 py-3 border-b border-gray-700 gap-4 min-w-0 flex-shrink-0">
                <div className="flex space-x-2 overflow-x-auto flex-1 min-w-0 custom-scrollbar">
                    {problems.map((prob, index) => {
                        const result = resultMap[prob.id];
                        const isActive = activeProblem.id === prob.id;
                        return (
                            <button
                                key={prob.id}
                                onClick={() => setActiveProblem(prob)}
                                className={`flex items-center gap-2 px-4 py-2 rounded-md font-medium text-sm transition-colors whitespace-nowrap ${
                                    isActive
                                        ? 'bg-blue-600 text-white'
                                        : 'bg-gray-700 text-gray-300 hover:bg-gray-600'
                                }`}
                            >
                                <ProblemStatusDot status={result?.status} />
                                {index + 1}. {prob.title}
                            </button>
                        );
                    })}
                </div>

                <div className="flex items-center space-x-4 flex-shrink-0">
                    <div className="hidden sm:block text-xs text-gray-400 font-mono">
                        Solved <span className="text-emerald-400 font-semibold">{solvedCount}</span>/{problems.length}
                    </div>
                    <div
                        className={`font-mono text-xl font-bold px-3 py-1 rounded-md ${
                            isExpired ? 'text-rose-500' : lowTime ? 'text-rose-400 animate-pulse' : 'text-gray-200'
                        }`}
                    >
                        {timeLeft || '--:--:--'}
                    </div>
                    <button
                        onClick={() => setShowFinishModal(true)}
                        disabled={isExpired}
                        className="px-4 py-2 rounded font-semibold text-sm border border-gray-600 text-gray-300 hover:border-rose-500 hover:text-rose-400 transition-colors disabled:opacity-40 disabled:cursor-not-allowed"
                    >
                        Submit Test
                    </button>
                </div>
            </div>

            {/* --- SPLIT PANE WORKSPACE --- */}
            <Split
                sizes={[40, 60]}
                minSize={300}
                expandToMin={false}
                gutterSize={8}
                gutterAlign="center"
                snapOffset={30}
                dragInterval={1}
                direction="horizontal"
                className="flex flex-row flex-1 min-w-0 overflow-hidden"
            >
                {/* LEFT PANE: Problem Description */}
                <div className="p-6 overflow-y-auto overflow-x-hidden bg-gray-900 custom-scrollbar min-w-0 pb-32">
                    <div className="flex items-start justify-between gap-3 mb-2">
                        <h1 className="text-2xl font-bold">{activeProblem.title}</h1>
                        {activeResult && (
                            <span
                                className={`text-xs font-semibold px-2 py-1 rounded whitespace-nowrap ${
                                    activeResult.status === 'SOLVED'
                                        ? 'bg-emerald-900/50 text-emerald-400'
                                        : 'bg-amber-900/50 text-amber-400'
                                }`}
                            >
                                {activeResult.status === 'SOLVED' ? 'Solved' : `${activeResult.passed}/${activeResult.total} cases`}
                            </span>
                        )}
                    </div>
                    <div className="flex space-x-3 mb-6 text-sm">
                        <span
                            className={`px-2 py-1 rounded font-bold text-xs ${
                                DIFFICULTY_STYLES[activeProblem.difficulty] || 'bg-gray-700 text-gray-300'
                            }`}
                        >
                            {activeProblem.difficulty}
                        </span>
                        {activeProblem.points && (
                            <span className="px-2 py-1 rounded font-medium text-xs bg-gray-800 text-gray-400 border border-gray-700">
                                {activeProblem.points} pts
                            </span>
                        )}
                    </div>

                    <div className="prose prose-invert max-w-none break-words prose-pre:bg-gray-800 prose-pre:overflow-x-auto prose-code:text-blue-300">
                        <ReactMarkdown>{activeProblem.description}</ReactMarkdown>
                    </div>
                </div>

                {/* RIGHT PANE: Editor & Console */}
                <div className="flex flex-col bg-gray-950 min-w-0">
                    <div className="flex items-center justify-between px-4 py-2 bg-gray-900 border-b border-gray-800">
                        <span className="text-xs uppercase tracking-wide text-gray-500 font-mono">
                            {(activeProblem.preferredLanguage || 'java').toLowerCase()}
                        </span>
                        <button
                            onClick={handleSubmit}
                            disabled={isSubmitting || isExpired}
                            className={`px-5 py-1.5 rounded font-bold text-sm shadow transition-all ${
                                isExpired
                                    ? 'bg-gray-700 cursor-not-allowed text-gray-500'
                                    : 'bg-green-600 hover:bg-green-500 text-white'
                            }`}
                        >
                            {isExpired ? "Time's Up" : isSubmitting ? 'Executing…' : 'Run Solution'}
                        </button>
                    </div>

                    <div className="flex-grow pt-2 relative">
                        <Editor
                            height="100%"
                            theme="vs-dark"
                            language={(activeProblem.preferredLanguage || 'java').toLowerCase()}
                            value={codeMap[activeProblem.id]}
                            onChange={handleCodeChange}
                            options={{
                                readOnly: isExpired || isTerminated,
                                minimap: { enabled: false },
                                fontSize: 15,
                                wordWrap: 'on',
                                scrollBeyondLastLine: false,
                                smoothScrolling: true,
                                contextmenu: false, // 🚨 Disables Right Click
                            }}
                        />
                    </div>

                    {/* Console / Output Window */}
                    <div className="h-48 bg-black border-t border-gray-800 p-4 font-mono text-sm overflow-y-auto">
                        <div className="text-gray-400 mb-2 flex items-center gap-2">
                            Terminal Output
                            {consoleState === 'running' && (
                                <span className="w-3 h-3 border-2 border-gray-600 border-t-blue-500 rounded-full animate-spin" />
                            )}
                        </div>
                        <pre
                            className={`whitespace-pre-wrap ${
                                consoleState === 'success'
                                    ? 'text-emerald-400'
                                    : consoleState === 'partial'
                                    ? 'text-amber-400'
                                    : consoleState === 'error'
                                    ? 'text-rose-400'
                                    : 'text-gray-500'
                            }`}
                        >
                            {consoleOutput || 'Awaiting code execution…'}
                        </pre>
                    </div>
                </div>
            </Split>

            {/* --- FINISH TEST CONFIRMATION MODAL --- */}
            {showFinishModal && (
                <div className="fixed inset-0 bg-black/60 flex items-center justify-center z-[100]">
                    <div className="bg-gray-800 border border-gray-700 rounded-lg p-6 max-w-sm w-full mx-4 shadow-2xl">
                        <h3 className="text-lg font-semibold mb-2">Submit the entire assessment?</h3>
                        <p className="text-sm text-gray-400 mb-4">
                            This permanently locks your workspace. You will not be able to edit or submit any
                            more code, even if there is time remaining on the clock.
                            <br/><br/>
                            Algorithms fully solved: <span className="text-emerald-400 font-semibold">{solvedCount} / {problems.length}</span>
                        </p>
                        <div className="flex justify-end gap-3">
                            <button
                                onClick={() => setShowFinishModal(false)}
                                disabled={isFinishing}
                                className="px-4 py-2 rounded text-sm text-gray-300 hover:bg-gray-700 transition-colors"
                            >
                                Cancel
                            </button>
                            <button
                                onClick={handleConfirmFinish}
                                disabled={isFinishing}
                                className="px-4 py-2 rounded text-sm font-semibold bg-rose-600 hover:bg-rose-500 text-white transition-colors disabled:opacity-50"
                            >
                                {isFinishing ? 'Securing Data…' : 'Submit Assessment'}
                            </button>
                        </div>
                    </div>
                </div>
            )}
        </div>
    );
};

export default CandidateWorkspace;