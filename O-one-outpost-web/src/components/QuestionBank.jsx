import React, { useState } from 'react';
import axios from 'axios';
import CompanyNav from './CompanyNav';
import { getApiErrorMessage } from '../utils/errors';

export default function QuestionBank() {
    const getToken = () => localStorage.getItem('algo_arena_token');

    const defaultProblemState = {
        title: '', difficulty: 'MEDIUM', description: '', 
        timeLimitMs: 2000, memoryLimitKb: 256000, 
        methodName: '', returnType: 'int', parameterTypes: ['int[]', 'int'],
        startingCodeJava: 'class Solution {\n    public int solve(int[] nums, int target) {\n        return 0;\n    }\n}',
        testCases: [{ inputs: ['[1,2,3]', '3'], expectedOutput: '1', hidden: false }]
    };

    const [problem, setProblem] = useState(defaultProblemState);
    const [status, setStatus] = useState('');
    const [isLoading, setIsLoading] = useState(false);

    const handleAddTestCase = () => {
        setProblem({
            ...problem,
            testCases: [...problem.testCases, { inputs: [''], expectedOutput: '', hidden: false }]
        });
    };

    const handleTestCaseChange = (index, field, value) => {
        const updatedTestCases = [...problem.testCases];
        if (field === 'inputs') {
            updatedTestCases[index][field] = value.split('|').map(s => s.trim());
        } else {
            updatedTestCases[index][field] = value;
        }
        setProblem({ ...problem, testCases: updatedTestCases });
    };

    const handleProblemSubmit = async (e) => {
        e.preventDefault();
        setIsLoading(true);
        setStatus('Encrypting and securing private algorithm...');
        
        try {
            await axios.post(`${import.meta.env.VITE_API_GATEWAY_URL}/api/assessment/company/problems`, problem, { 
                headers: { Authorization: `Bearer ${getToken()}` } 
            });
            
            setStatus('SUCCESS: Private problem locked into your company vault!');
            setProblem(defaultProblemState);
        } catch (err) {
            setStatus(`ERROR: ${getApiErrorMessage(err, 'Could not save the problem.')}`);
        } finally {
            setIsLoading(false);
        }
    };

    const inputStyle = { padding: '14px', background: 'rgba(255,255,255,0.04)', border: '1px solid rgba(255,255,255,0.1)', color: '#fff', outline: 'none', fontFamily: "'Inter', sans-serif", borderRadius: '12px', transition: 'all 0.3s', width: '100%', fontSize: '0.95rem' };
    const labelStyle = { display: 'block', color: '#888', fontSize: '0.8rem', fontWeight: 'bold', marginBottom: '8px', letterSpacing: '2px', textTransform: 'uppercase' };

    return (
        <div style={{ minHeight: '100vh', background: '#0a0503', color: '#fff', fontFamily: "'Inter', sans-serif" }}>
            <CompanyNav />

            <div style={{ padding: '60px', maxWidth: '1000px', margin: '0 auto' }}>
                
                {/* HEADER */}
                <div style={{ marginBottom: '40px', borderBottom: '1px solid rgba(255,255,255,0.05)', paddingBottom: '24px', display: 'flex', alignItems: 'center', gap: '20px' }}>
                    <div style={{ width: '60px', height: '60px', borderRadius: '12px', background: 'rgba(255,122,0,0.1)', border: '1px solid rgba(255,122,0,0.3)', display: 'flex', alignItems: 'center', justifyContent: 'center', fontSize: '1.7rem', color: '#ff7a00' }}>
                        <i className="bi bi-shield-lock" aria-hidden="true" />
                    </div>
                    <div>
                        <div style={{ color: '#ff7a00', fontSize: '0.75rem', letterSpacing: '4px', marginBottom: '8px', fontWeight: 'bold' }}>RECRUITER COMMAND CENTER</div>
                        <h1 style={{ color: 'var(--text-primary)', fontSize: '2.5rem', fontWeight: '800', letterSpacing: '-1px' }}>
                            Private <span style={{ color: 'var(--accent-purple)' }}>Question Vault</span>
                        </h1>
                    </div>
                </div>

                <section className="glass-panel" style={{ padding: '40px', marginBottom: '60px' }}>
                    <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '32px' }}>
                        <h2 style={{ color: 'var(--text-secondary)', fontSize: '1.2rem', letterSpacing: '2px', margin: 0 }}>
                            [1] ALGORITHM INJECTION
                        </h2>
                    </div>
                    
                    <div style={{ height: status ? 'auto' : '0', opacity: status ? 1 : 0, overflow: 'hidden', transition: 'all 0.3s ease', marginBottom: status ? '24px' : '0' }}>
                        {status && (
                            <div style={{ padding: '16px', background: status.includes('ERROR') ? 'rgba(239,68,68,0.1)' : 'var(--accent-purple-dim)', borderLeft: `4px solid ${status.includes('ERROR') ? 'var(--accent-red)' : 'var(--accent-purple)'}`, color: status.includes('ERROR') ? '#fca5a5' : 'var(--accent-purple)', fontSize: '0.9rem', borderRadius: '4px' }}>
                                {status}
                            </div>
                        )}
                    </div>

                    <form onSubmit={handleProblemSubmit} style={{ display: 'grid', gap: '24px' }}>
                        
                        <div style={{ display: 'grid', gridTemplateColumns: '2fr 1fr', gap: '20px' }}>
                            <div>
                                <label style={labelStyle}>PROBLEM TITLE</label>
                                <input 
                                    placeholder="e.g., Reverse Matrix (Confidential)" 
                                    value={problem.title} 
                                    onChange={e => setProblem({...problem, title: e.target.value})} 
                                    style={inputStyle} 
                                    required 
                                    autoComplete="off"
                                />
                            </div>
                            <div>
                                <label style={labelStyle}>DIFFICULTY</label>
                                <select value={problem.difficulty} onChange={e => setProblem({...problem, difficulty: e.target.value})} style={inputStyle}>
                                    <option value="EASY">EASY</option>
                                    <option value="MEDIUM">MEDIUM</option>
                                    <option value="HARD">HARD</option>
                                </select>
                            </div>
                        </div>

                        <div>
                            <label style={labelStyle}>PROBLEM DESCRIPTION (Markdown supported)</label>
                            <textarea placeholder="Explain the algorithm requirements..." value={problem.description} onChange={e => setProblem({...problem, description: e.target.value})} style={{ ...inputStyle, minHeight: '140px' }} required />
                        </div>

                        <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '20px' }}>
                            <div>
                                <label style={labelStyle}>TIME LIMIT (ms)</label>
                                <input 
                                    type="number" 
                                    placeholder="e.g., 2000" 
                                    value={problem.timeLimitMs} 
                                    onChange={e => setProblem({...problem, timeLimitMs: parseInt(e.target.value) || 0})} 
                                    style={inputStyle} 
                                    required 
                                />
                            </div>
                            <div>
                                <label style={labelStyle}>MEMORY LIMIT (KB)</label>
                                <input 
                                    type="number" 
                                    placeholder="e.g., 256000" 
                                    value={problem.memoryLimitKb} 
                                    onChange={e => setProblem({...problem, memoryLimitKb: parseInt(e.target.value) || 0})} 
                                    style={inputStyle} 
                                    required 
                                />
                            </div>
                        </div>

                        <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr 2fr', gap: '20px' }}>
                            <div>
                                <label style={labelStyle}>METHOD NAME</label>
                                <input placeholder="e.g., solve" value={problem.methodName} onChange={e => setProblem({...problem, methodName: e.target.value})} style={inputStyle} required />
                            </div>
                            <div>
                                <label style={labelStyle}>RETURN TYPE</label>
                                <input placeholder="e.g., int[]" value={problem.returnType} onChange={e => setProblem({...problem, returnType: e.target.value})} style={inputStyle} required />
                            </div>
                            <div>
                                <label style={labelStyle}>PARAM TYPES</label>
                                <input placeholder="comma separated (e.g., int[],int)" value={problem.parameterTypes?.join(',')} onChange={e => setProblem({...problem, parameterTypes: e.target.value.split(',')})} style={inputStyle} required />
                            </div>
                        </div>

                        <div>
                            <label style={labelStyle}>STARTING JAVA CODE</label>
                            <textarea placeholder="class Solution {\n\n}" value={problem.startingCodeJava} onChange={e => setProblem({...problem, startingCodeJava: e.target.value})} style={{ ...inputStyle, minHeight: '180px', background: 'var(--bg-primary)', color: 'var(--text-secondary)' }} required />
                        </div>

                        {/* TEST CASES SECTION */}
                        <div style={{ padding: '24px', background: 'rgba(0,0,0,0.2)', border: '1px solid var(--border-glass)', borderRadius: '8px' }}>
                            <h3 style={{ marginBottom: '20px', color: 'var(--text-secondary)', fontSize: '0.9rem', letterSpacing: '2px' }}>
                                TEST CASES (USE PIPE '|' TO SEPARATE PARAMS)
                            </h3>
                            
                            {problem.testCases.map((tc, index) => (
                                <div key={index} style={{ display: 'grid', gridTemplateColumns: '2fr 2fr auto', gap: '16px', alignItems: 'center', marginBottom: '16px' }}>
                                    <input placeholder="Inputs (e.g. [1,2] | 3)" value={tc.inputs?.join(' | ')} onChange={e => handleTestCaseChange(index, 'inputs', e.target.value)} style={inputStyle} required />
                                    <input placeholder="Expected Output" value={tc.expectedOutput} onChange={e => handleTestCaseChange(index, 'expectedOutput', e.target.value)} style={inputStyle} required />
                                    <label style={{ display: 'flex', alignItems: 'center', gap: '8px', color: 'var(--text-secondary)', fontSize: '0.85rem', cursor: 'pointer', fontWeight: 'bold' }}>
                                        <input type="checkbox" checked={tc.hidden} onChange={e => handleTestCaseChange(index, 'hidden', e.target.checked)} style={{ width: '18px', height: '18px', accentColor: 'var(--accent-purple)', cursor: 'pointer' }} /> 
                                        HIDDEN
                                    </label>
                                </div>
                            ))}
                            
                            <button type="button" onClick={handleAddTestCase} style={{ background: 'transparent', color: 'var(--text-secondary)', border: '1px dashed var(--border-glass-strong)', padding: '10px 20px', borderRadius: '6px', cursor: 'pointer', fontSize: '0.8rem', letterSpacing: '2px', transition: 'all 0.2s', marginTop: '8px', width: '100%' }} onMouseEnter={e => {e.target.style.background = 'var(--bg-tertiary)'; e.target.style.color = 'var(--text-primary)'}} onMouseLeave={e => {e.target.style.background = 'transparent'; e.target.style.color = 'var(--text-secondary)'}}>
                                + ADD TEST CASE
                            </button>
                        </div>

                        <button type="submit" disabled={isLoading} style={{ padding: '18px', background: isLoading ? 'var(--bg-tertiary)' : 'var(--accent-purple-dim)', border: `1px solid ${isLoading ? 'var(--border-glass)' : 'var(--accent-purple)'}`, color: isLoading ? 'var(--text-tertiary)' : 'var(--accent-purple)', fontWeight: 'bold', letterSpacing: '2px', borderRadius: '6px', cursor: isLoading ? 'not-allowed' : 'pointer', transition: 'all 0.3s', fontSize: '1rem', marginTop: '10px' }} onMouseEnter={e => {if (!isLoading) {e.target.style.background = 'rgba(139,92,246,0.2)'; e.target.style.boxShadow = 'var(--glow-purple)'}}} onMouseLeave={e => {if (!isLoading) {e.target.style.background = 'var(--accent-purple-dim)'; e.target.style.boxShadow = 'none'}}}>
                            {isLoading ? 'ENCRYPTING...' : 'SAVE TO VAULT →'}
                        </button>
                    </form>
                </section>
            </div>
        </div>
    );
}