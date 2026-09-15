import { Link } from 'react-router-dom';

export function LandingPage() {
  return <main className="landing-page">
    <nav className="landing-nav"><Link className="brand" to="/"><span className="brand-mark">D</span> dronzer</Link><a href="https://github.com/Shivanjan1302/ai-search-engine" target="_blank" rel="noreferrer">View GitHub <span aria-hidden="true">↗</span></a></nav>
    <section className="hero">
      <div className="hero-copy"><p className="eyebrow">Document intelligence, grounded</p><h1>Find the thread<br /><em>inside your knowledge.</em></h1><p className="hero-text">Dronzer turns scattered documents into a searchable, conversational knowledge base. Ingest once. Search semantically. Ask with confidence.</p><div className="hero-actions"><Link className="button button-primary" to="/register">Try Dronzer <span aria-hidden="true">→</span></Link><a className="button button-quiet" href="https://github.com/Shivanjan1302/ai-search-engine" target="_blank" rel="noreferrer">View the build ↗</a></div></div>
      <div className="hero-diagram" aria-label="Dronzer workflow"><div className="diagram-label">THE PIPELINE</div><div className="pipeline"><div className="pipeline-node"><strong>01</strong><span>Ingest</span><small>PDF / TXT</small></div><div className="pipeline-line" /><div className="pipeline-node active"><strong>02</strong><span>Understand</span><small>pgvector</small></div><div className="pipeline-line" /><div className="pipeline-node"><strong>03</strong><span>Answer</span><small>Gemini RAG</small></div></div><div className="diagram-note">Every answer traces back to a source<br />in your own documents.</div></div>
    </section>
    <section className="tech-strip"><span>BUILT WITH</span><strong>Spring Boot</strong><strong>PostgreSQL</strong><strong>pgvector</strong><strong>Gemini</strong><strong>React / TypeScript</strong></section>
    <footer className="landing-footer"><span>DRONZER / AI SEARCH ENGINE</span><span>A focused portfolio build by Shivanjan</span></footer>
  </main>;
}
