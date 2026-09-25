import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { citationSourceCount, citationSources, SourceGroups } from '../components/SourceGroups';
import type { RagResponse, WebSearchResult } from '../types/api';

function webSource(overrides: Partial<WebSearchResult> = {}): WebSearchResult {
  return {
    title: 'External result',
    url: 'https://external.test/news',
    snippet: 'An external fact.',
    publisher: 'external',
    domain: 'external.test',
    path: '/news',
    breadcrumb: 'external.test › news',
    publishedDate: '2026-09-24',
    ...overrides,
  };
}

function documentResponse(overrides: Partial<RagResponse> = {}): RagResponse {
  return {
    answer: 'Answer',
    sources: [
      { documentId: 10, filename: 'plans.pdf', chunkIndex: 1, similarity: 0.9, keywordScore: 0.2, hybridScore: 0.7 },
      { documentId: 11, filename: 'policy.pdf', chunkIndex: null, similarity: 0.8, keywordScore: 0.1, hybridScore: 0.6 },
    ],
    webSources: [],
    origin: 'DOCUMENTS',
    ...overrides,
  };
}

describe('citationSources', () => {
  it('numbers document sources first, then web sources, without gaps', () => {
    const sources = citationSources(documentResponse({
      origin: 'MIXED',
      webSources: [webSource()],
    }));

    expect(sources.map(entry => entry.citationNumber)).toEqual([1, 2, 3]);
    expect(sources.map(entry => entry.kind)).toEqual(['document', 'document', 'web']);
    expect(citationSourceCount(documentResponse({ origin: 'MIXED', webSources: [webSource()] }))).toBe(3);
  });

  it('returns no sources for model-knowledge and insufficient-evidence origins', () => {
    expect(citationSources(documentResponse({ origin: 'MODEL_KNOWLEDGE' }))).toEqual([]);
    expect(citationSources(documentResponse({ origin: 'INSUFFICIENT_EVIDENCE' }))).toEqual([]);
  });

  it('ignores document sources on a WEB-only answer and web sources on a DOCUMENTS-only answer', () => {

describe('SourceGroups rendering', () => {
  it('renders a Documents group and a Web group with counts', () => {
    render(
      <SourceGroups
        response={documentResponse({ origin: 'MIXED', webSources: [webSource()] })}
        messageId="m1"
        activeCitation={null}
      />,
    );

    expect(screen.getAllByText('Documents')).toHaveLength(1);
    expect(screen.getAllByText('Web')).toHaveLength(1);
    expect(screen.getByText('plans.pdf')).toBeInTheDocument();
    expect(screen.getByText('policy.pdf')).toBeInTheDocument();
    expect(screen.getByText('External result')).toBeInTheDocument();
  });

  it('renders only the Web group when the answer is web grounded', () => {
    render(
      <SourceGroups
        response={documentResponse({ origin: 'WEB', sources: [], webSources: [webSource()] })}
        messageId="m2"
        activeCitation={null}
      />,
    );

    expect(screen.queryByText('Documents')).toBeNull();
    expect(screen.getByText('Web')).toBeInTheDocument();
  });

  it('renders nothing at all when the answer has no citable sources', () => {
    const { container } = render(
      <SourceGroups
        response={documentResponse({ origin: 'INSUFFICIENT_EVIDENCE' })}
        messageId="m3"
        activeCitation={null}
      />,
    );

    expect(container).toBeEmptyDOMElement();
  });

  it('gives each source card a stable per-message anchor id', () => {
    render(
      <SourceGroups response={documentResponse()} messageId="m4" activeCitation={null} />,
    );


describe('SourceGroups unsafe URL handling', () => {
  it('renders an external link for a safe http(s) web source', () => {
    render(
      <SourceGroups
        response={documentResponse({ origin: 'WEB', sources: [], webSources: [webSource()] })}
        messageId="m7"
        activeCitation={null}
      />,
    );

    const link = screen.getByRole('link', { name: 'External result' });
    expect(link).toHaveAttribute('href', 'https://external.test/news');
    expect(link).toHaveAttribute('target', '_blank');
  });

  it('shows the title as plain text and does not link a javascript: web source', () => {
    render(
      <SourceGroups
        response={documentResponse({
          origin: 'WEB',
          sources: [],
          webSources: [webSource({ url: 'javascript:alert(1)' })],
        })}
        messageId="m8"
        activeCitation={null}
      />,
    );

    expect(screen.queryByRole('link')).toBeNull();
    expect(document.body.innerHTML).not.toContain('javascript:alert');
  });

  it('does not link a web source whose URL is null', () => {
    render(
      <SourceGroups
        response={documentResponse({
          origin: 'WEB',
          sources: [],
          webSources: [webSource({ url: null, title: 'No URL source' })],
        })}
        messageId="m9"
        activeCitation={null}
      />,
    );

    expect(screen.getByText('No URL source')).toBeInTheDocument();
    expect(screen.queryByRole('link')).toBeNull();
  });

  it('falls back to the publisher when a web source has no title', () => {
    render(
      <SourceGroups
        response={documentResponse({
          origin: 'WEB',
          sources: [],
          webSources: [webSource({ title: null, publisher: 'Fallback Publisher' })],
        })}
        messageId="m10"
        activeCitation={null}
      />,
    );

    expect(screen.getByText('Fallback Publisher')).toBeInTheDocument();
  });
});

    expect(document.getElementById('ask-source-m4-1')).not.toBeNull();
    expect(document.getElementById('ask-source-m4-2')).not.toBeNull();
  });

  it('marks only the active citation card as highlighted', () => {
    render(
      <SourceGroups response={documentResponse()} messageId="m5" activeCitation={2} />,
    );

    expect(document.getElementById('ask-source-m5-1')?.className).not.toContain('is-highlighted');
    expect(document.getElementById('ask-source-m5-2')?.className).toContain('is-highlighted');
  });

  it('labels a document with no chunk index without inventing one', () => {
    render(
      <SourceGroups response={documentResponse()} messageId="m6" activeCitation={null} />,
    );

    expect(screen.getAllByText('Chunk')).toHaveLength(1);
  });
});

    const webOnly = documentResponse({ origin: 'WEB', webSources: [webSource()], sources: [] });
    expect(citationSources(webOnly).map(entry => entry.kind)).toEqual(['web']);

    const documentOnly = documentResponse({ origin: 'DOCUMENTS' });
    expect(citationSources(documentOnly).map(entry => entry.kind)).toEqual(['document', 'document']);
  });
});
