import React, { useState } from 'react';
import { useNavigate, useLocation } from 'react-router-dom';
import { candidateApi } from '../services/candidateApi';
import { getApiErrorMessage } from '../utils/errors';

export default function CandidateEntry() {
    const navigate = useNavigate();
    const location = useLocation();
    const [testCode, setTestCode] = useState(location.state?.prefilledCode || '');
    const [status, setStatus] = useState('IDLE'); // IDLE, LOADING, ERROR
    const [errorMessage, setErrorMessage] = useState('');

    const handleJoin = async (e) => {
        e.preventDefault();
        
        // Basic frontend validation before hitting the server
        if (!testCode || testCode.length < 5) {
            setErrorMessage('Please enter a valid test code.');
            setStatus('ERROR');
            return;
        }

        setStatus('LOADING');
        setErrorMessage('');

        try {
            // Hit the Gateway!
            const assessment = await candidateApi.joinAssessment(testCode.toUpperCase());
            
            // If we get here, the Spring Boot backend verified the code AND the time limit.
            // Route them straight to the split-screen workspace.
            navigate(`/workspace/${assessment.id}`, { state: { assessment } });
            
        } catch (error) {
            setStatus('ERROR');
            // Prefer the server's specific business message (window closed, not registered, ...)
            setErrorMessage(
                getApiErrorMessage(error, 'Invalid test code or assessment unavailable.'),
            );
        }
    };

    return (
        <div style={{ backgroundColor: '#0a0503', color: '#fff', minHeight: '100vh', fontFamily: "'Inter', sans-serif", display: 'flex', alignItems: 'center', justifyContent: 'center', padding: '20px', position: 'relative', overflow: 'hidden' }}>
            <style>
                {`
                    @keyframes pulse-glow {
                        0% { opacity: 0.1; transform: scale(1); }
                        50% { opacity: 0.2; transform: scale(1.1); }
                        100% { opacity: 0.1; transform: scale(1); }
                    }
                    @keyframes spin {
                        to { transform: rotate(360deg); }
                    }
                    .glass-form {
                        background: linear-gradient(135deg, rgba(20,10,5,0.8), rgba(0,0,0,0.9));
                        border: 1px solid rgba(255,122,0,0.15);
                        border-radius: 20px;
                        padding: 50px 40px;
                        box-shadow: 0 20px 50px rgba(0,0,0,0.5), inset 0 0 20px rgba(255,122,0,0.02);
                        backdrop-filter: blur(10px);
                        width: 100%;
                        max-width: 480px;
                        position: relative;
                        z-index: 10;
                    }
                    .pulse-bg {
                        position: absolute;
                        border-radius: 50%;
                        filter: blur(80px);
                        z-index: 1;
                        animation: pulse-glow 6s ease-in-out infinite;
                    }
                    .entry-title {
                        font-family: 'Outfit', sans-serif;
                        font-size: 2.2rem;
                        font-weight: 800;
                        letter-spacing: -1px;
                        margin-bottom: 8px;
                        text-align: center;
                    }
                    .entry-subtitle {
                        color: #a1a1aa;
                        font-size: 0.95rem;
                        margin-bottom: 40px;
                        text-align: center;
                    }
                    .code-input {
                        width: 100%;
                        background: rgba(255,255,255,0.02);
                        border: 1px solid rgba(255,255,255,0.1);
                        padding: 20px;
                        border-radius: 12px;
                        color: #ff7a00;
                        font-family: 'Share Tech Mono', monospace;
                        font-size: 2rem;
                        text-align: center;
                        letter-spacing: 4px;
                        transition: all 0.3s;
                        outline: none;
                        margin-bottom: 24px;
                    }
                    .code-input:focus {
                        border-color: #ff7a00;
                        background: rgba(255,122,0,0.03);
                        box-shadow: 0 0 20px rgba(255,122,0,0.1);
                    }
                    .code-input::placeholder {
                        color: rgba(255,255,255,0.1);
                    }
                    .btn-submit {
                        background: linear-gradient(45deg, #ff3d00, #ff7a00);
                        color: #fff;
                        border: none;
                        padding: 18px;
                        border-radius: 12px;
                        font-size: 1.1rem;
                        font-weight: 700;
                        cursor: pointer;
                        transition: all 0.3s;
                        box-shadow: 0 10px 25px rgba(255, 122, 0, 0.3);
                        font-family: 'Inter', sans-serif;
                        text-transform: uppercase;
                        letter-spacing: 1px;
                        width: 100%;
                        display: flex;
                        justify-content: center;
                        align-items: center;
                        gap: 12px;
                    }
                    .btn-submit:hover:not(:disabled) {
                        transform: translateY(-2px);
                        box-shadow: 0 15px 35px rgba(255, 122, 0, 0.5);
                    }
                    .btn-submit:disabled {
                        opacity: 0.5;
                        cursor: not-allowed;
                        filter: grayscale(1);
                    }
                    .error-box {
                        background: rgba(239, 68, 68, 0.1);
                        border: 1px solid rgba(239, 68, 68, 0.3);
                        padding: 16px;
                        color: #fca5a5;
                        border-radius: 8px;
                        margin-bottom: 24px;
                        font-size: 0.9rem;
                        text-align: center;
                    }
                `}
            </style>

            <div className="pulse-bg" style={{ top: '20%', left: '30%', width: '300px', height: '300px', background: '#ff3d00' }} />
            <div className="pulse-bg" style={{ bottom: '20%', right: '30%', width: '400px', height: '400px', background: '#ff7a00', animationDelay: '2s' }} />

            <div className="glass-form">
                <div className="entry-title">
                    Enter <span style={{ color: '#ff7a00' }}>Arena</span>
                </div>
                <div className="entry-subtitle">
                    Input your secure tournament code to proceed.
                </div>

                <form onSubmit={handleJoin}>
                    <input
                        type="text"
                        value={testCode}
                        onChange={(e) => setTestCode(e.target.value.toUpperCase())}
                        placeholder="CODE"
                        className="code-input"
                        maxLength={8}
                        disabled={status === 'LOADING'}
                    />

                    {status === 'ERROR' && (
                        <div className="error-box">
                            {errorMessage}
                        </div>
                    )}

                    <button
                        type="submit"
                        disabled={status === 'LOADING' || !testCode}
                        className="btn-submit"
                    >
                        {status === 'LOADING' ? (
                            <>
                                <i
                                    className="bi bi-arrow-repeat"
                                    aria-hidden="true"
                                    style={{ display: 'inline-block', animation: 'spin 1s linear infinite' }}
                                />
                                AUTHENTICATING...
                            </>
                        ) : (
                            'JOIN TOURNAMENT'
                        )}
                    </button>
                </form>
            </div>
        </div>
    );
}