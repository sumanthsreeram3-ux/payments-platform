const BASE = '/api'

export type Summary = {
  customers: number
  activeSubscriptions: number
  pastDueSubscriptions: number
  canceledSubscriptions: number
  succeededPayments: number
  failedPayments: number
  revenueMinor: number
  pendingOutboxEvents: number
  pendingWebhooks: number
  failedWebhooks: number
  ledgerBalanced: boolean
}
export type CustomerRow = { id: string; email: string; name: string; paymentMethodValid: boolean }
export type PlanRow = { id: string; name: string; amountMinor: number; currency: string; billingInterval: string }
export type PaymentRow = {
  id: string
  customerEmail: string
  amountMinor: number
  currency: string
  status: string
  description: string | null
  createdAt: string
}
export type SubscriptionRow = {
  id: string
  customerEmail: string
  planName: string
  status: string
  billingAttempts: number
  nextBillingAttemptAt: string | null
  currentPeriodEnd: string
}
export type DeliveryRow = {
  id: string
  endpointUrl: string
  eventType: string
  status: string
  attempts: number
  lastStatusCode: number | null
  nextAttemptAt: string | null
  createdAt: string
}

export type CallResult<T> = { status: number; data: T; replayed: boolean }

async function call<T>(
  path: string,
  init: { method?: string; body?: unknown; headers?: Record<string, string> } = {},
): Promise<CallResult<T>> {
  const res = await fetch(BASE + path, {
    method: init.method ?? 'GET',
    headers: { 'Content-Type': 'application/json', ...(init.headers ?? {}) },
    body: init.body === undefined ? undefined : JSON.stringify(init.body),
  })
  const text = await res.text()
  let data: unknown = undefined
  try {
    data = text ? JSON.parse(text) : undefined
  } catch {
    data = text
  }
  if (!res.ok) {
    const problem = data as { detail?: string; message?: string } | undefined
    throw new Error(`${res.status} ${problem?.detail ?? problem?.message ?? res.statusText}`)
  }
  return { status: res.status, data: data as T, replayed: res.headers.get('Idempotent-Replayed') === 'true' }
}

export const get = async <T>(path: string): Promise<T> => (await call<T>(path)).data

export const post = <T = unknown>(path: string, body?: unknown, headers?: Record<string, string>) =>
  call<T>(path, { method: 'POST', body, headers })