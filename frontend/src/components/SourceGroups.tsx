import type { RagOrigin, RagResponse, RagSource, WebSearchResult } from '../types/api';

export type CitationSource =
  | { kind: 'document'; citationNumber: number; source: RagSource }
  | { kind: 'web'; citationNumber: number; source: WebSearchResult };

function applicableDocumentSources(response: RagResponse) {
  return response.origin === 'DOCUMENTS' || response.origin === 'MIXED' ? response.sources : [];
}

function applicableWebSources(response: RagResponse) {
  return response.origin === 'WEB' || response.origin === 'MIXED' ? response.webSources : [];
}

export function citationSources(response: RagResponse): CitationSource[] {
  if (response.origin === 'MODEL_KNOWLEDGE' || response.origin === 'INSUFFICIENT_EVIDENCE') return [];

  const documentSources = applicableDocumentSources(response);
  const webSources = applicableWebSources(response);
  return [
    ...documentSources.map((source, index) => ({ kind: 'document' as const, citationNumber: index + 1, source })),
    ...webSources.map((source, index) => ({
      kind: 'web' as const,
      citationNumber: documentSources.length + index + 1,
      source,
    })),
  ];
}

export function citationSourceCount(response: RagResponse) {
  return citationSources(response).length;
}

function safeWebUrl(value: string | null): string | null {
  if (!value) return null;
  try {
    const url = new URL(value);
    return url.protocol === 'http:' || url.protocol === 'https:' ? url.href : null;
  } catch {
    return null;
  }
}

function formatScore(value: number) {
  return Number.isFinite(value) ? value.toFixed(3) : '—';
}

function sourceId(messageId: string, citationNumber: number) {
  return `ask-source-${messageId}-${citationNumber}`;
}

interface SourceGroupsProps {
  response: RagResponse;
  messageId: string;
  activeCitation: number | null;
}

function DocumentSourceCard({ entry, messageId, active }: { entry: Extract<CitationSource, { kind: 'document' }>; messageId: string; active: boolean }) {
  const { source, citationNumber } = entry;
  return (
    <article className={`source-card document-source-card${active ? ' is-highlighted' : ''}`} id={sourceId(messageId, citationNumber)} tabIndex={-1} aria-label={`Source E${citationNumber}`}>
      <span className="source-index">E{citationNumber}</span>
      <div className="source-card-body">
        <div className="source-card-heading"><strong>{source.filename || 'Untitled document'}</strong><span className="source-type">Document</span></div>
        <dl className="source-metadata">
          <div><dt>Source type</dt><dd>Document chunk</dd></div>
          {source.chunkIndex !== null && <div><dt>Chunk</dt><dd>{source.chunkIndex}</dd></div>}
          <div><dt>Hybrid relevance</dt><dd>{formatScore(source.hybridScore)}</dd></div>
          <div><dt>Similarity</dt><dd>{formatScore(source.similarity)}</dd></div>
          <div><dt>Keyword relevance</dt><dd>{formatScore(source.keywordScore)}</dd></div>
        </dl>
      </div>
    </article>
  );
}

function WebSourceCard({ entry, messageId, active }: { entry: Extract<CitationSource, { kind: 'web' }>; messageId: string; active: boolean }) {
  const { source, citationNumber } = entry;
  const href = safeWebUrl(source.url);
  const title = source.title || source.publisher || source.domain || 'Web source';
  return (
    <article className={`source-card web-source-card${active ? ' is-highlighted' : ''}`} id={sourceId(messageId, citationNumber)} tabIndex={-1} aria-label={`Source E${citationNumber}`}>
      <span className="source-index">E{citationNumber}</span>
      <div className="source-card-body">
        <div className="source-card-heading">
          {href ? <a href={href} target="_blank" rel="noopener noreferrer"><strong>{title}</strong></a> : <strong>{title}</strong>}
          <span className="source-type">Web</span>
        </div>
        <dl className="source-metadata">
          {source.publisher && <div><dt>Publisher</dt><dd>{source.publisher}</dd></div>}
          {source.domain && <div><dt>Domain</dt><dd>{source.domain}</dd></div>}
          {source.breadcrumb && <div><dt>Path</dt><dd>{source.breadcrumb}</dd></div>}
          {!source.breadcrumb && source.path && <div><dt>Path</dt><dd>{source.path}</dd></div>}
          {source.publishedDate && <div><dt>Published</dt><dd>{source.publishedDate}</dd></div>}
        </dl>
        {source.url && <div className="source-url">{href ? <a href={href} target="_blank" rel="noopener noreferrer">{source.url}</a> : source.url}</div>}
        {source.snippet && <p className="source-excerpt">{source.snippet}</p>}
      </div>
    </article>
  );
}

export function SourceGroups({ response, messageId, activeCitation }: SourceGroupsProps) {
  const sources = citationSources(response);
  const documentSources = sources.filter((entry): entry is Extract<CitationSource, { kind: 'document' }> => entry.kind === 'document');
  const webSources = sources.filter((entry): entry is Extract<CitationSource, { kind: 'web' }> => entry.kind === 'web');
  if (!sources.length) return null;
  return (
    <section className="source-groups" aria-label="Sources">
      {!!documentSources.length && <section className="source-group"><div className="source-group-heading"><span>Documents</span><span>{documentSources.length}</span></div>{documentSources.map(entry => <DocumentSourceCard key={entry.citationNumber} entry={entry} messageId={messageId} active={activeCitation === entry.citationNumber} />)}</section>}
      {!!webSources.length && <section className="source-group"><div className="source-group-heading"><span>Web</span><span>{webSources.length}</span></div>{webSources.map(entry => <WebSourceCard key={entry.citationNumber} entry={entry} messageId={messageId} active={activeCitation === entry.citationNumber} />)}</section>}
    </section>
  );
}

export const originLabels: Record<RagOrigin, string> = {
  DOCUMENTS: 'Document grounded',
  WEB: 'Web grounded',
  MIXED: 'Documents + web',
  MODEL_KNOWLEDGE: 'Model knowledge',
  INSUFFICIENT_EVIDENCE: 'Insufficient evidence',
};

