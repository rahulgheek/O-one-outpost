import React, { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { candidateApi } from '../services/candidateApi';
import EngineerNav from './EngineerNav';

export default function AssessmentDirectory({ currentUser }) {
    const [tournaments, setTournaments] = useState([]);
    const [isLoading, setIsLoading] = useState(true);
    const navigate = useNavigate();

    useEffect(() => {
        const fetchDirectory = async () => {
            try {
                // Now we use the clean candidateApi method
                const data = await candidateApi.getAssessmentDirectory(0, 20);
                
                // Spring Data JPA returns the array inside a "content" field when paginating
                setTournaments(data.content || data); 
            } catch (error) {
                console.error("Failed to load directory:", error);
            } finally {
                setIsLoading(false);
            }
        };
        fetchDirectory();
    }, []);

    const handleLogout = () => {
        localStorage.removeItem('algo_arena_token');
        localStorage.removeItem('algo_arena_user');
        localStorage.removeItem('algo_arena_role');
        navigate('/login');
    };

    if (isLoading) {
        return (
            <div className="dir-loading">
                <div className="spinner"></div>
                <div className="loading-text">LOADING DIRECTORY...</div>
            </div>
        );
    }

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
                    padding: 60px 40px;
                    position: relative;
                    z-index: 10;
                }
                .dir-header {
                    margin-bottom: 50px;
                    text-align: center;
                }
                .dir-title {
                    font-family: 'Outfit', sans-serif;
                    font-size: 3rem;
                    font-weight: 800;
                    letter-spacing: -1px;
                    margin-bottom: 12px;
                }
                .dir-subtitle {
                    color: #a1a1aa;
                    font-size: 1.1rem;
                    max-width: 600px;
                    margin: 0 auto;
                }
                .dir-grid {
                    display: grid;
                    grid-template-columns: repeat(auto-fill, minmax(350px, 1fr));
                    gap: 30px;
                }
                @keyframes cardHover {
                    0% { transform: translateY(0); box-shadow: 0 10px 30px rgba(0,0,0,0.5); }
                    100% { transform: translateY(-8px); box-shadow: 0 20px 40px rgba(255,122,0,0.15); }
                }
                .dir-card {
                    background: linear-gradient(135deg, rgba(20,10,5,0.9), rgba(0,0,0,0.95));
                    border: 1px solid rgba(255, 122, 0, 0.15);
                    border-radius: 20px;
                    padding: 30px;
                    display: flex;
                    flex-direction: column;
                    justify-content: space-between;
                    transition: all 0.4s cubic-bezier(0.175, 0.885, 0.32, 1.275);
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
                    transform: translateY(-8px);
                    border-color: rgba(255, 122, 0, 0.4);
                    box-shadow: 0 20px 40px rgba(255, 122, 0, 0.15), inset 0 0 20px rgba(255,122,0,0.05);
                }
                .card-header {
                    display: flex;
                    justify-content: space-between;
                    align-items: flex-start;
                    margin-bottom: 20px;
                }
                .card-title {
                    font-size: 1.4rem;
                    font-weight: 800;
                    color: #fff;
                    font-family: 'Outfit', sans-serif;
                    letter-spacing: -0.5px;
                }
                .card-badge {
                    background: rgba(255, 122, 0, 0.1);
                    color: #ff7a00;
                    border: 1px solid rgba(255, 122, 0, 0.3);
                    padding: 6px 12px;
                    border-radius: 20px;
                    font-size: 0.75rem;
                    font-weight: bold;
                    font-family: 'Share Tech Mono', monospace;
                    box-shadow: 0 0 10px rgba(255, 122, 0, 0.2);
                }
                .card-desc {
                    color: #a1a1aa;
                    font-size: 0.95rem;
                    line-height: 1.6;
                    margin-bottom: 30px;
                    display: -webkit-box;
                    -webkit-line-clamp: 3;
                    -webkit-box-orient: vertical;
                    overflow: hidden;
                }
                .card-times {
                    font-size: 0.85rem;
                    font-family: 'Share Tech Mono', monospace;
                    color: #ff7a00;
                    margin-bottom: 30px;
                    background: rgba(255,122,0,0.03);
                    padding: 15px;
                    border-radius: 12px;
                    border: 1px dashed rgba(255,122,0,0.1);
                }
                .card-times span {
                    color: #fff;
                    margin-left: 8px;
                }
                .card-times div {
                    margin-bottom: 8px;
                }
                .card-times div:last-child {
                    margin-bottom: 0;
                }
                .btn-register {
                    width: 100%;
                    background: linear-gradient(45deg, #ff3d00, #ff7a00);
                    border: none;
                    color: #fff;
                    padding: 16px;
                    border-radius: 12px;
                    font-weight: 700;
                    font-family: 'Inter', sans-serif;
                    letter-spacing: 1px;
                    cursor: pointer;
                    transition: all 0.3s;
                    text-transform: uppercase;
                    font-size: 0.95rem;
                    box-shadow: 0 8px 20px rgba(255, 122, 0, 0.25);
                }
                .btn-register:hover {
                    transform: translateY(-2px);
                    box-shadow: 0 12px 25px rgba(255, 122, 0, 0.4);
                }
                .dir-loading {
                    min-height: 100vh;
                    background: #0a0503;
                    display: flex;
                    flex-direction: column;
                    align-items: center;
                    justify-content: center;
                }
                .spinner {
                    width: 50px;
                    height: 50px;
                    border: 4px solid rgba(255, 122, 0, 0.1);
                    border-top-color: #ff7a00;
                    border-radius: 50%;
                    animation: spin 1s cubic-bezier(0.5, 0.1, 0.4, 0.9) infinite;
                    margin-bottom: 20px;
                    box-shadow: 0 0 20px rgba(255, 122, 0, 0.2);
                }
                .loading-text {
                    color: #ff7a00;
                    font-family: 'Share Tech Mono', monospace;
                    letter-spacing: 4px;
                    font-size: 1.1rem;
                }
                .dir-empty {
                    text-align: center;
                    color: #a1a1aa;
                    font-family: 'Share Tech Mono', monospace;
                    padding: 60px;
                    background: rgba(255,255,255,0.02);
                    border: 1px dashed rgba(255,255,255,0.1);
                    border-radius: 16px;
                    letter-spacing: 2px;
                }
                @keyframes spin {
                    to { transform: rotate(360deg); }
                }
            `}</style>
            
            {/* Top Navbar */}
            <EngineerNav currentUser={currentUser} />

            <div className="dir-container">
                <header className="dir-header">
                    <h1 className="dir-title">Public <span style={{ color: '#ff7a00' }}>Tournaments</span></h1>
                    <p className="dir-subtitle">Browse active company assessments and register for your spot in the arena.</p>
                </header>

                {tournaments.length === 0 ? (
                    <div className="dir-empty">NO ACTIVE TOURNAMENTS AVAILABLE</div>
                ) : (
                    <div className="dir-grid">
                        {tournaments.map(t => (
                            <div key={t.id} className="dir-card">
                                <div>
                                    <div className="card-header">
                                        <h2 className="card-title">{t.title}</h2>
                                        <span className="card-badge">
                                            {t.durationMinutes} MINS
                                        </span>
                                    </div>
                                    <p className="card-desc">{t.description}</p>
                                    
                                    <div className="card-times">
                                        <div>STARTS: <span>{new Date(t.startTime).toLocaleString()}</span></div>
                                        <div>ENDS: <span>{new Date(t.endTime).toLocaleString()}</span></div>
                                    </div>
                                </div>

                                <button 
                                    onClick={() => navigate(`/candidate/register/${t.id}`)}
                                    className="btn-register"
                                >
                                    REGISTER NOW
                                </button>
                            </div>
                        ))}
                    </div>
                )}
            </div>
        </div>
    );
}