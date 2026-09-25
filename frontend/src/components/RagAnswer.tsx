import type { MouseEvent } from 'react';
import DOMPurify from 'dompurify';
import { marked } from 'marked';
import type { WebSearchResult } from '../types/api';

const CITATION_PATTERN = /\[E(\d+)]/g;
const SAFE_LINK_PROTOCOLS = new Set(['http:', 'https:']);

interface RagAnswerProps {
  markdown: string;
  citationCount: number;
  allowedWebUrls: WebSearchResult[];
  onCitationClick: (citationNumber: number) => void;
}

function normalizeExternalUrl(value: string | null | undefined): string | null {
  if (!value) return null;
  try {
    const url = new URL(value, window.location.origin);
    return SAFE_LINK_PROTOCOLS.has(url.protocol) ? url.href : null;
  } catch {
    return null;
  }
}

function replaceCitationMarkers(root: ParentNode, citationCount: number) {
  const walker = document.createTreeWalker(root, NodeFilter.SHOW_TEXT);
  const textNodes: Text[] = [];
  let current = walker.nextNode();
  while (current) {
    const node = current as Text;
    const parent = node.parentElement;
    if (parent && !parent.closest('a, button, code, pre')) {
      CITATION_PATTERN.lastIndex = 0;
      if (CITATION_PATTERN.test(node.nodeValue ?? '')) textNodes.push(node);
    }
    current = walker.nextNode();
  }

  for (const node of textNodes) {
    const text = node.nodeValue ?? '';
    const fragment = document.createDocumentFragment();
    let cursor = 0;
    CITATION_PATTERN.lastIndex = 0;

    for (const match of text.matchAll(CITATION_PATTERN)) {
      const raw = match[0];
      const start = match.index ?? 0;
      const citationNumber = Number(match[1]);
      fragment.append(document.createTextNode(text.slice(cursor, start)));

      if (Number.isSafeInteger(citationNumber) && citationNumber >= 1 && citationNumber <= citationCount) {
        // This element is created from a bounded integer, never from arbitrary citation text.
        const chip = document.createElement('button');
        chip.type = 'button';
        chip.className = 'citation-chip';
        chip.dataset.citationNumber = String(citationNumber);
        chip.setAttribute('aria-label', `View source E${citationNumber}`);
        chip.textContent = `E${citationNumber}`;
        fragment.append(chip);
      } else {
        fragment.append(document.createTextNode(raw));
      }
      cursor = start + raw.length;
    }

    fragment.append(document.createTextNode(text.slice(cursor)));
    node.replaceWith(fragment);
  }
}

function prepareAnswerHtml(markdown: string, citationCount: number, allowedWebUrls: WebSearchResult[]) {
  const rendered = String(marked.parse(markdown, { async: false }));
  const sanitized = DOMPurify.sanitize(rendered);
  const template = document.createElement('template');
  template.innerHTML = sanitized;

  const trustedUrls = new Set(
    allowedWebUrls
      .map(source => normalizeExternalUrl(source.url))
      .filter((url): url is string => url !== null),
  );

  for (const anchor of Array.from(template.content.querySelectorAll('a'))) {
    const href = normalizeExternalUrl(anchor.getAttribute('href'));
    if (!href || !trustedUrls.has(href)) {
      anchor.replaceWith(...Array.from(anchor.childNodes));
      continue;
    }
    anchor.href = href;
    anchor.target = '_blank';
    anchor.rel = 'noopener noreferrer';
  }

  replaceCitationMarkers(template.content, citationCount);

  // Sanitize once more after the narrow, text-only DOM augmentation.
  return DOMPurify.sanitize(template.innerHTML);
}

export function RagAnswer({ markdown, citationCount, allowedWebUrls, onCitationClick }: RagAnswerProps) {
  function handleClick(event: MouseEvent<HTMLDivElement>) {
    const target = event.target;
    if (!(target instanceof Element)) return;
    const chip = target.closest<HTMLButtonElement>('button.citation-chip[data-citation-number]');
    if (!chip || !event.currentTarget.contains(chip)) return;

    const value = chip.dataset.citationNumber;
    if (!value || !/^[1-9]\d*$/.test(value)) return;
    const citationNumber = Number(value);
    if (Number.isSafeInteger(citationNumber) && citationNumber <= citationCount) onCitationClick(citationNumber);
  }

  return (
    <div
      className="markdown-content"
      onClick={handleClick}
      dangerouslySetInnerHTML={{ __html: prepareAnswerHtml(markdown, citationCount, allowedWebUrls) }}
    />
  );
}
