import { ApiError } from '../services/api';

export function readableError(error: unknown, fallback = 'Something went wrong.'): string {
  if (!(error instanceof ApiError)) return fallback;
  if (error.status === 413) return 'That file is larger than the 10 MB upload limit.';
  if (error.status === 415) return 'Only PDF and TXT files are supported.';
  if (error.status === 422) return 'Dronzer could not process that document.';
  if (error.status === 502) return 'Gemini is unavailable right now. Try again in a moment.';
  if (error.status === 404) return 'That item could not be found.';
  return error.message;
}
