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
  documentId: number | null;
  filename: string | null;
  chunkIndex: number | null;
  similarity: number;
  keywordScore: number;
  hybridScore: number;
}

export interface SemanticSearchResult {
  documentId: number | null;
  filename: string | null;
  chunkIndex: number | null;
  chunkText: string | null;
  similarity: number | null;
  keywordScore: number;
  hybridScore: number;
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

export interface ConversationTurn {
  role: 'user' | 'assistant';
  content: string;
  sourceFilenames?: string[];
}

export type RagOrigin = 'DOCUMENTS' | 'WEB' | 'MODEL_KNOWLEDGE' | 'MIXED' | 'INSUFFICIENT_EVIDENCE';

export interface WebSearchResult {
  title: string | null;
  url: string | null;
  snippet: string | null;
  publisher: string | null;
  domain: string | null;
  path: string | null;
  breadcrumb: string | null;
  publishedDate: string | null;
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
