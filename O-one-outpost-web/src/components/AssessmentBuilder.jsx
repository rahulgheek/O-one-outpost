import React, { useState, useEffect } from 'react';
import axios from 'axios';
import { useNavigate } from 'react-router-dom';
import CompanyNav from './CompanyNav';

const API = `${import.meta.env.VITE_API_GATEWAY_URL}/api/assessment`;
const getToken = () => localStorage.getItem('algo_arena_token');
const authHeader = () => ({ Authorization: `Bearer ${getToken()}` });

const DIFF_COLOR = { EASY: 'var(--accent-cyan)', MEDIUM: 'var(--accent-yellow)', HARD: 'var(--accent-red)' };

export default function AssessmentBuilder() {
    const navigate = useNavigate(); 

    const [availableProblems, setAvailableProblems]   = useState([]);
    const [selectedProblemIds, setSelectedProblemIds] = useState([]);
    const [title, setTitle]         = useState('');
    const [targetRole, setTargetRole] = useState('');
    const [department, setDepartment] = useState('');
    const [duration, setDuration]   = useState(90);
    const [startTime, setStartTime] = useState('');
    const [isLoading, setIsLoading] = useState(false);
    const [error, setError]         = useState('');
    const [testCode, setTestCode]   = useState(null);

    const localNowForPicker = () => {
        const now = new Date();
        const offset = now.getTimezoneOffset() * 60000;
        return new Date(now - offset).toISOString().slice(0, 16);
    };

    useEffect(() => {
        axios.get(`${API}/company/problems`, { headers: authHeader() })
            .then(res => setAvailableProblems(res.data))
            .catch(err => setError(`Could not load question bank: ${err.message}`));
    }, []);

    const toggleProblem = (id) => {
        setSelectedProblemIds(prev =>
            prev.includes(id) ? prev.filter(p => p !== id) : [...prev, id]
        );
    };

    const handleSubmit = async (e) => {
        e.preventDefault();
        setError('');
        setTestCode(null);

        if (selectedProblemIds.length === 0) {
            setError('Select at least one problem before generating.');
            return;
        }

        const endTime = (() => {
            const [date, time] = startTime.split('T');
            const [h, m] = time.split(':').map(Number);
            const totalMinutes = h * 60 + m + parseInt(duration);
            const endH = Math.floor(totalMinutes / 60) % 24;
            const endM = totalMinutes % 60;
            const endDate = totalMinutes >= 24 * 60
                ? (() => {
                    const d = new Date(date);
                    d.setDate(d.getDate() + 1);
                    return d.toISOString().slice(0, 10);
                })()
                : date;
            return `${endDate}T${String(endH).padStart(2,'0')}:${String(endM).padStart(2,'0')}:00`;
        })();

        const payload = {
            title,
            description: `TARGET ROLE: ${targetRole} | DEPT: ${department}\n\nStrict time limit enforced. Complete all algorithms before the timer expires.`,
            durationMinutes: parseInt(duration),
            startTime: `${startTime}:00`,   
            endTime,                        
            companyProblemIds: selectedProblemIds,
        };

        setIsLoading(true);
        try {
            const res = await axios.post(`${API}/company/create`, payload, { headers: authHeader() });
            setTestCode(res.data.testCode);
            setTitle(''); setTargetRole(''); setDepartment('');
            setDuration(90); setStartTime(''); setSelectedProblemIds([]);
        } catch (err) {
            setError(err.response?.data || err.message);
        } finally {
            setIsLoading(false);
        }
    };

    const endPreview = (() => {
        if (!startTime) return null;
        const [date, time] = startTime.split('T');
        const [h, m] = time.split(':').map(Number);
        const total = h * 60 + m + parseInt(duration || 0);
        const eH = Math.floor(total / 60) % 24;
        const eM = total % 60;
        const suffix = eH >= 12 ? 'PM' : 'AM';
        const display = `${eH % 12 || 12}:${String(eM).padStart(2,'0')} ${suffix}`;
        return display;
    })();

    const startPreview = (() => {
        if (!startTime) return null;
        const [, time] = startTime.split('T');
        const [h, m] = time.split(':').map(Number);
        const suffix = h >= 12 ? 'PM' : 'AM';
        return `${h % 12 || 12}:${String(m).padStart(2,'0')} ${suffix}`;
    })();

    const inputStyle = { padding: '14px', background: 'rgba(255,255,255,0.04)', border: '1px solid rgba(255,255,255,0.1)', color: '#fff', outline: 'none', fontFamily: "'Inter', sans-serif", borderRadius: '12px', transition: 'all 0.3s', width: '100%', fontSize: '0.95rem' };
    const labelStyle = { display: 'block', color: '#888', fontSize: '0.8rem', fontWeight: 'bold', marginBottom: '8px', letterSpacing: '2px', textTransform: 'uppercase' };

    return (
        <div style={{ minHeight: '100vh', background: '#0a0503', color: '#fff', fontFamily: "'Inter', sans-serif" }}>
          <CompanyNav />

            <div className="animate-slide-up" style={{ maxWidth: '900px', margin: '0 auto', padding: '60px 40px' }}>

                {/* ── HEADER ─────────────────────────────────────── */}
                <div style={{ marginBottom: '40px', display: 'flex', justifyContent: 'space-between', alignItems: 'center', borderBottom: '1px solid var(--border-glass)', paddingBottom: '24px' }}>
                    <div style={{ display: 'flex', alignItems: 'center', gap: '20px' }}>
                        <div style={{ width: '60px', height: '60px', borderRadius: '12px', background: 'var(--accent-purple)', display: 'flex', alignItems: 'center', justifyContent: 'center', fontSize: '2rem', boxShadow: '0 0 20px rgba(139,92,246,0.3)' }}>
                            📋
                        </div>
                        <div>
                            <div style={{
                                color: 'var(--accent-purple)', fontSize: '0.75rem',
                                letterSpacing: '4px', marginBottom: '8px',
                                fontWeight: 'bold'
                            }}>
                                RECRUITER COMMAND CENTER
                            </div>
                            <h1 style={{ color: 'var(--text-primary)', fontSize: '2.5rem', fontWeight: 800, margin: 0, letterSpacing: '-1px' }}>
                                New <span style={{ color: 'var(--accent-purple)' }}>Assessment</span>
                            </h1>
                        </div>
                    </div>
                    
                    <button
                        type="button"
                        onClick={() => navigate('/company/question-bank')}
                        style={{
                            background: 'var(--accent-purple-dim)',
                            border: '1px solid var(--accent-purple)',
                            color: 'var(--accent-purple)',
                            padding: '12px 24px',
                            borderRadius: '6px',
                            cursor: 'pointer',
                            fontSize: '0.85rem',
                            letterSpacing: '2px',
                            fontFamily: 'var(--font-mono)',
                            transition: 'all 0.3s',
                            fontWeight: 'bold'
                        }}
                        onMouseEnter={e => {
                            e.target.style.background = 'rgba(139,92,246,0.2)';
                            e.target.style.boxShadow = 'var(--glow-purple)';
                        }}
                        onMouseLeave={e => {
                            e.target.style.background = 'var(--accent-purple-dim)';
                            e.target.style.boxShadow = 'none';
                        }}
                    >
                        + ADD TO QUESTION BANK
                    </button>
                </div>

                <div style={{ height: error ? 'auto' : '0', opacity: error ? 1 : 0, overflow: 'hidden', transition: 'all 0.3s ease', marginBottom: error ? '24px' : '0' }}>
                    {error && (
                        <div style={{
                            padding: '16px',
                            background: 'rgba(239,68,68,0.1)',
                            borderLeft: '4px solid var(--accent-red)',
                            color: '#fca5a5',
                            borderRadius: '4px',
                            fontSize: '0.9rem',
                        }}>
                            {error}
                        </div>
                    )}
                </div>

                <form onSubmit={handleSubmit} style={{ display: 'flex', flexDirection: 'column', gap: '24px' }}>

                    {/* ── SECTION 1: METADATA ────────────────────── */}
                    <Section label="01  ASSESSMENT DETAILS">
                        <Field label="TITLE">
                            <input
                                type="text"
                                value={title}
                                onChange={e => setTitle(e.target.value)}
                                placeholder="e.g. Backend Engineering — Final Round"
                                required
                                style={inputStyle}
                            />
                        </Field>
                        <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '20px' }}>
                            <Field label="TARGET ROLE">
                                <input type="text" value={targetRole} onChange={e => setTargetRole(e.target.value)}
                                    placeholder="e.g. SDE II" required style={inputStyle} />
                            </Field>
                            <Field label="DEPARTMENT">
                                <input type="text" value={department} onChange={e => setDepartment(e.target.value)}
                                    placeholder="e.g. Payments" required style={inputStyle} />
                            </Field>
                        </div>
                    </Section>

                    {/* ── SECTION 2: TIMING ──────────────────────── */}
                    <Section label="02  TIMING">
                        <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '20px' }}>
                            <Field label="START TIME (IST)">
                                <input
                                    type="datetime-local"
                                    value={startTime}
                                    min={localNowForPicker()}
                                    onChange={e => setStartTime(e.target.value)}
                                    required
                                    style={inputStyle}
                                />
                            </Field>
                            <Field label="DURATION (MINUTES)">
                                <input
                                    type="number" min="10" max="300"
                                    value={duration}
                                    onChange={e => setDuration(e.target.value)}
                                    required
                                    style={inputStyle}
                                />
                            </Field>
                        </div>

                        {startPreview && (
                            <div className="animate-fade-in" style={{
                                marginTop: '20px',
                                display: 'flex', gap: '32px',
                                background: 'var(--accent-purple-dim)',
                                border: '1px solid rgba(139,92,246,0.3)',
                                borderRadius: '8px', padding: '16px 24px',
                                fontSize: '0.85rem',
                            }}>
                                <div>
                                    <div style={{ color: 'var(--text-secondary)', marginBottom: '8px', fontSize: '0.7rem', letterSpacing: '2px', fontWeight: 'bold' }}>OPENS AT</div>
                                    <div style={{ color: 'var(--text-primary)', fontWeight: 'bold' }}>{startPreview} IST</div>
                                </div>
                                <div style={{ width: '1px', background: 'rgba(139,92,246,0.2)' }} />
                                <div>
                                    <div style={{ color: 'var(--text-secondary)', marginBottom: '8px', fontSize: '0.7rem', letterSpacing: '2px', fontWeight: 'bold' }}>CLOSES AT</div>
                                    <div style={{ color: 'var(--text-primary)', fontWeight: 'bold' }}>{endPreview} IST</div>
                                </div>
                                <div style={{ width: '1px', background: 'rgba(139,92,246,0.2)' }} />
                                <div>
                                    <div style={{ color: 'var(--text-secondary)', marginBottom: '8px', fontSize: '0.7rem', letterSpacing: '2px', fontWeight: 'bold' }}>DURATION</div>
                                    <div style={{ color: 'var(--text-primary)', fontWeight: 'bold' }}>{duration} min</div>
                                </div>
                            </div>
                        )}
                    </Section>

                    {/* ── SECTION 3: PROBLEMS ────────────────────── */}
                    <Section label={`03  PROBLEMS  ${selectedProblemIds.length > 0 ? `· ${selectedProblemIds.length} selected` : ''}`}>
                        {availableProblems.length === 0 ? (
                            <div style={{ color: 'var(--text-tertiary)', fontSize: '0.9rem', padding: '24px', textAlign: 'center', background: 'rgba(0,0,0,0.2)', border: '1px dashed var(--border-glass-strong)', borderRadius: '8px' }}>
                                No problems found in bank. Inject problems first.
                            </div>
                        ) : (
                            <div style={{ display: 'flex', flexDirection: 'column', gap: '12px' }}>
                                {availableProblems.map(p => {
                                    const dc = DIFF_COLOR[p.difficulty] || 'var(--text-secondary)';
                                    const selected = selectedProblemIds.includes(p.id);
                                    return (
                                        <label key={p.id} style={{
                                            display: 'flex', alignItems: 'center', gap: '16px',
                                            padding: '16px 20px', borderRadius: '8px', cursor: 'pointer',
                                            background: selected ? 'var(--accent-purple-dim)' : 'rgba(0,0,0,0.2)',
                                            border: `1px solid ${selected ? 'var(--accent-purple)' : 'var(--border-glass)'}`,
                                            transition: 'all 0.2s',
                                        }} onMouseEnter={e => {if(!selected) {e.currentTarget.style.background = 'var(--bg-tertiary)'; e.currentTarget.style.borderColor = 'var(--border-glass-strong)'}}} onMouseLeave={e => {if(!selected) {e.currentTarget.style.background = 'rgba(0,0,0,0.2)'; e.currentTarget.style.borderColor = 'var(--border-glass)'}}}>
                                            <input
                                                type="checkbox"
                                                checked={selected}
                                                onChange={() => toggleProblem(p.id)}
                                                style={{ width: '20px', height: '20px', accentColor: 'var(--accent-purple)', cursor: 'pointer', flexShrink: 0 }}
                                            />
                                            <span style={{ flex: 1, color: selected ? 'var(--text-primary)' : 'var(--text-secondary)', fontWeight: 'bold', fontSize: '1.05rem' }}>
                                                {p.title}
                                            </span>
                                            <span style={{
                                                color: dc,
                                                padding: '4px 12px', borderRadius: '4px',
                                                fontSize: '0.75rem', letterSpacing: '2px', fontWeight: 'bold',
                                            }}>
                                                {p.difficulty}
                                            </span>
                                        </label>
                                    );
                                })}
                            </div>
                        )}
                    </Section>

                    {/* ── SUBMIT ─────────────────────────────────── */}
                    <button
                        type="submit"
                        disabled={isLoading || selectedProblemIds.length === 0}
                        style={{
                            padding: '18px',
                            background: (isLoading || selectedProblemIds.length === 0)
                                ? 'var(--bg-tertiary)'
                                : 'var(--accent-purple-dim)',
                            border: `1px solid ${(isLoading || selectedProblemIds.length === 0) ? 'var(--border-glass)' : 'var(--accent-purple)'}`,
                            color: (isLoading || selectedProblemIds.length === 0) ? 'var(--text-tertiary)' : 'var(--accent-purple)',
                            fontWeight: 'bold', letterSpacing: '3px',
                            borderRadius: '6px', fontSize: '1rem',
                            cursor: (isLoading || selectedProblemIds.length === 0) ? 'not-allowed' : 'pointer',
                            transition: 'all 0.3s', marginTop: '10px'
                        }}
                        onMouseEnter={e => {if (!isLoading && selectedProblemIds.length > 0) {e.target.style.background = 'rgba(139,92,246,0.2)'; e.target.style.boxShadow = 'var(--glow-purple)'}}}
                        onMouseLeave={e => {if (!isLoading && selectedProblemIds.length > 0) {e.target.style.background = 'var(--accent-purple-dim)'; e.target.style.boxShadow = 'none'}}}
                    >
                        {isLoading ? 'GENERATING...' : 'GENERATE ASSESSMENT →'}
                    </button>
                </form>

                {/* ── SUCCESS ────────────────────────────────────── */}
                {testCode && (
                    <div style={{
                        marginTop: '40px',
                        padding: '40px',
                        background: 'radial-gradient(circle at center, rgba(0,255,180,0.1), transparent 70%)',
                        border: '1px solid rgba(0,255,180,0.3)',
                        borderRadius: '12px',
                        textAlign: 'center',
                        animation: 'rise 0.4s ease forwards',
                    }}>
                        <div style={{
                            color: 'var(--accent-cyan)', fontSize: '0.8rem',
                            letterSpacing: '4px', marginBottom: '16px', fontWeight: 'bold'
                        }}>
                            ACCESS KEY GENERATED
                        </div>
                        <div style={{
                            fontSize: '4.5rem', fontWeight: 800,
                            color: 'var(--text-primary)', letterSpacing: '12px',
                            textShadow: 'var(--glow-cyan)',
                            marginBottom: '20px',
                        }}>
                            {testCode}
                        </div>
                        <p style={{ color: 'var(--text-secondary)', fontSize: '0.9rem', margin: 0, marginBottom: '24px' }}>
                            Share this key with candidates. The gateway unlocks at the scheduled start time.
                        </p>
                        <button
                            onClick={() => { navigator.clipboard.writeText(testCode); }}
                            style={{
                                background: 'var(--accent-cyan-dim)',
                                border: '1px solid var(--accent-cyan)',
                                color: 'var(--accent-cyan)', padding: '12px 32px',
                                borderRadius: '6px', cursor: 'pointer', fontWeight: 'bold',
                                fontSize: '0.85rem', letterSpacing: '2px', transition: 'all 0.3s'
                            }}
                            onMouseEnter={e => {e.target.style.background = 'rgba(0,255,180,0.2)'; e.target.style.boxShadow = 'var(--glow-cyan)'}}
                            onMouseLeave={e => {e.target.style.background = 'var(--accent-cyan-dim)'; e.target.style.boxShadow = 'none'}}
                        >
                            COPY KEY
                        </button>
                    </div>
                )}
            </div>

            <style>{`
                @keyframes rise {
                    from { opacity: 0; transform: translateY(20px); }
                    to   { opacity: 1; transform: translateY(0); }
                }
                input[type="datetime-local"]::-webkit-calendar-picker-indicator {
                    filter: invert(0.8);
                    cursor: pointer;
                }
            `}</style>
        </div>
    );
}

function Section({ label, children }) {
    return (
        <div className="glass-panel" style={{ padding: '32px' }}>
            <div style={{
                color: 'var(--text-secondary)', fontSize: '0.8rem', fontWeight: 'bold',
                letterSpacing: '3px', marginBottom: '24px',
            }}>
                {label}
            </div>
            <div style={{ display: 'flex', flexDirection: 'column', gap: '20px' }}>
                {children}
            </div>
        </div>
    );
}

function Field({ label, children }) {
    return (
        <div>
            <label style={{
                display: 'block',
                color: 'var(--text-secondary)', fontSize: '0.75rem', fontWeight: 'bold',
                letterSpacing: '2px', marginBottom: '10px',
            }}>
                {label}
            </label>
            {children}
        </div>
    );
}

const inputStyle = {
    width: '100%', padding: '14px 16px',
    background: 'var(--bg-tertiary)',
    border: '1px solid var(--border-glass-strong)',
    borderRadius: '6px', color: 'var(--text-primary)',
    fontSize: '0.9rem', outline: 'none',
    boxSizing: 'border-box',
    transition: 'all 0.3s',
};