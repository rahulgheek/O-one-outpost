import React, { useState } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { candidateApi } from '../services/candidateApi';
import { getApiErrorMessage } from '../utils/errors';
import EngineerNav from './EngineerNav';

export default function TournamentRegistration({ currentUser }) {
    const { assessmentId } = useParams();
    const navigate = useNavigate();
    const [status, setStatus] = useState('IDLE');
    const [errorMessage, setErrorMessage] = useState('');

    // State mapped directly to your Spring Boot DTO
    const [formData, setFormData] = useState({
        fullName: '', phoneNumber: '', occupation: '', address: '', cityCountry: '',
        resumeUrl: '', portfolioUrl: '', githubId: '', leetcodeId: '', profilePictureUrl: '',
        college: '', degree: '', branch: '', currentYear: '', graduationYear: '',
        cgpa: '', tenthPercentage: '', twelfthPercentage: '',
        contestRating: '', problemsSolved: '', preferredContestTime: '', availability: '',
        webcamAvailable: false, internetStabilityConfirmed: false, preferredLanguage: 'Java',
        skills: '', areasOfInterest: '' // We will convert these to arrays on submit
    });

    const handleChange = (e) => {
        const { name, value, type, checked } = e.target;
        setFormData(prev => ({
            ...prev,
            [name]: type === 'checkbox' ? checked : value
        }));
    };

    const handleSubmit = async (e) => {
        e.preventDefault();
        setStatus('LOADING');
        setErrorMessage('');

        try {
            // Format the comma-separated strings into arrays for MongoDB
            const payload = {
                ...formData,
                cgpa: parseFloat(formData.cgpa) || null,
                tenthPercentage: parseFloat(formData.tenthPercentage) || null,
                twelfthPercentage: parseFloat(formData.twelfthPercentage) || null,
                contestRating: parseInt(formData.contestRating) || null,
                problemsSolved: parseInt(formData.problemsSolved) || null,
                skills: formData.skills.split(',').map(s => s.trim()).filter(Boolean),
                areasOfInterest: formData.areasOfInterest.split(',').map(s => s.trim()).filter(Boolean)
            };

            await candidateApi.registerForTournament(assessmentId, payload);
            navigate('/candidate/dashboard');
        } catch (error) {
            setErrorMessage(getApiErrorMessage(error, 'Registration failed.'));
            setStatus('ERROR');
        }
    };

    return (
        <div style={{ backgroundColor: '#0a0503', color: '#fff', minHeight: '100vh', fontFamily: "'Inter', sans-serif" }}>
            <EngineerNav currentUser={currentUser} />
            
            <style>
                {`
                    .reg-container {
                        max-width: 900px;
                        margin: 40px auto;
                        padding: 0 20px;
                        position: relative;
                        z-index: 10;
                    }
                    .glass-form {
                        background: linear-gradient(135deg, rgba(20,10,5,0.8), rgba(0,0,0,0.9));
                        border: 1px solid rgba(255,122,0,0.15);
                        border-radius: 20px;
                        padding: 40px;
                        box-shadow: 0 20px 50px rgba(0,0,0,0.5), inset 0 0 20px rgba(255,122,0,0.02);
                        backdrop-filter: blur(10px);
                    }
                    .reg-title {
                        font-family: 'Outfit', sans-serif;
                        font-size: 2.5rem;
                        font-weight: 800;
                        letter-spacing: -1px;
                        margin-bottom: 8px;
                    }
                    .reg-subtitle {
                        color: #a1a1aa;
                        font-size: 0.95rem;
                        margin-bottom: 40px;
                        border-bottom: 1px solid rgba(255,255,255,0.05);
                        padding-bottom: 20px;
                    }
                    .section-title {
                        font-family: 'Share Tech Mono', monospace;
                        color: #ff7a00;
                        font-size: 1.1rem;
                        letter-spacing: 2px;
                        text-transform: uppercase;
                        margin-bottom: 20px;
                        margin-top: 30px;
                        display: flex;
                        align-items: center;
                        gap: 10px;
                    }
                    .section-title::before {
                        content: '';
                        display: inline-block;
                        width: 8px;
                        height: 8px;
                        background: #ff7a00;
                        border-radius: 50%;
                        box-shadow: 0 0 10px #ff7a00;
                    }
                    .input-group {
                        display: flex;
                        flex-direction: column;
                        gap: 8px;
                        margin-bottom: 16px;
                    }
                    .input-group input, .input-group select {
                        background: rgba(255,255,255,0.03);
                        border: 1px solid rgba(255,255,255,0.1);
                        padding: 14px 16px;
                        border-radius: 10px;
                        color: #fff;
                        font-family: 'Inter', sans-serif;
                        font-size: 0.95rem;
                        transition: all 0.3s;
                        outline: none;
                    }
                    .input-group input:focus, .input-group select:focus {
                        border-color: #ff7a00;
                        background: rgba(255,122,0,0.02);
                        box-shadow: 0 0 0 3px rgba(255,122,0,0.1);
                    }
                    .input-group input::placeholder {
                        color: #52525b;
                    }
                    .grid-2 { display: grid; grid-template-columns: 1fr 1fr; gap: 20px; }
                    .grid-3 { display: grid; grid-template-columns: 1fr 1fr 1fr; gap: 20px; }
                    
                    .checkbox-group {
                        background: rgba(255,255,255,0.02);
                        border: 1px dashed rgba(255,255,255,0.1);
                        padding: 20px;
                        border-radius: 12px;
                        margin-top: 40px;
                        display: flex;
                        flex-direction: column;
                        gap: 12px;
                    }
                    .checkbox-label {
                        display: flex;
                        align-items: center;
                        gap: 12px;
                        cursor: pointer;
                        color: #d4d4d8;
                        font-size: 0.9rem;
                    }
                    .checkbox-label input[type="checkbox"] {
                        appearance: none;
                        width: 20px;
                        height: 20px;
                        border: 1px solid #ff7a00;
                        border-radius: 4px;
                        background: rgba(255,122,0,0.1);
                        cursor: pointer;
                        position: relative;
                        transition: all 0.2s;
                    }
                    .checkbox-label input[type="checkbox"]:checked {
                        background: #ff7a00;
                    }
                    .checkbox-label input[type="checkbox"]:checked::after {
                        content: '';
                        position: absolute;
                        top: 2px;
                        left: 6px;
                        width: 5px;
                        height: 10px;
                        border: solid #fff;
                        border-width: 0 2px 2px 0;
                        transform: rotate(45deg);
                    }

                    .btn-submit {
                        background: linear-gradient(45deg, #ff3d00, #ff7a00);
                        color: #fff;
                        border: none;
                        padding: 16px;
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
                        margin-top: 30px;
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
                        border-left: 4px solid #ef4444;
                        padding: 16px;
                        color: #fca5a5;
                        border-radius: 4px;
                        margin-bottom: 24px;
                        font-size: 0.95rem;
                    }
                    @media (max-width: 768px) {
                        .grid-2, .grid-3 { grid-template-columns: 1fr; }
                    }
                `}
            </style>

            <div className="reg-container">
                <div className="glass-form">
                    <h1 className="reg-title">Tournament <span style={{ color: '#ff7a00' }}>Registration</span></h1>
                    <p className="reg-subtitle">Establish your identity and configure your combat parameters to enter the arena.</p>

                    {status === 'ERROR' && (
                        <div className="error-box">
                            {errorMessage}
                        </div>
                    )}

                    <form onSubmit={handleSubmit}>
                        {/* Basic Info */}
                        <h2 className="section-title">Identity Parameters</h2>
                        <div className="grid-2">
                            <div className="input-group">
                                <input name="fullName" placeholder="Full Name" required onChange={handleChange} />
                            </div>
                            <div className="input-group">
                                <input name="phoneNumber" placeholder="Secure Comms (Phone)" required onChange={handleChange} />
                            </div>
                            <div className="input-group">
                                <input name="cityCountry" placeholder="Location (City, Country)" required onChange={handleChange} />
                            </div>
                            <div className="input-group">
                                <input name="occupation" placeholder="Current Role (e.g. Engineer, Student)" onChange={handleChange} />
                            </div>
                        </div>

                        {/* Profiles & Links */}
                        <h2 className="section-title">External Uplinks</h2>
                        <div className="grid-2">
                            <div className="input-group">
                                <input name="resumeUrl" placeholder="Resume URL (Drive, Dropbox)" required onChange={handleChange} />
                            </div>
                            <div className="input-group">
                                <input name="portfolioUrl" placeholder="Portfolio / Website URL" onChange={handleChange} />
                            </div>
                            <div className="input-group">
                                <input name="githubId" placeholder="GitHub Designation" onChange={handleChange} />
                            </div>
                            <div className="input-group">
                                <input name="leetcodeId" placeholder="LeetCode Alias" onChange={handleChange} />
                            </div>
                        </div>

                        {/* Academics */}
                        <h2 className="section-title">Academic Record</h2>
                        <div className="grid-3">
                            <div className="input-group" style={{ gridColumn: 'span 2' }}>
                                <input name="college" placeholder="Institution / University" required onChange={handleChange} />
                            </div>
                            <div className="input-group">
                                <input name="degree" placeholder="Degree (e.g. B.Tech)" required onChange={handleChange} />
                            </div>
                            <div className="input-group">
                                <input name="graduationYear" placeholder="Graduation Year" required onChange={handleChange} />
                            </div>
                            <div className="input-group">
                                <input name="cgpa" type="number" step="0.01" placeholder="CGPA" required onChange={handleChange} />
                            </div>
                            <div className="input-group">
                                <input name="twelfthPercentage" type="number" step="0.1" placeholder="12th Grade %" onChange={handleChange} />
                            </div>
                        </div>

                        {/* DSA & Tech */}
                        <h2 className="section-title">Combat Loadout</h2>
                        <div className="grid-2">
                            <div className="input-group">
                                <input name="preferredLanguage" placeholder="Primary Weapon (Java, C++, etc.)" required onChange={handleChange} />
                            </div>
                            <div className="input-group">
                                <input name="problemsSolved" type="number" placeholder="Total Problems Solved" onChange={handleChange} />
                            </div>
                            <div className="input-group" style={{ gridColumn: '1 / -1' }}>
                                <input name="skills" placeholder="Skill Matrix (comma separated)" required onChange={handleChange} />
                            </div>
                        </div>

                        {/* Proctoring Agreements */}
                        <div className="checkbox-group">
                            <label className="checkbox-label">
                                <input type="checkbox" name="webcamAvailable" onChange={handleChange} required />
                                <span>I authorize webcam access for automated proctoring surveillance.</span>
                            </label>
                            <label className="checkbox-label">
                                <input type="checkbox" name="internetStabilityConfirmed" onChange={handleChange} required />
                                <span>I confirm network stability for the duration of the engagement.</span>
                            </label>
                        </div>

                        <button 
                            type="submit" 
                            disabled={status === 'LOADING'}
                            className="btn-submit"
                        >
                            {status === 'LOADING' ? 'INITIALIZING...' : 'INITIALIZE REGISTRATION'}
                        </button>
                    </form>
                </div>
            </div>
        </div>
    );
}