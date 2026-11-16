/**
 * Assessment timestamps are stored as naive LocalDateTime values in IST
 * (Asia/Kolkata). Browsers parse naive ISO strings in their own timezone, so
 * always route assessment timestamps through this helper before formatting or
 * comparing them.
 */
export const ASSESSMENT_TIME_ZONE = 'Asia/Kolkata';

const NAIVE_LOCAL_DATE_TIME = /^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}(:\d{2}(\.\d+)?)?$/;

export function parseAssessmentTime(value) {
  if (!value) return null;
  if (value instanceof Date) return value;
  if (typeof value !== 'string') return new Date(value);
  if (NAIVE_LOCAL_DATE_TIME.test(value)) {
    return new Date(`${value}+05:30`);
  }
  return new Date(value);
}

export function formatAssessmentDateTime(value) {
  const date = parseAssessmentTime(value);
  if (!date || Number.isNaN(date.getTime())) return '—';
  const formatted = new Intl.DateTimeFormat('en-IN', {
    day: '2-digit',
    month: 'short',
    year: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
    hour12: true,
    timeZone: ASSESSMENT_TIME_ZONE,
  }).format(date);
  return `${formatted} IST`;
}

export function formatAssessmentDate(value) {
  const date = parseAssessmentTime(value);
  if (!date || Number.isNaN(date.getTime())) return '—';
  return new Intl.DateTimeFormat('en-IN', {
    day: '2-digit',
    month: 'short',
    year: 'numeric',
    timeZone: ASSESSMENT_TIME_ZONE,
  }).format(date);
}

export function formatDurationSeconds(value) {
  const numeric = Number(value);
  const total = Number.isFinite(numeric) ? Math.max(0, Math.floor(numeric)) : 0;
  const hours = Math.floor(total / 3600);
  const minutes = Math.floor((total % 3600) / 60);
  const seconds = total % 60;
  if (hours > 0) return `${hours}h ${minutes}m`;
  if (minutes > 0) return `${minutes}m ${seconds}s`;
  return `${seconds}s`;
}

export function shortCandidateId(candidateId) {
  if (!candidateId) return 'Unknown candidate';
  return candidateId.length > 8 ? `${candidateId.slice(0, 8)}...` : candidateId;
}
