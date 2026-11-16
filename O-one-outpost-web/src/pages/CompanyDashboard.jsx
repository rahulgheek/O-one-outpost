import { useEffect, useRef, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import axios from 'axios';
import CompanyNav from '../components/CompanyNav';
import { formatAssessmentDateTime, parseAssessmentTime } from '../utils/datetime';
import { getApiErrorMessage } from '../utils/errors';

const API = `${import.meta.env.VITE_API_GATEWAY_URL}/api/assessment/company`;

export default function CompanyDashboard() {
    const [problems, setProblems] = useState([]);
    const [assessments, setAssessments] = useState([]);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState('');
    const navigate = useNavigate();
    const canvasRef = useRef(null);

    useEffect(() => {
        let active = true;
        const token = localStorage.getItem('algo_arena_token');
        const headers = { Authorization: `Bearer ${token}` };

        Promise.all([
            axios.get(`${API}/problems`, { headers }),
            axios.get(`${API}/assessments`, { headers }),
        ])
            .then(([problemsResponse, assessmentsResponse]) => {
                if (!active) return;
                setProblems(problemsResponse.data ?? []);
                setAssessments(assessmentsResponse.data ?? []);
            })
            .catch((requestError) => {
                if (!active) return;
                setError(
                    getApiErrorMessage(requestError, 'Unable to load the company workspace.'),
                );
            })
            .finally(() => {
                if (active) setLoading(false);
            });

        return () => {
            active = false;
        };
    }, []);

    // Particle background
    useEffect(() => {
        const canvas = canvasRef.current;
        if (!canvas) return undefined;
        const ctx = canvas.getContext('2d');
        let animationId;

        const resize = () => {
            canvas.width = window.innerWidth;
            canvas.height = window.innerHeight;
        };
        resize();
        window.addEventListener('resize', resize);

        const particles = Array.from({ length: 40 }, () => ({
            x: Math.random() * (canvas.width || 1200),
            y: Math.random() * (canvas.height || 900),
            r: Math.random() * 2 + 1,
            dx: (Math.random() - 0.5) * 0.3,
            dy: (Math.random() - 0.5) * 0.3,
            alpha: Math.random() * 0.3 + 0.05,
        }));

        const draw = () => {
            ctx.clearRect(0, 0, canvas.width, canvas.height);
            for (let i = 0; i < particles.length; i += 1) {
                const particle = particles[i];
                ctx.beginPath();
                ctx.arc(particle.x, particle.y, particle.r, 0, Math.PI * 2);
                ctx.fillStyle = `rgba(255,122,0,${particle.alpha})`;
                ctx.fill();
                particle.x += particle.dx;
                particle.y += particle.dy;
                if (particle.x < 0 || particle.x > canvas.width) particle.dx *= -1;
                if (particle.y < 0 || particle.y > canvas.height) particle.dy *= -1;

                for (let j = i + 1; j < particles.length; j += 1) {
                    const other = particles[j];
                    const distance = Math.hypot(particle.x - other.x, particle.y - other.y);
                    if (distance < 120) {
                        ctx.beginPath();
                        ctx.strokeStyle = `rgba(255, 122, 0, ${0.15 * (1 - distance / 120)})`;
                        ctx.lineWidth = 1;
                        ctx.moveTo(particle.x, particle.y);
                        ctx.lineTo(other.x, other.y);
                        ctx.stroke();
                    }
                }
            }
            animationId = requestAnimationFrame(draw);
        };
        draw();

        return () => {
            cancelAnimationFrame(animationId);
            window.removeEventListener('resize', resize);
        };
    }, []);

    const now = new Date();
    const activeAssessments = assessments.filter((assessment) => {
        const endTime = parseAssessmentTime(assessment.endTime);
        return endTime && endTime > now;
    });
    const nextAssessment = [...activeAssessments].sort(
        (first, second) => parseAssessmentTime(first.startTime) - parseAssessmentTime(second.startTime),
    )[0];

    if (loading) {
        return (
            <div style={{ minHeight: '100vh', background: '#0a0503', color: '#ff7a00', display: 'flex', justifyContent: 'center', alignItems: 'center', fontFamily: "'Share Tech Mono', monospace", letterSpacing: '3px' }}>
                LOADING COMMAND CENTER...
            </div>
        );
    }

    return (
        <>
            <style>{`
                * { box-sizing: border-box; margin: 0; padding: 0; }
                body { background: #0a0503; margin: 0; color: #fff; font-family: 'Inter', sans-serif; }
                .comp-page { min-height: 100vh; position: relative; overflow-x: hidden; }
                .comp-canvas { position: fixed; inset: 0; width: 100vw; height: 100vh; pointer-events: none; z-index: 0; }
                .comp-bg-gradient { position: fixed; inset: 0; background: radial-gradient(ellipse at top left, rgba(255, 122, 0, 0.08) 0%, transparent 60%); z-index: 1; pointer-events: none; }
                .comp-bg-glow1 { position: absolute; top: -10%; left: -10%; width: 50%; height: 50%; background: radial-gradient(circle, rgba(255,122,0,0.12) 0%, transparent 60%); filter: blur(80px); pointer-events: none; z-index: 1; }
                .comp-bg-glow2 { position: absolute; bottom: -10%; right: -10%; width: 50%; height: 50%; background: radial-gradient(circle, rgba(255,61,0,0.08) 0%, transparent 60%); filter: blur(80px); pointer-events: none; z-index: 1; }
                .content { position: relative; z-index: 10; max-width: 1200px; margin: 60px auto; padding: 0 40px; display: flex; flex-direction: column; gap: 40px; }
                .glass-panel {
                    background: rgba(15, 8, 3, 0.5); backdrop-filter: blur(24px);
                    border: 1px solid rgba(255, 122, 0, 0.2); border-radius: 28px; padding: 48px;
                    box-shadow: 0 30px 60px rgba(0,0,0,0.6), inset 0 0 0 1px rgba(255,255,255,0.05);
                    position: relative; overflow: hidden;
                }
                .glass-panel::before {
                    content: ''; position: absolute; top: 0; left: 0; right: 0; height: 1px;
                    background: linear-gradient(90deg, transparent, rgba(255,122,0,0.5), transparent);
                }
                .stat-row { display: flex; gap: 28px; flex-wrap: wrap; padding-bottom: 30px; border-bottom: 1px solid rgba(255,255,255,0.05); }
                .stat-label { color: #888; font-size: 0.78rem; text-transform: uppercase; letter-spacing: 1px; font-weight: 700; margin-bottom: 6px; }
                .stat-value { color: #ff7a00; font-size: 2.3rem; font-weight: 800; font-family: 'Outfit', sans-serif; line-height: 1; }
                .stat-note { color: #71717a; font-size: 0.8rem; margin-top: 7px; }
                .action-grid { display: grid; grid-template-columns: repeat(3, 1fr); gap: 28px; margin-top: 36px; }
                .action-card {
                    background: rgba(255,255,255,0.02); border: 1px solid rgba(255,255,255,0.08);
                    border-radius: 24px; padding: 32px; cursor: pointer;
                    transition: all 0.35s cubic-bezier(0.175, 0.885, 0.32, 1.275);
                    position: relative; overflow: hidden;
                }
                .action-card::before {
                    content: ''; position: absolute; inset: 0;
                    background: radial-gradient(circle at top left, rgba(255,122,0,0.1), transparent 70%);
                    opacity: 0; transition: opacity 0.4s ease; pointer-events: none;
                }
                .action-card:hover {
                    transform: translateY(-8px) scale(1.02);
                    border-color: rgba(255,122,0,0.4);
                    background: rgba(255,122,0,0.02);
                    box-shadow: 0 20px 40px rgba(255,122,0,0.15), 0 0 20px rgba(255,122,0,0.1);
                }
                .action-card:hover::before { opacity: 1; }
                .action-icon {
                    width: 54px; height: 54px; border-radius: 14px;
                    display: flex; align-items: center; justify-content: center;
                    background: rgba(255,122,0,0.1); border: 1px solid rgba(255,122,0,0.3);
                    color: #ff7a00; font-size: 1.5rem; margin-bottom: 20px;
                    transition: transform 0.4s ease;
                }
                .action-card:hover .action-icon { transform: scale(1.08) rotate(4deg); }
                .action-title { font-family: 'Outfit', sans-serif; font-size: 1.45rem; font-weight: 700; color: #fff; margin-bottom: 10px; }
                .action-desc { color: #aaa; font-size: 0.92rem; line-height: 1.6; }
                .dash-error {
                    padding: 18px; border-radius: 12px; margin-bottom: 26px;
                    background: rgba(239,68,68,0.08); border: 1px solid rgba(239,68,68,0.3);
                    color: #fca5a5; font-size: 0.9rem;
                }
                @media (max-width: 900px) {
                    .content { padding: 0 20px; margin: 36px auto; }
                    .glass-panel { padding: 30px 24px; }
                    .action-grid { grid-template-columns: 1fr; }
                }
            `}</style>

            <div className="comp-page">
                <canvas ref={canvasRef} className="comp-canvas" />
                <div className="comp-bg-gradient" />
                <div className="comp-bg-glow1" />
                <div className="comp-bg-glow2" />

                <CompanyNav />

                <div className="content">
                    <div className="glass-panel">
                        <h1 style={{ fontFamily: 'Outfit', fontSize: '2.8rem', fontWeight: 800, marginBottom: '12px', background: 'linear-gradient(135deg, #fff 0%, #ffb84d 100%)', WebkitBackgroundClip: 'text', WebkitTextFillColor: 'transparent' }}>
                            Company Command Center
                        </h1>
                        <p style={{ color: '#888', fontSize: '1rem', marginBottom: '36px' }}>
                            Manage your private question bank, schedule tournaments, and review candidate performance.
                        </p>

                        {error && <div className="dash-error">{error}</div>}

                        <div className="stat-row">
                            <div>
                                <div className="stat-label">Private problems</div>
                                <div className="stat-value">{problems.length}</div>
                            </div>
                            <div>
                                <div className="stat-label">Assessments created</div>
                                <div className="stat-value">{assessments.length}</div>
                            </div>
                            <div>
                                <div className="stat-label">Active assessments</div>
                                <div className="stat-value">{activeAssessments.length}</div>
                            </div>
                            <div style={{ flex: 1, minWidth: '220px' }}>
                                <div className="stat-label">Next assessment</div>
                                <div style={{ color: '#e4e4e7', fontSize: '0.9rem', marginTop: '8px', fontFamily: "'Share Tech Mono', monospace" }}>
                                    {nextAssessment
                                        ? `${nextAssessment.title} · ${formatAssessmentDateTime(nextAssessment.startTime)}`
                                        : 'Nothing scheduled'}
                                </div>
                            </div>
                        </div>

                        <div className="action-grid">
                            <div className="action-card" onClick={() => navigate('/company/create-assessment')}>
                                <div className="action-icon">
                                    <i className="bi bi-trophy-fill" aria-hidden="true" />
                                </div>
                                <div className="action-title">Host Tournament</div>
                                <div className="action-desc">
                                    Generate a secure tournament, attach problems, and share the invite code with candidates.
                                </div>
                            </div>
                            <div className="action-card" onClick={() => navigate('/company/question-bank')}>
                                <div className="action-icon">
                                    <i className="bi bi-shield-lock-fill" aria-hidden="true" />
                                </div>
                                <div className="action-title">Question Bank</div>
                                <div className="action-desc">
                                    Create and manage private coding problems with hidden test cases.
                                </div>
                            </div>
                            <div className="action-card" onClick={() => navigate('/company/selection')}>
                                <div className="action-icon">
                                    <i className="bi bi-graph-up-arrow" aria-hidden="true" />
                                </div>
                                <div className="action-title">Selection Process</div>
                                <div className="action-desc">
                                    Review candidate scores, timing, and session status to shortlist engineers.
                                </div>
                            </div>
                        </div>
                    </div>
                </div>
            </div>
        </>
    );
}
