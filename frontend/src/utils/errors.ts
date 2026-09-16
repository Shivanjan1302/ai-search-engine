import { ApiError } from '../services/api';

export function readableError(error: unknown, fallback = 'Something went wrong.'): string {
  if (!(error instanceof ApiError)) return fallback;
  if (error.status === 400) return error.message;
  if (error.status === 401) return error.message || 'Your session has expired. Sign in again.';
  if (error.status === 409) return error.message || 'That email is already registered.';
  if (error.status === 413) return 'That file is larger than the 10 MB upload limit.';
  if (error.status === 415) return 'Only PDF and TXT files are supported.';
  if (error.status === 422) return error.message || 'Dronzer could not process that document.';
  if (error.status === 502) {
    if (error.code === 'WEB_SEARCH_UNAVAILABLE') return 'Web search is unavailable right now. Try again in a moment.';
    if (error.code === 'GEMINI_UNAVAILABLE') return 'Gemini is unavailable right now. Try again in a moment.';
    return 'An upstream service is unavailable right now. Try again in a moment.';
  }
  if (error.status === 404) return 'That item could not be found.';
  return error.message;
}
