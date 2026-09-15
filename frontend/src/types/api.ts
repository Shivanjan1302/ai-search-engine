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
}

export interface ApiErrorBody {
  timestamp?: string;
  status?: number;
  error?: string;
  message?: string;
  path?: string;
}
