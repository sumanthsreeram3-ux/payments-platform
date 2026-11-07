import { useState } from 'react'
import type { FormEvent } from 'react'
import { post } from './api'

type Answer = {
  question: string
  sql: string
  columns: string[]
  rows: unknown[][]
  truncated: boolean
  answer: string
  attempts: number
  model: string
}

const EXAMPLES = [
  'What is our total revenue?',
  'Revenue by customer',
  'Which payments failed recently?',
  'How many subscriptions are past due?',
  'How many webhook deliveries failed?',
  'Are the ledger accounts balanced?',
]

const cell = (v: unknown) => (v === null || v === undefined ? '—' : String(v))

export default function AskPanel() {
  const [question, setQuestion] = useState('')
  const [result, setResult] = useState<Answer | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [loading, setLoading] = useState(false)

  async function ask(q: string) {
    const text = q.trim()
    if (!text) return
    setQuestion(text)
    setLoading(true)
    setError(null)
    try {
      const r = await post<Answer>('/assistant/ask', { question: text })
      setResult(r.data)
    } catch (e) {
      setResult(null)
      setError((e as Error).message)
    } finally {
      setLoading(false)
    }
  }

  function onSubmit(e: FormEvent) {
    e.preventDefault()
    void ask(question)
  }

  return (
    <section className="panel">
      <h2>Ask your ledger</h2>
      <form className="ask-row" onSubmit={onSubmit}>
        <input
          className="ask-input"
          value={question}
          maxLength={500}
          placeholder="e.g. Which customers generated the most revenue?"
          onChange={(e) => setQuestion(e.target.value)}
        />
        <button type="submit" disabled={loading || !question.trim()}>
          {loading ? 'Thinking…' : 'Ask'}
        </button>
      </form>
      <div className="chips">
        {EXAMPLES.map((ex) => (
          <button key={ex} type="button" className="chip" disabled={loading} onClick={() => void ask(ex)}>
            {ex}
          </button>
        ))}
      </div>

      {error && <div className="notice error">{error}</div>}

      {result && (
        <div className="ask-result">
          <p className="ask-answer">{result.answer}</p>
          <p className="ask-meta">
            Model: <code>{result.model}</code>
            {result.attempts > 1 && ` · corrected itself after ${result.attempts - 1} failed attempt(s)`}
            {result.truncated && ' · showing the first 200 rows'}
          </p>
          <details>
            <summary>SQL that was run (read-only)</summary>
            <pre>{result.sql}</pre>
          </details>
          {result.rows.length > 0 && (
            <table>
              <thead>
                <tr>{result.columns.map((c) => <th key={c}>{c}</th>)}</tr>
              </thead>
              <tbody>
                {result.rows.map((row, i) => (
                  <tr key={i}>{row.map((v, j) => <td key={j}>{cell(v)}</td>)}</tr>
                ))}
              </tbody>
            </table>
          )}
        </div>
      )}
    </section>
  )
}