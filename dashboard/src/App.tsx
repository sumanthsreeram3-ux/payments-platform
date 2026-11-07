import { useCallback, useEffect, useState } from 'react'
import { get, post } from './api'
import type { CustomerRow, DeliveryRow, PaymentRow, PlanRow, SubscriptionRow, Summary } from './api'
import './App.css'

const money = (minor: number, currency = 'INR') =>
  new Intl.NumberFormat('en-IN', { style: 'currency', currency }).format(minor / 100)

const time = (iso: string | null) => (iso ? new Date(iso).toLocaleTimeString() : '—')

function Badge({ value }: { value: string }) {
  return <span className={`badge ${value.toLowerCase()}`}>{value.replace('_', ' ')}</span>
}

function Stat({ label, value, tone }: { label: string; value: string | number; tone?: string }) {
  return (
    <div className={`stat ${tone ?? ''}`}>
      <div className="stat-value">{value}</div>
      <div className="stat-label">{label}</div>
    </div>
  )
}

type Notice = { kind: 'ok' | 'error'; text: string }

export default function App() {
  const [summary, setSummary] = useState<Summary | null>(null)
  const [customers, setCustomers] = useState<CustomerRow[]>([])
  const [plans, setPlans] = useState<PlanRow[]>([])
  const [payments, setPayments] = useState<PaymentRow[]>([])
  const [subscriptions, setSubscriptions] = useState<SubscriptionRow[]>([])
  const [deliveries, setDeliveries] = useState<DeliveryRow[]>([])
  const [sinkFailing, setSinkFailing] = useState(false)
  const [offline, setOffline] = useState(false)

  const [customerId, setCustomerId] = useState('')
  const [planId, setPlanId] = useState('')
  const [amount, setAmount] = useState(499)
  const [busy, setBusy] = useState(false)
  const [notice, setNotice] = useState<Notice | null>(null)

  const refresh = useCallback(async () => {
    try {
      const [s, c, p, pay, subs, del, sink] = await Promise.all([
        get<Summary>('/dashboard/summary'),
        get<CustomerRow[]>('/dashboard/customers'),
        get<PlanRow[]>('/plans'),
        get<PaymentRow[]>('/dashboard/payments'),
        get<SubscriptionRow[]>('/dashboard/subscriptions'),
        get<DeliveryRow[]>('/dashboard/webhook-deliveries'),
        get<{ failing: boolean }>('/dev/webhook-sink/mode'),
      ])
      setSummary(s)
      setCustomers(c)
      setPlans(p)
      setPayments(pay)
      setSubscriptions(subs)
      setDeliveries(del)
      setSinkFailing(sink.failing)
      setCustomerId((prev) => prev || c[0]?.id || '')
      setPlanId((prev) => prev || p[0]?.id || '')
      setOffline(false)
    } catch {
      setOffline(true)
    }
  }, [])

  useEffect(() => {
    void refresh()
    const timer = setInterval(() => void refresh(), 2000)
    return () => clearInterval(timer)
  }, [refresh])

  async function run(action: () => Promise<string>) {
    setBusy(true)
    try {
      setNotice({ kind: 'ok', text: await action() })
    } catch (e) {
      setNotice({ kind: 'error', text: (e as Error).message })
    } finally {
      setBusy(false)
      void refresh()
    }
  }

  const chargeBody = () => ({
    customerId,
    amountMinor: Math.round(amount * 100),
    currency: 'INR',
    description: 'Dashboard demo',
  })

  const charge = () =>
    run(async () => {
      const r = await post('/payments', chargeBody(), { 'Idempotency-Key': crypto.randomUUID() })
      return `Payment created (HTTP ${r.status})`
    })

  const chargeTwice = () =>
    run(async () => {
      const key = crypto.randomUUID()
      const results = await Promise.all([
        post('/payments', chargeBody(), { 'Idempotency-Key': key }),
        post('/payments', chargeBody(), { 'Idempotency-Key': key }),
      ])
      const codes = results.map((r) => r.status).sort()
      return `Same Idempotency-Key sent twice at once → HTTP ${codes.join(' and ')}. Only one payment was recorded.`
    })

  const subscribe = () =>
    run(async () => {
      await post('/subscriptions', { customerId, planId })
      return 'Subscription created'
    })

  const selectedCustomer = customers.find((c) => c.id === customerId)

  const toggleCard = () =>
    run(async () => {
      const valid = !(selectedCustomer?.paymentMethodValid ?? true)
      await post(`/billing/dev/customers/${customerId}/payment-method?valid=${valid}`)
      return valid ? 'Card fixed: renewals will succeed' : 'Card declined: renewals will now fail'
    })

  const makeDue = (id: string) =>
    run(async () => {
      await post(`/billing/dev/subscriptions/${id}/make-due`)
      return 'Subscription is now due. The scheduler bills it within about 5 seconds.'
    })

  const runBilling = () =>
    run(async () => {
      const r = await post<{ processed: number }>('/billing/run')
      return `Billing cycle processed ${r.data.processed} subscription(s)`
    })

  const registerEndpoint = () =>
    run(async () => {
      const r = await post<{ alreadyRegistered: boolean }>('/dev/webhook-sink/register')
      return r.data.alreadyRegistered ? 'Demo webhook endpoint already registered' : 'Demo webhook endpoint registered'
    })

  const toggleOutage = () =>
    run(async () => {
      await post(`/dev/webhook-sink/mode?fail=${!sinkFailing}`)
      return sinkFailing ? 'Receiver is healthy again' : 'Receiver outage simulated: deliveries will fail and retry'
    })

      const retryNow = () =>
    run(async () => {
      const r = await post<{ rescheduled: number }>('/dev/webhook-sink/retry-now')
      return `Rescheduled ${r.data.rescheduled} webhook deliver${r.data.rescheduled === 1 ? 'y' : 'ies'} for an immediate retry`
    })

  return (
    <div className="page">
      <header>
        <h1>Payments Platform</h1>
        <span className={`pill ${offline ? 'bad' : 'good'}`}>{offline ? 'API offline' : 'Live'}</span>
      </header>

      {summary && (
        <section className="stats">
          <Stat label="Revenue" value={money(summary.revenueMinor)} />
          <Stat label="Payments succeeded" value={summary.succeededPayments} />
          <Stat label="Payments failed" value={summary.failedPayments} tone={summary.failedPayments ? 'warn' : ''} />
          <Stat label="Active subscriptions" value={summary.activeSubscriptions} />
          <Stat label="Past due" value={summary.pastDueSubscriptions} tone={summary.pastDueSubscriptions ? 'warn' : ''} />
          <Stat label="Canceled" value={summary.canceledSubscriptions} />
          <Stat label="Webhooks pending" value={summary.pendingWebhooks} />
          <Stat label="Webhooks failed" value={summary.failedWebhooks} tone={summary.failedWebhooks ? 'bad' : ''} />
          <Stat label="Unpublished events" value={summary.pendingOutboxEvents} />
          <Stat
            label="Ledger"
            value={summary.ledgerBalanced ? 'Balanced' : 'OUT OF BALANCE'}
            tone={summary.ledgerBalanced ? 'good' : 'bad'}
          />
        </section>
      )}

      <section className="panel">
        <h2>Try it</h2>
        <div className="controls">
          <label>
            Customer
            <select value={customerId} onChange={(e) => setCustomerId(e.target.value)}>
              {customers.map((c) => (
                <option key={c.id} value={c.id}>
                  {c.email}
                  {c.paymentMethodValid ? '' : ' (card declined)'}
                </option>
              ))}
            </select>
          </label>
          <label>
            Plan
            <select value={planId} onChange={(e) => setPlanId(e.target.value)}>
              {plans.map((p) => (
                <option key={p.id} value={p.id}>
                  {p.name} · {money(p.amountMinor, p.currency)}/{p.billingInterval.toLowerCase()}
                </option>
              ))}
            </select>
          </label>
          <label>
            Amount (₹)
            <input type="number" min={1} value={amount} onChange={(e) => setAmount(Number(e.target.value))} />
          </label>
        </div>
        <div className="buttons">
          <button disabled={busy || !customerId} onClick={charge}>Charge</button>
          <button disabled={busy || !customerId} onClick={chargeTwice}>Charge twice (same key)</button>
          <button disabled={busy || !customerId || !planId} onClick={subscribe}>Subscribe</button>
          <button disabled={busy || !customerId} onClick={toggleCard}>
            {selectedCustomer?.paymentMethodValid === false ? 'Fix card' : 'Decline card'}
          </button>
          <button disabled={busy} onClick={runBilling}>Run billing now</button>
          <button disabled={busy} onClick={registerEndpoint}>Register demo webhook</button>
          <button disabled={busy} className={sinkFailing ? 'danger' : ''} onClick={toggleOutage}>
            {sinkFailing ? 'End receiver outage' : 'Simulate receiver outage'}
          </button>
          <button disabled={busy} onClick={retryNow}>Retry webhooks now</button>
        </div>
        {notice && <div className={`notice ${notice.kind}`}>{notice.text}</div>}
      </section>

      <div className="grid">
        <section className="panel">
          <h2>Payments</h2>
          <table>
            <thead>
              <tr><th>Time</th><th>Customer</th><th>Amount</th><th>Status</th></tr>
            </thead>
            <tbody>
              {payments.map((p) => (
                <tr key={p.id}>
                  <td>{time(p.createdAt)}</td>
                  <td>{p.customerEmail}</td>
                  <td>{money(p.amountMinor, p.currency)}</td>
                  <td><Badge value={p.status} /></td>
                </tr>
              ))}
            </tbody>
          </table>
        </section>

        <section className="panel">
          <h2>Subscriptions</h2>
          <table>
            <thead>
              <tr><th>Customer</th><th>Plan</th><th>Status</th><th>Tries</th><th>Next retry</th><th /></tr>
            </thead>
            <tbody>
              {subscriptions.map((s) => (
                <tr key={s.id}>
                  <td>{s.customerEmail}</td>
                  <td>{s.planName}</td>
                  <td><Badge value={s.status} /></td>
                  <td>{s.billingAttempts}</td>
                  <td>{time(s.nextBillingAttemptAt)}</td>
                  <td>
                    {s.status === 'ACTIVE' && (
                      <button className="small" disabled={busy} onClick={() => makeDue(s.id)}>Make due</button>
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </section>

        <section className="panel wide">
          <h2>Webhook deliveries</h2>
          <table>
            <thead>
              <tr><th>Time</th><th>Event</th><th>Status</th><th>Attempts</th><th>Last code</th><th>Next attempt</th></tr>
            </thead>
            <tbody>
              {deliveries.map((d) => (
                <tr key={d.id}>
                  <td>{time(d.createdAt)}</td>
                  <td>{d.eventType}</td>
                  <td><Badge value={d.status} /></td>
                  <td>{d.attempts}</td>
                  <td>{d.lastStatusCode ?? '—'}</td>
                  <td>{time(d.nextAttemptAt)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </section>
      </div>
    </div>
  )
}