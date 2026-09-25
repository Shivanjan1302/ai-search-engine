import '@testing-library/jest-dom/vitest';
import { cleanup } from '@testing-library/react';
import { afterEach, vi } from 'vitest';

// jsdom does not implement scrollIntoView; AskView calls it on a ref.
if (!Element.prototype.scrollIntoView) {
  Element.prototype.scrollIntoView = vi.fn();
}

// Unmount between tests so queries never see another test's DOM.
afterEach(() => {
  cleanup();
});
