import { useEffect, useMemo, useState } from 'react';
import axios from 'axios';
import CompanyNav from '../components/CompanyNav';
import {
    formatAssessmentDate,
    formatAssessmentDateTime,
    formatDurationSeconds,
    parseAssessmentTime,
    shortCandidateId,
} from '../utils/datetime';
import { getApiErrorMessage } from '../utils/errors';

const API = `${import.meta.env.VITE_API_GATEWAY_URL}/api/assessment/company`;
const getToken = () => localStorage.getItem('algo_arena_token');

const STATUS_LABEL = {
    REGISTERED: 'Registered',
    IN_PROGRESS: 'In progress',
    COMPLETED: 'Completed',
    TERMINATED: 'Terminated',
};

const statusClass = (status) => `status-${(status || 'REGISTERED').toLowerCase().replace(/_/g, '')}`;

export default function SelectionProcess() {
    const [assessments, setAssessments] = useState([]);
    const [selectedAssessmentId, setSelectedAssessmentId] = useState('');
    const [candidates, setCandidates] = useState([]);
    const [loadingAssessments, setLoadingAssessments] = useState(true);
    const [loadingCandidates, setLoadingCandidates] = useState(false);
    const [error, setError] = useState('');
    const [candidateError, setCandidateError] = useState('');
    const [search, setSearch] = useState('');

    useEffect(() => {
        let active = true;
        axios
            .get(`${API}/assessments`, { headers: { Authorization: `Bearer ${getToken()}` } })
            .then((response) => {
                if (!active) return;
                const sorted = [...(response.data ?? [])].sort(
                    (first, second) => parseAssessmentTime(second.startTime) - parseAssessmentTime(first.startTime),
                );
                setAssessments(sorted);
                setSelectedAssessmentId(sorted[0]?.id ?? '');
            })
            .catch((requestError) => {
                if (!active) return;
                setError(
                    getApiErrorMessage(requestError, 'Failed to fetch tournaments.'),
                );
            })
            .finally(() => {
                if (active) setLoadingAssessments(false);
            });

        return () => {
            active = false;
        };
    }, []);

    useEffect(() => {
        if (!selectedAssessmentId) {
            setCandidates([]);
            return undefined;
        }

        let active = true;
        setLoadingCandidates(true);
        setCandidateError('');

        axios
            .get(`${API}/${selectedAssessmentId}/results`, {
                headers: { Authorization: `Bearer ${getToken()}` },
            })
            .then((response) => {
                if (!active) return;
                const sorted = [...(response.data ?? [])].sort((first, second) => {
                    const points = (second.totalPoints ?? 0) - (first.totalPoints ?? 0);
                    if (points !== 0) return points;
                    const penalty = (first.totalTimePenaltySeconds ?? 0) - (second.totalTimePenaltySeconds ?? 0);
                    if (penalty !== 0) return penalty;
                    return (first.fullName || first.candidateId || '').localeCompare(
                        second.fullName || second.candidateId || '',
                    );
                });
                setCandidates(sorted);
            })
            .catch((requestError) => {
                if (!active) return;
                setCandidateError(
                    getApiErrorMessage(
                        requestError,
                        'Failed to fetch candidates for this tournament.',
                    ),
                );
            })
            .finally(() => {
                if (active) setLoadingCandidates(false);
            });

        return () => {
            active = false;
        };
    }, [selectedAssessmentId]);

    const selectedAssessment = assessments.find((assessment) => assessment.id === selectedAssessmentId);

    const filteredCandidates = useMemo(() => {
        const query = search.trim().toLowerCase();
        if (!query) return candidates;
        return candidates.filter((candidate) => (
            candidate.fullName?.toLowerCase().includes(query)
            || candidate.candidateId?.toLowerCase().includes(query)
            || candidate.college?.toLowerCase().includes(query)
            || candidate.status?.toLowerCase().includes(query)
        ));
    }, [candidates, search]);

    const stats = useMemo(() => ({
        total: candidates.length,
        inProgress: candidates.filter((candidate) => candidate.status === 'IN_PROGRESS').length,
        completed: candidates.filter((candidate) => candidate.status === 'COMPLETED').length,
        terminated: candidates.filter((candidate) => candidate.status === 'TERMINATED').length,
    }), [candidates]);

    return (
        <>
            <style>{`
                * { box-sizing: border-box; margin: 0; padding: 0; }
                body { background: #0a0503; margin: 0; color: #fff; font-family: 'Inter', sans-serif; }
                .selection-page { min-height: 100vh; position: relative; }
                .selection-container { position: relative; z-index: 10; max-width: 1200px; margin: 0 auto; padding: 60px 40px 80px; }
                .selection-header { margin-bottom: 32px; display: flex; justify-content: space-between; align-items: flex-end; gap: 24px; flex-wrap: wrap; }
                .selection-title { font-family: 'Outfit', sans-serif; font-size: 2.6rem; font-weight: 800; letter-spacing: -1px; margin-bottom: 8px; }
                .selection-subtitle { color: #888; font-size: 1rem; max-width: 620px; }
                .glass-panel {
                    background: rgba(15, 8, 3, 0.45);
                    backdrop-filter: blur(12px);
                    border: 1px solid rgba(255, 122, 0, 0.15);
                    border-radius: 22px;
                    padding: 32px;
                    box-shadow: 0 20px 40px rgba(0,0,0,0.4);
                }
                .field-label { margin-bottom: 12px; color: #888; font-size: 0.75rem; text-transform: uppercase; letter-spacing: 2px; font-weight: 700; }
                .select-wrap { position: relative; }
                .select-style {
                    width: 100%;
                    padding: 15px 44px 15px 16px;
                    background: rgba(255,255,255,0.04);
                    border: 1px solid rgba(255,255,255,0.1);
                    color: #fff;
                    outline: none;
                    font-family: 'Inter', sans-serif;
                    border-radius: 12px;
                    font-size: 0.95rem;
                    transition: border-color 0.2s, box-shadow 0.2s;
                    appearance: none;
                    cursor: pointer;
                }
                .select-style:focus { border-color: #ff7a00; box-shadow: 0 0 0 3px rgba(255,122,0,0.08); }
                .select-wrap i { position: absolute; right: 18px; top: 50%; transform: translateY(-50%); color: #888; pointer-events: none; }
                .selection-error {
                    padding: 16px; background: rgba(239,68,68,0.08);
                    border: 1px solid rgba(239,68,68,0.3); color: #fca5a5;
                    border-radius: 12px; font-size: 0.9rem;
                }
                .selection-stats { display: flex; gap: 12px; flex-wrap: wrap; margin: 26px 0; }
                .selection-stat {
                    display: inline-flex; align-items: center; gap: 9px;
                    padding: 10px 15px; border-radius: 12px;
                    background: rgba(255,255,255,0.03); border: 1px solid rgba(255,255,255,0.08);
                    color: #a1a1aa; font-family: 'Share Tech Mono', monospace;
                    font-size: 0.75rem; letter-spacing: 1px;
                }
                .selection-stat i { color: #ff7a00; }
                .selection-stat strong { color: #fff; }
                .panel-head { display: flex; justify-content: space-between; align-items: center; gap: 18px; flex-wrap: wrap; margin-bottom: 20px; }
                .panel-title { font-family: 'Outfit', sans-serif; font-size: 1.45rem; font-weight: 700; }
                .search-wrap { position: relative; min-width: 240px; flex: 1; max-width: 360px; }
                .search-wrap i { position: absolute; left: 14px; top: 50%; transform: translateY(-50%); color: #71717a; }
                .table-search {
                    width: 100%; padding: 11px 14px 11px 40px;
                    background: rgba(255,255,255,0.04); border: 1px solid rgba(255,255,255,0.1);
                    border-radius: 10px; color: #fff; outline: none; font-family: 'Inter', sans-serif;
                    font-size: 0.88rem;
                }
                .table-search:focus { border-color: rgba(255,122,0,0.5); }
                .candidate-table { width: 100%; border-collapse: collapse; }
                .candidate-table th { text-align: left; padding: 14px 16px; border-bottom: 1px solid rgba(255,255,255,0.1); color: #888; font-size: 0.72rem; text-transform: uppercase; letter-spacing: 1px; white-space: nowrap; }
                .candidate-table td { padding: 15px 16px; border-bottom: 1px solid rgba(255,255,255,0.04); color: #fff; font-size: 0.9rem; vertical-align: middle; }
                .candidate-table tr:hover td { background: rgba(255,122,0,0.04); }
                .candidate-name { font-weight: 600; }
                .candidate-id { color: #71717a; font-family: 'Share Tech Mono', monospace; font-size: 0.74rem; margin-top: 3px; }
                .college { color: #a1a1aa; font-size: 0.82rem; }
                .status-badge { padding: 5px 11px; border-radius: 20px; font-size: 0.68rem; font-weight: 600; text-transform: uppercase; letter-spacing: 0.8px; display: inline-block; white-space: nowrap; }
                .status-completed { background: rgba(39,201,63,0.1); color: #27c93f; border: 1px solid rgba(39,201,63,0.3); }
                .status-inprogress { background: rgba(255,122,0,0.1); color: #ff7a00; border: 1px solid rgba(255,122,0,0.3); }
                .status-terminated { background: rgba(239,68,68,0.1); color: #ef4444; border: 1px solid rgba(239,68,68,0.3); }
                .status-registered { background: rgba(255,255,255,0.05); color: #aaa; border: 1px solid rgba(255,255,255,0.1); }
                .score { font-family: 'Outfit', sans-serif; font-size: 1.1rem; font-weight: 800; color: #00ffb4; }
                .mono { font-family: 'Share Tech Mono', monospace; color: #a1a1aa; font-size: 0.82rem; }
                .rank { font-family: 'Outfit', sans-serif; font-weight: 800; color: #888; }
                .rank-top { color: #ff7a00; }
                .empty-state { padding: 50px 20px; text-align: center; color: #888; font-family: 'Share Tech Mono', monospace; letter-spacing: 1px; }
                .empty-state i { display: block; font-size: 2rem; color: #3f3f46; margin-bottom: 12px; }
                .loading-state { padding: 30px; color: #ff7a00; font-family: 'Share Tech Mono', monospace; font-size: 0.85rem; letter-spacing: 1px; }
                .table-scroll { overflow-x: auto; }
                @media (max-width: 720px) {
                    .selection-container { padding: 40px 20px 60px; }
                    .selection-title { font-size: 2rem; }
                    .glass-panel { padding: 24px; }
                    .search-wrap { max-width: none; }
                }
            `}</style>

            <div className="selection-page">
                <div style={{ position: 'fixed', inset: 0, background: 'radial-gradient(ellipse at top right, rgba(255, 122, 0, 0.08) 0%, transparent 60%)', pointerEvents: 'none', zIndex: 0 }} />

                <CompanyNav />

                <div className="selection-container">
                    <header className="selection-header">
                        <div>
                            <h1 className="selection-title">
                                Candidate <span style={{ color: '#ff7a00' }}>Selection</span>
                            </h1>
                            <p className="selection-subtitle">
                                Compare candidate scores and timing, then shortlist the engineers who
                                performed best.
                            </p>
                        </div>
                    </header>

                    <div className="glass-panel" style={{ marginBottom: '28px' }}>
                        <div className="field-label">Select tournament</div>

                        {loadingAssessments ? (
                            <div className="loading-state">LOADING TOURNAMENTS...</div>
                        ) : error ? (
                            <div className="selection-error">{error}</div>
                        ) : assessments.length === 0 ? (
                            <div className="empty-state">
                                <i className="bi bi-calendar-plus" aria-hidden="true" />
                                You have not created any tournaments yet.
                            </div>
                        ) : (
                            <div className="select-wrap">
                                <select
                                    className="select-style"
                                    value={selectedAssessmentId}
                                    onChange={(event) => {
                                        setSelectedAssessmentId(event.target.value);
                                        setSearch('');
                                    }}
                                    aria-label="Select a tournament to view candidates"
                                >
                                    {assessments.map((assessment) => (
                                        <option key={assessment.id} value={assessment.id}>
                                            {assessment.title} · {formatAssessmentDate(assessment.startTime)}
                                            {assessment.testCode ? ` · Code ${assessment.testCode}` : ''}
                                        </option>
                                    ))}
                                </select>
                                <i className="bi bi-chevron-expand" aria-hidden="true" />
                            </div>
                        )}
                    </div>

                    {selectedAssessmentId && (
                        <div className="glass-panel">
                            <div className="panel-head">
                                <div>
                                    <h2 className="panel-title">Leaderboard &amp; performance</h2>
                                    {selectedAssessment && (
                                        <div className="mono" style={{ marginTop: '6px' }}>
                                            {formatAssessmentDateTime(selectedAssessment.startTime)}
                                            {' — '}
                                            {formatAssessmentDateTime(selectedAssessment.endTime)}
                                        </div>
                                    )}
                                </div>
                                <div className="search-wrap">
                                    <i className="bi bi-search" aria-hidden="true" />
                                    <input
                                        className="table-search"
                                        type="search"
                                        value={search}
                                        onChange={(event) => setSearch(event.target.value)}
                                        placeholder="Search candidates..."
                                        aria-label="Search candidates"
                                    />
                                </div>
                            </div>

                            <div className="selection-stats">
                                <span className="selection-stat">
                                    <i className="bi bi-people" aria-hidden="true" />
                                    Registered <strong>{stats.total}</strong>
                                </span>
                                <span className="selection-stat">
                                    <i className="bi bi-broadcast" aria-hidden="true" />
                                    In progress <strong>{stats.inProgress}</strong>
                                </span>
                                <span className="selection-stat">
                                    <i className="bi bi-check2-circle" aria-hidden="true" />
                                    Completed <strong>{stats.completed}</strong>
                                </span>
                                <span className="selection-stat">
                                    <i className="bi bi-exclamation-triangle" aria-hidden="true" />
                                    Terminated <strong>{stats.terminated}</strong>
                                </span>
                            </div>

                            {candidateError ? (
                                <div className="selection-error">{candidateError}</div>
                            ) : loadingCandidates ? (
                                <div className="loading-state">LOADING CANDIDATES...</div>
                            ) : candidates.length === 0 ? (
                                <div className="empty-state">
                                    <i className="bi bi-person-x" aria-hidden="true" />
                                    No candidates have registered for this tournament yet.
                                </div>
                            ) : filteredCandidates.length === 0 ? (
                                <div className="empty-state">
                                    <i className="bi bi-search" aria-hidden="true" />
                                    No candidates match “{search}”.
                                </div>
                            ) : (
                                <div className="table-scroll">
                                    <table className="candidate-table">
                                        <thead>
                                            <tr>
                                                <th>Rank</th>
                                                <th>Candidate</th>
                                                <th>College</th>
                                                <th>Status</th>
                                                <th>Score</th>
                                                <th>Time</th>
                                                <th>Started</th>
                                            </tr>
                                        </thead>
                                        <tbody>
                                            {filteredCandidates.map((candidate, index) => (
                                                <tr key={candidate.registrationId || candidate.candidateId}>
                                                    <td className={`rank ${index < 3 ? 'rank-top' : ''}`}>#{index + 1}</td>
                                                    <td>
                                                        <div className="candidate-name">
                                                            {candidate.fullName || 'Unnamed candidate'}
                                                        </div>
                                                        <div className="candidate-id" title={candidate.candidateId}>
                                                            {shortCandidateId(candidate.candidateId)}
                                                        </div>
                                                    </td>
                                                    <td className="college">
                                                        {candidate.college || '—'}
                                                        {candidate.degree ? ` · ${candidate.degree}` : ''}
                                                    </td>
                                                    <td>
                                                        <span className={`status-badge ${statusClass(candidate.status)}`}>
                                                            {STATUS_LABEL[candidate.status] || candidate.status}
                                                        </span>
                                                    </td>
                                                    <td className="score">{candidate.totalPoints ?? 0}</td>
                                                    <td className="mono">
                                                        {(candidate.totalTimePenaltySeconds ?? 0) > 0
                                                            ? formatDurationSeconds(candidate.totalTimePenaltySeconds)
                                                            : '—'}
                                                    </td>
                                                    <td className="mono">
                                                        {candidate.startedAt
                                                            ? formatAssessmentDateTime(candidate.startedAt)
                                                            : 'Not started'}
                                                    </td>
                                                </tr>
                                            ))}
                                        </tbody>
                                    </table>
                                </div>
                            )}
                        </div>
                    )}
                </div>
            </div>
        </>
    );
}
