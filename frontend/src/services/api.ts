import type { ApiErrorBody, ConversationTurn, DocumentRecord, LoginResponse, NoteRecord, RagResponse, RegisterResponse, ReindexResponse, SemanticSearchResult, WebSearchResponse } from '../types/api';

const baseUrl = import.meta.env.DEV ? '' : (import.meta.env.VITE_API_BASE_URL ?? '');
const oauthBaseUrl = import.meta.env.DEV ? 'http://localhost:8080' : baseUrl;

export class ApiError extends Error {
  readonly status: number;
  readonly code?: string;

  constructor(status: number, message: string, code?: string) {
    super(message);
    this.name = 'ApiError';
    this.status = status;
    this.code = code;
  }
}

async function request<T>(path: string, init: RequestInit = {}, responseType: 'json' | 'text' = 'json'): Promise<T> {
  const token = localStorage.getItem('dronzer_token');
  const headers = new Headers(init.headers);
  if (!(init.body instanceof FormData)) headers.set('Content-Type', 'application/json');
  if (token) headers.set('Authorization', `Bearer ${token}`);

  let response: Response;
  try {
    response = await fetch(`${baseUrl}${path}`, { ...init, headers });
  } catch {
    throw new ApiError(0, 'Unable to reach Dronzer. Check that the API is running.');
  }

  if (response.status === 401) {
    localStorage.removeItem('dronzer_token');
    window.dispatchEvent(new Event('dronzer:unauthorized'));
  }
  if (!response.ok) {
    let body: ApiErrorBody = {};
    try { body = (await response.json()) as ApiErrorBody; } catch { /* empty error body */ }
    throw new ApiError(response.status, body.message || response.statusText || 'Something went wrong.', body.code ?? undefined);
  }
  if (response.status === 204) return undefined as T;
  return (responseType === 'text' ? await response.text() : await response.json()) as T;
}

export const api = {
  login: (email: string, password: string) => request<LoginResponse>('/auth/login', { method: 'POST', body: JSON.stringify({ email, password }) }),
  register: (email: string, password: string) => request<RegisterResponse>('/auth/register', { method: 'POST', body: JSON.stringify({ email, password }) }),
  documents: () => request<DocumentRecord[]>('/documents'),
  searchDocuments: (keyword: string) => request<DocumentRecord[]>(`/documents/search?keyword=${encodeURIComponent(keyword)}`),
  semanticSearch: (query: string, limit = 10) => request<SemanticSearchResult[]>(`/documents/semantic-search?query=${encodeURIComponent(query)}&limit=${limit}`),
  reindex: () => request<ReindexResponse>('/documents/reindex', { method: 'POST' }),
  upload: (file: File) => {
    const data = new FormData();
    data.append('file', file);
    return request<DocumentRecord>('/documents/upload', { method: 'POST', body: data });
  },
  notes: () => request<NoteRecord[]>('/notes'),
  createNote: (title: string) => request<NoteRecord>('/notes', { method: 'POST', body: JSON.stringify({ title }) }),
  updateNote: (id: number, title: string) => request<NoteRecord>(`/notes/${id}`, { method: 'PUT', body: JSON.stringify({ title }) }),
  deleteNote: (id: number) => request<void>(`/notes/${id}`, { method: 'DELETE' }),
  ask: (question: string, recentTurns: ConversationTurn[] = []) => request<RagResponse>('/rag/ask', { method: 'POST', body: JSON.stringify({ question, recentTurns }) }),
  webSearch: (query: string, limit = 10) => request<WebSearchResponse>(`/search/web?q=${encodeURIComponent(query)}&limit=${limit}`),
  oauthToken: async () => {
    const response = await fetch(`${oauthBaseUrl}/auth/oauth-token`, { credentials: 'include' });
    if (!response.ok) throw new ApiError(response.status, 'Google sign-in could not be completed.');
    return response.text();
  },
};
