import React, { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { candidateApi } from '../services/candidateApi';
import { formatAssessmentDateTime } from '../utils/datetime';
import { getApiErrorMessage } from '../utils/errors';
import EngineerNav from './EngineerNav';

const PAGE_SIZE = 9;

const PHASE_LABEL = {
    UPCOMING: 'Registration open',
    ONGOING: 'In progress',
    ENDED: 'Concluded',
};

export default function AssessmentDirectory({ currentUser }) {
    const [pageData, setPageData] = useState({
        content: [],
        totalPages: 0,
        totalElements: 0,
        number: 0,
    });
    const [searchInput, setSearchInput] = useState('');
    const [search, setSearch] = useState('');
    const [page, setPage] = useState(0);
    const [isLoading, setIsLoading] = useState(true);
    const [error, setError] = useState('');
    const navigate = useNavigate();

    useEffect(() => {
        const handle = setTimeout(() => {
            setSearch(searchInput.trim());
            setPage(0);
        }, 350);
        return () => clearTimeout(handle);
    }, [searchInput]);

    useEffect(() => {
        let active = true;
        setIsLoading(true);
        setError('');

        candidateApi
            .getAssessmentDirectory(page, PAGE_SIZE, search)
            .then((data) => {
                if (!active) return;
                const totalPages = data.totalPages ?? 0;
                if (totalPages > 0 && page >= totalPages) {
                    setPage(totalPages - 1);
                    return;
                }
                setPageData({
                    content: data.content ?? [],
                    totalPages,
                    totalElements: data.totalElements ?? 0,
                    number: data.number ?? page,
                });
            })
            .catch((requestError) => {
                if (!active) return;
                setError(
                    getApiErrorMessage(requestError, 'Unable to load the tournament directory.'),
                );
            })
            .finally(() => {
                if (active) setIsLoading(false);
            });

        return () => {
            active = false;
        };
    }, [page, search]);

    return (
        <div className="dir-page">
            <style>{`
                * { box-sizing: border-box; margin: 0; padding: 0; }
                .dir-page {
                    min-height: 100vh;
                    background: #0a0503;
                    font-family: 'Inter', sans-serif;
                    color: #fff;
                    position: relative;
                }
                .dir-container {
                    max-width: 1200px;
                    margin: 0 auto;
                    padding: 60px 40px 80px;
                    position: relative;
                    z-index: 10;
                }
                .dir-header {
                    margin-bottom: 36px;
                    display: flex;
                    justify-content: space-between;
                    align-items: flex-end;
                    gap: 24px;
                    flex-wrap: wrap;
                }
                .dir-title {
                    font-family: 'Outfit', sans-serif;
                    font-size: 2.75rem;
                    font-weight: 800;
                    letter-spacing: -1px;
                    margin-bottom: 10px;
                }
                .dir-subtitle {
                    color: #a1a1aa;
                    font-size: 1rem;
                    max-width: 620px;
                    line-height: 1.6;
                }
                .dir-count {
                    color: #ff7a00;
                    font-family: 'Share Tech Mono', monospace;
                    font-size: 0.85rem;
                    letter-spacing: 1px;
                    white-space: nowrap;
                }
                .dir-toolbar {
                    display: flex;
                    align-items: center;
                    gap: 14px;
                    margin-bottom: 30px;
                    flex-wrap: wrap;
                }
                .search-wrap {
                    position: relative;
                    flex: 1;
                    min-width: 260px;
                }
                .search-wrap i {
                    position: absolute;
                    left: 16px;
                    top: 50%;
                    transform: translateY(-50%);
                    color: #71717a;
                }
                .dir-search {
                    width: 100%;
                    padding: 14px 16px 14px 44px;
                    background: rgba(255,255,255,0.04);
                    border: 1px solid rgba(255,255,255,0.1);
                    border-radius: 12px;
                    color: #fff;
                    font-family: 'Inter', sans-serif;
                    font-size: 0.95rem;
                    outline: none;
                    transition: border-color 0.2s, box-shadow 0.2s;
                }
                .dir-search:focus {
                    border-color: rgba(255,122,0,0.6);
                    box-shadow: 0 0 0 3px rgba(255,122,0,0.08);
                }
                .dir-search::placeholder { color: #52525b; }
                .dir-grid {
                    display: grid;
                    grid-template-columns: repeat(auto-fill, minmax(340px, 1fr));
                    gap: 26px;
                    transition: opacity 0.2s;
                }
                .dir-grid.loading { opacity: 0.45; pointer-events: none; }
                .dir-card {
                    background: linear-gradient(135deg, rgba(20,10,5,0.9), rgba(0,0,0,0.95));
                    border: 1px solid rgba(255, 122, 0, 0.15);
                    border-radius: 20px;
                    padding: 28px;
                    display: flex;
                    flex-direction: column;
                    justify-content: space-between;
                    transition: transform 0.3s, border-color 0.3s, box-shadow 0.3s;
                    backdrop-filter: blur(12px);
                    box-shadow: 0 10px 30px rgba(0,0,0,0.5);
                    position: relative;
                    overflow: hidden;
                }
                .dir-card::before {
                    content: '';
                    position: absolute;
                    top: 0; left: 0; width: 100%; height: 4px;
                    background: linear-gradient(90deg, #ff3d00, #ff7a00);
                    opacity: 0.8;
                }
                .dir-card:hover {
                    transform: translateY(-6px);
                    border-color: rgba(255, 122, 0, 0.4);
                    box-shadow: 0 18px 36px rgba(255, 122, 0, 0.12);
                }
                .card-header {
                    display: flex;
                    justify-content: space-between;
                    align-items: flex-start;
                    gap: 14px;
                    margin-bottom: 16px;
                }
                .card-title {
                    font-size: 1.35rem;
                    font-weight: 800;
                    color: #fff;
                    font-family: 'Outfit', sans-serif;
                    letter-spacing: -0.5px;
                    line-height: 1.3;
                }
                .phase-badge {
                    display: inline-flex;
                    align-items: center;
                    gap: 6px;
                    padding: 6px 10px;
                    border-radius: 20px;
                    font-size: 0.7rem;
                    font-weight: 700;
                    font-family: 'Share Tech Mono', monospace;
                    letter-spacing: 0.5px;
                    text-transform: uppercase;
                    white-space: nowrap;
                    flex-shrink: 0;
                }
                .phase-UPCOMING { color: #eab308; background: rgba(234,179,8,0.1); border: 1px solid rgba(234,179,8,0.3); }
                .phase-ONGOING { color: #00ffb4; background: rgba(0,255,180,0.1); border: 1px solid rgba(0,255,180,0.3); }
                .phase-ENDED { color: #a1a1aa; background: rgba(255,255,255,0.05); border: 1px solid rgba(255,255,255,0.12); }
                .card-desc {
                    color: #a1a1aa;
                    font-size: 0.93rem;
                    line-height: 1.65;
                    margin-bottom: 20px;
                    display: -webkit-box;
                    -webkit-line-clamp: 3;
                    -webkit-box-orient: vertical;
                    overflow: hidden;
                    min-height: 66px;
                }
                .card-meta {
                    display: flex;
                    gap: 18px;
                    flex-wrap: wrap;
                    margin-bottom: 20px;
                    color: #71717a;
                    font-family: 'Share Tech Mono', monospace;
                    font-size: 0.78rem;
                }
                .card-meta span { display: inline-flex; align-items: center; gap: 7px; }
                .card-meta i { color: #ff7a00; }
                .card-times {
                    font-size: 0.8rem;
                    font-family: 'Share Tech Mono', monospace;
                    color: #ff7a00;
                    margin-bottom: 24px;
                    background: rgba(255,122,0,0.03);
                    padding: 14px 16px;
                    border-radius: 12px;
                    border: 1px dashed rgba(255,122,0,0.12);
                }
                .card-times div {
                    display: flex;
                    justify-content: space-between;
                    gap: 12px;
                    margin-bottom: 8px;
                }
                .card-times div:last-child { margin-bottom: 0; }
                .card-times span { color: #e4e4e7; text-align: right; }
                .btn-register {
                    width: 100%;
                    background: linear-gradient(45deg, #ff3d00, #ff7a00);
                    border: none;
                    color: #fff;
                    padding: 15px;
                    border-radius: 12px;
                    font-weight: 700;
                    font-family: 'Inter', sans-serif;
                    letter-spacing: 1px;
                    cursor: pointer;
                    transition: transform 0.25s, box-shadow 0.25s;
                    text-transform: uppercase;
                    font-size: 0.9rem;
                    box-shadow: 0 8px 20px rgba(255, 122, 0, 0.25);
                    display: inline-flex;
                    align-items: center;
                    justify-content: center;
                    gap: 8px;
                }
                .btn-register:hover {
                    transform: translateY(-2px);
                    box-shadow: 0 12px 25px rgba(255, 122, 0, 0.4);
                }
                .btn-register.secondary {
                    background: transparent;
                    border: 1px solid rgba(255,122,0,0.4);
                    color: #ffb84d;
                    box-shadow: none;
                }
                .btn-register.secondary:hover {
                    background: rgba(255,122,0,0.08);
                    box-shadow: none;
                }
                .dir-loading {
                    min-height: 60vh;
                    display: flex;
                    flex-direction: column;
                    align-items: center;
                    justify-content: center;
                }
                .spinner {
                    width: 46px;
                    height: 46px;
                    border: 3px solid rgba(255, 122, 0, 0.1);
                    border-top-color: #ff7a00;
                    border-radius: 50%;
                    animation: spin 1s linear infinite;
                    margin-bottom: 18px;
                }
                .loading-text {
                    color: #ff7a00;
                    font-family: 'Share Tech Mono', monospace;
                    letter-spacing: 4px;
                    font-size: 0.9rem;
                }
                .dir-empty, .dir-error {
                    text-align: center;
                    color: #a1a1aa;
                    font-family: 'Share Tech Mono', monospace;
                    padding: 60px 24px;
                    background: rgba(255,255,255,0.02);
                    border: 1px dashed rgba(255,255,255,0.1);
                    border-radius: 16px;
                    letter-spacing: 1px;
                }
                .dir-empty i, .dir-error i { display: block; font-size: 2.2rem; margin-bottom: 14px; color: #3f3f46; }
                .dir-error { border-color: rgba(239,68,68,0.3); color: #fca5a5; }
                .dir-error i { color: #ef4444; }
                .dir-empty button {
                    margin-top: 18px;
                    background: transparent;
                    border: 1px solid rgba(255,122,0,0.4);
                    color: #ffb84d;
                    padding: 10px 18px;
                    border-radius: 10px;
                    cursor: pointer;
                    font-family: inherit;
                }
                .dir-pagination {
                    display: flex;
                    justify-content: center;
                    align-items: center;
                    gap: 14px;
                    margin-top: 40px;
                }
                .page-btn {
                    background: rgba(255,255,255,0.04);
                    border: 1px solid rgba(255,255,255,0.1);
                    color: #d4d4d8;
                    width: 42px;
                    height: 42px;
                    border-radius: 10px;
                    cursor: pointer;
                    transition: all 0.2s;
                    display: inline-flex;
                    align-items: center;
                    justify-content: center;
                }
                .page-btn:hover:not(:disabled) { border-color: rgba(255,122,0,0.5); color: #ff7a00; }
                .page-btn:disabled { opacity: 0.3; cursor: not-allowed; }
                .page-status {
                    color: #71717a;
                    font-family: 'Share Tech Mono', monospace;
                    font-size: 0.8rem;
                    letter-spacing: 1px;
                }
                @keyframes spin { to { transform: rotate(360deg); } }
                @media (max-width: 640px) {
                    .dir-container { padding: 40px 20px 60px; }
                    .dir-title { font-size: 2rem; }
                    .dir-grid { grid-template-columns: 1fr; }
                }
            `}</style>

            <EngineerNav currentUser={currentUser} />

            <div className="dir-container">
                <header className="dir-header">
                    <div>
                        <h1 className="dir-title">
                            Public <span style={{ color: '#ff7a00' }}>Tournaments</span>
                        </h1>
                        <p className="dir-subtitle">
                            Browse company assessments, register before the start time, and enter the arena
                            with your invite code.
                        </p>
                    </div>
                    <div className="dir-count">
                        {isLoading ? 'SYNCING...' : `${pageData.totalElements} OPEN ASSESSMENT${pageData.totalElements === 1 ? '' : 'S'}`}
                    </div>
                </header>

                <div className="dir-toolbar">
                    <div className="search-wrap">
                        <i className="bi bi-search" aria-hidden="true" />
                        <input
                            className="dir-search"
                            type="search"
                            value={searchInput}
                            onChange={(event) => setSearchInput(event.target.value)}
                            placeholder="Search tournaments by title..."
                            aria-label="Search tournaments by title"
                        />
                    </div>
                </div>

                {error ? (
                    <div className="dir-error">
                        <i className="bi bi-exclamation-octagon" aria-hidden="true" />
                        {error}
                    </div>
                ) : isLoading && pageData.content.length === 0 ? (
                    <div className="dir-loading">
                        <div className="spinner" />
                        <div className="loading-text">LOADING DIRECTORY...</div>
                    </div>
                ) : pageData.content.length === 0 ? (
                    <div className="dir-empty">
                        <i className="bi bi-calendar-x" aria-hidden="true" />
                        {search ? 'NO TOURNAMENTS MATCH YOUR SEARCH' : 'NO ACTIVE TOURNAMENTS AVAILABLE'}
                        {search && (
                            <button type="button" onClick={() => setSearchInput('')}>
                                Clear search
                            </button>
                        )}
                    </div>
                ) : (
                    <>
                        <div className={`dir-grid ${isLoading ? 'loading' : ''}`}>
                            {pageData.content.map((tournament) => {
                                const phase = tournament.phase || 'UPCOMING';
                                return (
                                    <article key={tournament.id} className="dir-card">
                                        <div>
                                            <div className="card-header">
                                                <h2 className="card-title">{tournament.title}</h2>
                                                <span className={`phase-badge phase-${phase}`}>
                                                    <i className={`bi ${phase === 'ONGOING' ? 'bi-broadcast' : 'bi-calendar-check'}`} aria-hidden="true" />
                                                    {PHASE_LABEL[phase] || phase}
                                                </span>
                                            </div>
                                            <p className="card-desc">
                                                {tournament.description || 'No description provided.'}
                                            </p>
                                            <div className="card-meta">
                                                <span>
                                                    <i className="bi bi-list-ol" aria-hidden="true" />
                                                    {tournament.problemCount} problem{tournament.problemCount === 1 ? '' : 's'}
                                                </span>
                                                <span>
                                                    <i className="bi bi-stopwatch" aria-hidden="true" />
                                                    {tournament.durationMinutes} minutes
                                                </span>
                                            </div>
                                            <div className="card-times">
                                                <div>
                                                    <span>STARTS</span>
                                                    <span>{formatAssessmentDateTime(tournament.startTime)}</span>
                                                </div>
                                                <div>
                                                    <span>ENDS</span>
                                                    <span>{formatAssessmentDateTime(tournament.endTime)}</span>
                                                </div>
                                            </div>
                                        </div>

                                        {phase === 'ONGOING' ? (
                                            <button
                                                type="button"
                                                onClick={() => navigate('/candidate/join')}
                                                className="btn-register secondary"
                                            >
                                                <i className="bi bi-key" aria-hidden="true" />
                                                Join with invite code
                                            </button>
                                        ) : (
                                            <button
                                                type="button"
                                                onClick={() => navigate(`/candidate/register/${tournament.id}`)}
                                                className="btn-register"
                                            >
                                                <i className="bi bi-pencil-square" aria-hidden="true" />
                                                Register now
                                            </button>
                                        )}
                                    </article>
                                );
                            })}
                        </div>

                        {pageData.totalPages > 1 && (
                            <nav className="dir-pagination" aria-label="Directory pagination">
                                <button
                                    type="button"
                                    className="page-btn"
                                    onClick={() => setPage((current) => Math.max(0, current - 1))}
                                    disabled={page === 0 || isLoading}
                                    aria-label="Previous page"
                                >
                                    <i className="bi bi-chevron-left" aria-hidden="true" />
                                </button>
                                <span className="page-status">
                                    PAGE {pageData.number + 1} OF {pageData.totalPages}
                                </span>
                                <button
                                    type="button"
                                    className="page-btn"
                                    onClick={() => setPage((current) => Math.min(pageData.totalPages - 1, current + 1))}
                                    disabled={pageData.number + 1 >= pageData.totalPages || isLoading}
                                    aria-label="Next page"
                                >
                                    <i className="bi bi-chevron-right" aria-hidden="true" />
                                </button>
                            </nav>
                        )}
                    </>
                )}
            </div>
        </div>
    );
}
