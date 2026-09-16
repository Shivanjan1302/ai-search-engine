import { useEffect, useRef, useState } from 'react';
import DOMPurify from 'dompurify';
import { marked } from 'marked';
import { DashboardLayout } from '../layouts/DashboardLayout';
import { SectionHeader } from '../components/SectionHeader';
import { StatusMessage } from '../components/StatusMessage';
import { api, ApiError } from '../services/api';
import type { DocumentRecord, NoteRecord, RagResponse, WebSearchResponse } from '../types/api';
import { readableError } from '../utils/errors';

function formatDate(value: string) { return new Intl.DateTimeFormat(undefined, { month: 'short', day: 'numeric', year: 'numeric' }).format(new Date(value)); }

export function DashboardPage() {
  const [active, setActive] = useState('overview');
  const [documents, setDocuments] = useState<DocumentRecord[]>([]);
  const [notes, setNotes] = useState<NoteRecord[]>([]);
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState('');
  useEffect(() => { Promise.all([api.documents(), api.notes()]).then(([docs, nextNotes]) => { setDocuments(docs); setNotes(nextNotes); }).catch(error => setLoadError(readableError(error, 'Could not load your workspace.'))).finally(() => setLoading(false)); }, []);
  return <DashboardLayout active={active} onChange={setActive}><div className="content-wrap">{loading ? <div className="loading-state">Loading your workspace...</div> : loadError ? <StatusMessage>{loadError}</StatusMessage> : active === 'documents' ? <DocumentsView documents={documents} onUploaded={document => setDocuments(current => [document, ...current])} /> : active === 'ask' ? <AskView /> : active === 'web' ? <WebSearchView /> : active === 'notes' ? <NotesView notes={notes} onChange={setNotes} /> : <Overview documents={documents} notes={notes} onChange={setActive} />}</div></DashboardLayout>;
}

function Overview({ documents, notes, onChange }: { documents: DocumentRecord[]; notes: NoteRecord[]; onChange: (value: string) => void }) {
  return <><SectionHeader eyebrow="Your workspace" title="Good to see you." description="A focused place for the ideas hiding in your files." /><div className="stat-grid"><button className="stat-card" onClick={() => onChange('documents')}><span className="stat-value">{documents.length}</span><span className="stat-label">Documents indexed <b>→</b></span></button><button className="stat-card" onClick={() => onChange('notes')}><span className="stat-value">{notes.length}</span><span className="stat-label">Notes captured <b>→</b></span></button><button className="stat-card accent" onClick={() => onChange('ask')}><span className="stat-value">Ask</span><span className="stat-label">Search your knowledge <b>→</b></span></button></div><div className="overview-grid"><section className="panel"><div className="panel-heading"><h2>Recent documents</h2><button className="text-button" onClick={() => onChange('documents')}>View all →</button></div>{documents.length ? documents.slice(0, 4).map(document => <DocumentRow key={document.id} document={document} />) : <EmptyState title="No documents yet" text="Upload a PDF or TXT to give Dronzer something to understand." action="Upload a document" onClick={() => onChange('documents')} />}</section><section className="panel dark-panel"><p className="eyebrow">PUBLIC WEB</p><h2>Find fresh context.</h2><p>Search beyond your private workspace without mixing sources.</p><button className="button button-light" onClick={() => onChange('web')}>Search the web <span>→</span></button></section></div></>;
}

function DocumentsView({ documents, onUploaded }: { documents: DocumentRecord[]; onUploaded: (document: DocumentRecord) => void }) {
  const [dragging, setDragging] = useState(false); const [busy, setBusy] = useState(false); const [message, setMessage] = useState(''); const [error, setError] = useState('');
  async function upload(file?: File) { if (!file) return; setMessage(''); setError(''); setBusy(true); try { const document = await api.upload(file); onUploaded(document); setMessage(`${document.filename} is now indexed.`); } catch (requestError) { setError(readableError(requestError, 'Upload failed. The document was not saved.')); } finally { setBusy(false); } }
  return <><SectionHeader eyebrow="Knowledge base" title="Documents" description="Add the source material Dronzer will search and cite." action={<button className="button button-dark" onClick={() => document.getElementById('file-input')?.click()} disabled={busy}>{busy ? 'Uploading...' : '+ Upload file'}</button>} /><input id="file-input" className="visually-hidden" type="file" accept=".pdf,.txt,application/pdf,text/plain" onChange={event => upload(event.target.files?.[0])} /><div className={`drop-zone ${dragging ? 'dragging' : ''}`} onDragOver={event => { event.preventDefault(); setDragging(true); }} onDragLeave={() => setDragging(false)} onDrop={event => { event.preventDefault(); setDragging(false); upload(event.dataTransfer.files[0]); }}><span className="upload-glyph">↑</span><strong>{busy ? 'Processing your file...' : 'Drop a document here'}</strong><span>PDF or TXT · up to 10 MB</span><button className="text-button" onClick={() => document.getElementById('file-input')?.click()}>Choose from your device</button></div>{error && <StatusMessage>{error}</StatusMessage>}{message && <StatusMessage tone="success">{message}</StatusMessage>}<section className="panel table-panel"><div className="panel-heading"><h2>Indexed documents <span className="count">{documents.length}</span></h2></div>{documents.length ? documents.map(document => <DocumentRow key={document.id} document={document} />) : <EmptyState title="Your knowledge base is empty" text="Documents you upload will appear here after successful processing." />}</section></>;
}

interface ChatMessage { id: string; role: 'user' | 'assistant'; text: string; response?: RagResponse; }

function AskView() {
  const [question, setQuestion] = useState('');
  const [messages, setMessages] = useState<ChatMessage[]>([]);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const endRef = useRef<HTMLDivElement>(null);

  useEffect(() => { endRef.current?.scrollIntoView({ behavior: 'smooth' }); }, [messages, busy]);

  async function ask(nextQuestion = question) {
    const trimmed = nextQuestion.trim();
    if (!trimmed || busy) return;
    setQuestion(''); setError(''); setBusy(true);
    const userMessage: ChatMessage = { id: `${Date.now()}-user`, role: 'user', text: trimmed };
    setMessages(current => [...current, userMessage]);
    try {
      const response = await api.ask(trimmed);
      setMessages(current => [...current, { id: `${Date.now()}-assistant`, role: 'assistant', text: response.answer, response }]);
    } catch (requestError) { setError(readableError(requestError, 'We could not complete that question.')); }
    finally { setBusy(false); }
  }

  function clearConversation() { setMessages([]); setError(''); }

  return <><SectionHeader eyebrow="Grounded answers" title="Ask Dronzer" description="A private conversation with your indexed documents, with web evidence when needed." action={<button className="button button-quiet" onClick={clearConversation} disabled={!messages.length}>Clear chat</button>} /><section className="chat-shell"><div className="chat-history">{!messages.length && !busy ? <div className="chat-empty"><span className="chat-mark">D</span><h2>What should we find?</h2><p>Ask about your documents in plain language. Try one of these to begin.</p><div className="suggestion-list"><button onClick={() => ask('What technologies are used in Dronzer?')}>What technologies are used in Dronzer?</button><button onClick={() => ask('Summarize the most important project decisions.')}>Summarize the most important project decisions.</button><button onClick={() => ask('What are the latest features of Java 25?')}>What are the latest features of Java 25?</button></div></div> : messages.map(message => <ChatMessageView key={message.id} message={message} />)}{busy && <div className="chat-message assistant-message"><span className="message-avatar">D</span><div className="typing-indicator" aria-label="Dronzer is thinking"><i /><i /><i /></div></div>}<div ref={endRef} /></div>{error && <StatusMessage>{error}</StatusMessage>}<form className="chat-composer" onSubmit={event => { event.preventDefault(); void ask(); }}><textarea aria-label="Ask Dronzer a question" rows={2} value={question} onChange={event => setQuestion(event.target.value)} placeholder="Ask anything about your workspace..." disabled={busy} /><button className="button button-dark" type="submit" disabled={busy || !question.trim()}>{busy ? 'Thinking...' : 'Send'} <span aria-hidden="true">↑</span></button></form></section></>;
}

function ChatMessageView({ message }: { message: ChatMessage }) {
  if (message.role === 'user') return <div className="chat-message user-message"><div className="message-bubble">{message.text}</div></div>;
  const response = message.response;
  return <div className="chat-message assistant-message"><span className="message-avatar">D</span><div className="assistant-content"><div className="markdown-content" dangerouslySetInnerHTML={{ __html: DOMPurify.sanitize(String(marked.parse(message.text, { async: false }))) }} /><div className="message-tools"><button className="text-button" onClick={() => void navigator.clipboard.writeText(message.text)}>Copy answer</button><span className="origin-label">{response?.origin === 'WEB' ? 'Web grounded' : response?.origin === 'DOCUMENTS_AND_WEB' ? 'Documents + web' : response?.origin === 'INSUFFICIENT_EVIDENCE' ? 'Insufficient evidence' : 'Document grounded'}</span></div>{response && <SourceGroups response={response} />}</div></div>;
}

function SourceGroups({ response }: { response: RagResponse }) {
  const documentSources = response.sources ?? [];
  const webSources = response.webSources ?? [];
  if (!documentSources.length && !webSources.length) return null;
  return <div className="source-groups">{documentSources.length > 0 && <section className="source-group"><div className="source-group-heading"><span>Document sources</span><span className="count">{documentSources.length}</span></div>{documentSources.map((source, index) => <div className="source-card" key={`${source.documentId}-${source.chunkIndex}-${index}`}><span className="source-index">0{index + 1}</span><div><strong>{source.filename}</strong><small>Relevant source</small></div></div>)}</section>}{webSources.length > 0 && <section className="source-group"><div className="source-group-heading"><span>Web sources</span><span className="count">{webSources.length}</span></div>{webSources.map((source, index) => <a className="source-card web-source-card" href={source.url} target="_blank" rel="noreferrer" key={`${source.url}-${index}`}><span className="source-index">↗</span><div><strong>{source.publisher || source.title}</strong><small>{source.title}</small></div></a>)}</section>}</div>;
}

function WebSearchView() {
  const [query, setQuery] = useState(''); const [result, setResult] = useState<WebSearchResponse | null>(null); const [busy, setBusy] = useState(false); const [error, setError] = useState('');
  async function search(nextQuery = query) { const trimmed = nextQuery.trim(); if (!trimmed || busy) return; setBusy(true); setError(''); try { setResult(await api.webSearch(trimmed)); } catch (requestError) { setError(readableError(requestError, 'Web search is unavailable right now.')); } finally { setBusy(false); } }
  return <div className="web-search-page"><div className="web-search-intro"><p className="eyebrow">Public web</p><h1>Search the web.</h1><p className="muted">Find fresh context beyond your private workspace.</p></div><form className="web-search-bar" onSubmit={event => { event.preventDefault(); void search(); }}><input aria-label="Search the web" value={query} onChange={event => setQuery(event.target.value)} placeholder="Search for anything..." /><button className="button button-dark" type="submit" disabled={busy || !query.trim()} aria-label="Search">{busy ? 'Searching...' : 'Search'} <span aria-hidden="true">⌕</span></button></form>{error && <StatusMessage>{error}</StatusMessage>}{result && <section className="web-results"><div className="web-results-heading"><div><p className="eyebrow">Web results</p><h2>Results for “{result.query}”</h2></div><span className="result-count">{result.results.length} results</span></div>{result.results.length ? result.results.map((item, index) => <article className="web-result" key={`${item.url}-${index}`}><a href={item.url} target="_blank" rel="noreferrer"><h3>{item.title}</h3></a><div className="result-url">{item.publisher || item.url}</div><p>{item.snippet}</p></article>) : <div className="empty-state"><strong>No results found</strong><p>Try a broader or more specific search.</p></div>}</section>}</div>;
}

function NotesView({ notes, onChange }: { notes: NoteRecord[]; onChange: (notes: NoteRecord[]) => void }) {
  const [title, setTitle] = useState(''); const [editing, setEditing] = useState<number | null>(null); const [busy, setBusy] = useState(false); const [error, setError] = useState('');
  async function save() { if (!title.trim()) return; setBusy(true); setError(''); try { if (editing === null) { const note = await api.createNote(title.trim()); onChange([note, ...notes]); } else { const note = await api.updateNote(editing, title.trim()); onChange(notes.map(item => item.id === note.id ? note : item)); } setTitle(''); setEditing(null); } catch (requestError) { setError(readableError(requestError, 'Could not save that note.')); } finally { setBusy(false); } }
  async function remove(id: number) { setError(''); try { await api.deleteNote(id); onChange(notes.filter(note => note.id !== id)); } catch (requestError) { setError(readableError(requestError, 'Could not delete that note.')); } }
  return <><SectionHeader eyebrow="Your thinking space" title="Notes" description="Keep a lightweight trail of ideas alongside your source material." /><section className="note-composer"><label htmlFor="note-title">{editing === null ? 'Capture a note' : 'Edit note'}<input id="note-title" value={title} onChange={event => setTitle(event.target.value)} onKeyDown={event => { if (event.key === 'Enter') save(); }} placeholder="A thought worth keeping..." /></label><button className="button button-dark" onClick={save} disabled={busy || !title.trim()}>{busy ? 'Saving...' : editing === null ? 'Add note' : 'Save note'}</button></section>{error && <StatusMessage>{error}</StatusMessage>}<section className="notes-list">{notes.length ? notes.map(note => <article className="note-card" key={note.id}><span className="note-dot" /><p>{note.title}</p><div className="note-actions"><button className="text-button" onClick={() => { setEditing(note.id); setTitle(note.title); }}>Edit</button><button className="text-button danger-text" onClick={() => remove(note.id)}>Delete</button></div></article>) : <div className="panel"><EmptyState title="No notes yet" text="Capture a decision, idea, or question above." /></div>}</section></>;
}

function DocumentRow({ document }: { document: DocumentRecord }) { return <div className="document-row"><span className="file-badge">{document.filename.toLowerCase().endsWith('.pdf') ? 'PDF' : 'TXT'}</span><div><strong>{document.filename}</strong><small>Indexed {formatDate(document.uploadedAt)}</small></div><span className="row-status">Ready</span></div>; }
function EmptyState({ title, text, action, onClick }: { title: string; text: string; action?: string; onClick?: () => void }) { return <div className="empty-state"><strong>{title}</strong><p>{text}</p>{action && <button className="text-button" onClick={onClick}>{action} →</button>}</div>; }
