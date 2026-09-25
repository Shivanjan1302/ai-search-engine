import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import { RagAnswer } from '../components/RagAnswer';
import type { WebSearchResult } from '../types/api';

function webSource(overrides: Partial<WebSearchResult> = {}): WebSearchResult {
  return {
    title: 'Allowed source',
    url: 'https://allowed.test/article',
    snippet: 'snippet',
    publisher: 'publisher',
    domain: 'allowed.test',
    path: '/article',
    breadcrumb: 'allowed.test › article',
    publishedDate: '2026-09-24',
    ...overrides,
  };
}

describe('RagAnswer citation rendering', () => {
  it('renders a clickable chip for a citation inside the valid range', async () => {
    const onCitationClick = vi.fn();
    render(
      <RagAnswer
        markdown={'Leaves accrue monthly [E1].'}
        citationCount={2}
        allowedWebUrls={[]}
        onCitationClick={onCitationClick}
      />,
    );

    const chip = screen.getByRole('button', { name: 'View source E1' });
    expect(chip).toHaveTextContent('E1');

    await userEvent.click(chip);
    expect(onCitationClick).toHaveBeenCalledExactlyOnceWith(1);
  });

  it('leaves an out-of-range citation as plain text and does not emit a chip', () => {
    render(
      <RagAnswer
        markdown={'Unsupported claim [E9].'}
        citationCount={2}
        allowedWebUrls={[]}
        onCitationClick={vi.fn()}
      />,
    );

    expect(screen.queryByRole('button', { name: 'View source E9' })).toBeNull();
    expect(screen.getByText(/\[E9]\./)).toBeInTheDocument();
  });

  it('never renders a chip for a zero or non-numeric citation marker', () => {
    render(
      <RagAnswer
        markdown={'Bad markers [E0] and [Ex].'}
        citationCount={3}
        allowedWebUrls={[]}
        onCitationClick={vi.fn()}
      />,
    );

    expect(screen.queryByRole('button')).toBeNull();
  });

  it('renumbers nothing: chip order follows the text order of the answer', () => {
    render(
      <RagAnswer
        markdown={'First [E2] then [E1].'}
        citationCount={2}
        allowedWebUrls={[]}
        onCitationClick={vi.fn()}
      />,
    );

    const chips = screen.getAllByRole('button');
    expect(chips.map(chip => chip.textContent)).toEqual(['E2', 'E1']);
  });
});

describe('RagAnswer unsafe URL handling', () => {
  it('keeps a link only when its URL is an allowed http(s) source', () => {
    render(
      <RagAnswer
        markdown={'See [the article](https://allowed.test/article).'}
        citationCount={0}
        allowedWebUrls={[webSource()]}
        onCitationClick={vi.fn()}
      />,
    );

    const link = screen.getByRole('link');
    expect(link).toHaveAttribute('href', 'https://allowed.test/article');
    expect(link).toHaveAttribute('rel', 'noopener noreferrer');
  });

  it('unwraps a link whose URL is not in the allowed source list', () => {
    render(
      <RagAnswer
        markdown={'See [the article](https://not-allowed.test/x).'}
        citationCount={0}
        allowedWebUrls={[webSource()]}
        onCitationClick={vi.fn()}
      />,
    );

    expect(screen.queryByRole('link')).toBeNull();
    expect(document.querySelector('.markdown-content')).toHaveTextContent('the article');
  });

  it('unwraps a javascript: URL even when the anchor is otherwise well formed', () => {
    render(
      <RagAnswer
        markdown={'Click [here](javascript:alert(1)).'}
        citationCount={0}
        allowedWebUrls={[]}
        onCitationClick={vi.fn()}
      />,
    );

    expect(screen.queryByRole('link')).toBeNull();
    expect(document.body.innerHTML).not.toContain('javascript:');
  });

  it('strips script content from the rendered markdown', () => {
    render(
      <RagAnswer
        markdown={'Hello <script>window.__pwned = true;</script> world [E1].'}
        citationCount={1}
        allowedWebUrls={[]}
        onCitationClick={vi.fn()}
      />,
    );

    expect((window as unknown as { __pwned?: boolean }).__pwned).toBeUndefined();
    expect(document.querySelector('script')).toBeNull();
  });
});
