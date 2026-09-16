export interface DocumentRecord {
  id: number;
  filename: string;
  uploadedAt: string;
}

export interface NoteRecord {
  id: number;
  title: string;
}

export interface RagSource {
  documentId: number;
  filename: string;
  chunkIndex: number;
  similarity: number;
}

export interface SemanticSearchResult {
  documentId: number;
  filename: string;
  chunkIndex: number;
  chunkText: string;
  similarity: number;
}

export interface ReindexResponse {
  chunksReindexed: number;
}

export interface RagResponse {
  answer: string;
  sources: RagSource[];
  webSources: WebSearchResult[];
  origin: RagOrigin;
}

export type RagOrigin = 'DOCUMENTS' | 'WEB' | 'DOCUMENTS_AND_WEB' | 'INSUFFICIENT_EVIDENCE';

export interface WebSearchResult {
  title: string;
  url: string;
  snippet: string;
  publisher: string;
}

export interface WebSearchResponse {
  query: string;
  results: WebSearchResult[];
}

export interface LoginResponse {
  token: string;
}

export interface RegisterResponse {
  message: string;
}

export interface ApiErrorBody {
  timestamp?: string;
  status?: number;
  error?: string;
  message?: string;
  path?: string;
  code?: string | null;
}
