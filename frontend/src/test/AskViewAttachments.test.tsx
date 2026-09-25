import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { AskView } from '../pages/DashboardPage';
import { api, ApiError } from '../services/api';
import type { DocumentRecord } from '../types/api';

vi.mock('../services/api', async importOriginal => {
  const actual = await importOriginal<typeof import('../services/api')>();
  return {
    ...actual,
    api: { ...actual.api, ask: vi.fn(), upload: vi.fn() },
  };
});

const askMock = vi.mocked(api.ask);
const uploadMock = vi.mocked(api.upload);

function uploaded(id: number, filename: string): DocumentRecord {
  return { id, filename, uploadedAt: '2026-09-24T00:00:00Z' };
}

function file(name: string): File {
  return new File(['content'], name, { type: 'text/plain' });
}

async function attach(files: File[]) {
  const input = document.querySelector<HTMLInputElement>('input[type="file"]')!;
  await userEvent.upload(input, files);
}

async function renderAsk() {
  const onUploaded = vi.fn();
  const result = render(<AskView onUploaded={onUploaded} />);
  return { onUploaded, ...result };
}

beforeEach(() => {
  askMock.mockReset();
  uploadMock.mockReset();
  askMock.mockResolvedValue({ answer: 'Answer', sources: [], webSources: [], origin: 'MODEL_KNOWLEDGE' });
});

afterEach(() => {
  vi.clearAllMocks();
});

describe('AskView independent attachment state', () => {
  it('tracks each attachment separately and uploads them one at a time', async () => {
    uploadMock
      .mockResolvedValueOnce(uploaded(1, 'a.txt'))
      .mockResolvedValueOnce(uploaded(2, 'b.txt'));
    const { onUploaded } = await renderAsk();

    await attach([file('a.txt'), file('b.txt')]);
    expect(screen.getAllByText('Ready to upload')).toHaveLength(2);

    await userEvent.click(screen.getAllByRole('button', { name: 'Upload' })[0]);
    await waitFor(() => expect(screen.getAllByText('Uploaded ✓')).toHaveLength(1));
    expect(screen.getAllByText('Ready to upload')).toHaveLength(1);

    await userEvent.click(screen.getByRole('button', { name: 'Upload' }));
    await waitFor(() => expect(screen.getAllByText('Uploaded ✓')).toHaveLength(2));

    expect(onUploaded).toHaveBeenNthCalledWith(1, uploaded(1, 'a.txt'));
    expect(onUploaded).toHaveBeenNthCalledWith(2, uploaded(2, 'b.txt'));
  });

  it('ignores a duplicate file selected twice', async () => {
    await renderAsk();

    // The same File instance represents the same document picked twice; its
    // name:size:lastModified identity is what the component de-duplicates on.
    const sameFile = file('a.txt');
    await attach([sameFile]);
    await attach([sameFile]);

    expect(screen.getAllByRole('button', { name: /Remove a\.txt/ })).toHaveLength(1);
    expect(screen.getAllByText('Ready to upload')).toHaveLength(1);
  });
});

describe('AskView failed upload retry', () => {
  it('shows a failure, keeps the attachment, and retries on demand', async () => {
    uploadMock
      .mockRejectedValueOnce(new ApiError(422, 'Dronzer could not process that document.'))
      .mockResolvedValueOnce(uploaded(7, 'retry.txt'));
    const { onUploaded } = await renderAsk();

    await attach([file('retry.txt')]);
    await userEvent.click(screen.getByRole('button', { name: 'Upload' }));

    await waitFor(() => expect(screen.getByRole('alert')).toHaveTextContent('could not process'));
    expect(screen.getByText('Upload failed')).toBeInTheDocument();
    expect(onUploaded).not.toHaveBeenCalled();

    await userEvent.click(screen.getByRole('button', { name: 'Retry' }));

    await waitFor(() => expect(screen.getByText('Uploaded ✓')).toBeInTheDocument());
    expect(screen.queryByRole('alert')).toBeNull();
    expect(onUploaded).toHaveBeenCalledExactlyOnceWith(uploaded(7, 'retry.txt'));
  });

  it('keeps other attachments unaffected when one upload fails', async () => {
    uploadMock
      .mockRejectedValueOnce(new ApiError(500, 'boom'))
      .mockResolvedValueOnce(uploaded(2, 'ok.txt'));
    await renderAsk();

    await attach([file('bad.txt'), file('ok.txt')]);
    await userEvent.click(screen.getAllByRole('button', { name: 'Upload' })[0]);
    await waitFor(() => expect(screen.getByText('Upload failed')).toBeInTheDocument());

    await userEvent.click(screen.getByRole('button', { name: 'Upload' }));
    await waitFor(() => expect(screen.getByText('Uploaded ✓')).toBeInTheDocument());
    expect(screen.getByText('Upload failed')).toBeInTheDocument();
  });
});

describe('AskView removal while uploading', () => {
  it('disables removal only for the attachment that is currently uploading', async () => {
    let release: (value: DocumentRecord) => void = () => {};
    uploadMock.mockReturnValueOnce(new Promise<DocumentRecord>(resolve => { release = resolve; }));
    await renderAsk();

    await attach([file('slow.txt'), file('other.txt')]);
    await userEvent.click(screen.getAllByRole('button', { name: 'Upload' })[0]);

    await waitFor(() => expect(screen.getByText('Uploading...')).toBeInTheDocument());
    expect(screen.getByRole('button', { name: 'Remove slow.txt' })).toBeDisabled();
    expect(screen.getByRole('button', { name: 'Remove other.txt' })).toBeEnabled();

    release(uploaded(1, 'slow.txt'));
    await waitFor(() => expect(screen.getByText('Uploaded ✓')).toBeInTheDocument());
    expect(screen.getByRole('button', { name: 'Remove slow.txt' })).toBeEnabled();
  });

  it('removes a settled attachment and clears its row', async () => {
    uploadMock.mockResolvedValue(uploaded(3, 'done.txt'));
    await renderAsk();

    await attach([file('done.txt')]);
    await userEvent.click(screen.getByRole('button', { name: 'Upload' }));
    await waitFor(() => expect(screen.getByText('Uploaded ✓')).toBeInTheDocument());

    await userEvent.click(screen.getByRole('button', { name: 'Remove done.txt' }));

    expect(screen.queryByRole('button', { name: 'Remove done.txt' })).toBeNull();
    expect(screen.queryByText('Attachments')).toBeNull();
  });
});
