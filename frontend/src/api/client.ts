import { RequestLogEntry, ErrorResponse } from './types';
import { formatTimeOnly } from '../lib/format';

export class ApiError extends Error {
  status: number;
  code: string;
  isNetworkError: boolean;
  responseBody?: unknown;

  constructor(status: number, code: string, message: string, isNetworkError = false, responseBody?: unknown) {
    super(message);
    this.name = 'ApiError';
    this.status = status;
    this.code = code;
    this.isNetworkError = isNetworkError;
    this.responseBody = responseBody;
  }
}

export interface ApiFetchOptions extends RequestInit {
  idempotencyKey?: string;
  skipLog?: boolean;
}

export interface ApiResponse<T> {
  data: T;
  status: number;
  headers: Headers;
}

type LogListener = (entry: RequestLogEntry) => void;
const logListeners: Set<LogListener> = new Set();

export function subscribeRequestLog(listener: LogListener): () => void {
  logListeners.add(listener);
  return () => {
    logListeners.delete(listener);
  };
}

function notifyLog(entry: RequestLogEntry) {
  logListeners.forEach(listener => {
    try {
      listener(entry);
    } catch (err) {
      console.error('Error in request log listener', err);
    }
  });
}

export async function apiFetch<T>(
  path: string,
  options: ApiFetchOptions = {}
): Promise<ApiResponse<T>> {
  const { idempotencyKey, skipLog = false, headers: customHeaders, body, ...rest } = options;

  const headers = new Headers(customHeaders);
  if (body && !headers.has('Content-Type')) {
    headers.set('Content-Type', 'application/json');
  }
  if (idempotencyKey) {
    headers.set('Idempotency-Key', idempotencyKey);
  }

  const startTime = performance.now();
  let status = 0;
  let responseData: any = null;
  let responseHeadersObj: Record<string, string> = {};

  let parsedRequestBody: unknown = undefined;
  if (typeof body === 'string') {
    try {
      parsedRequestBody = JSON.parse(body);
    } catch {
      parsedRequestBody = body;
    }
  }

  try {
    const response = await fetch(path, {
      ...rest,
      headers,
      body,
    });

    status = response.status;
    const durationMs = Math.round(performance.now() - startTime);

    response.headers.forEach((value, key) => {
      responseHeadersObj[key.toLowerCase()] = value;
    });

    const contentType = response.headers.get('content-type') || '';
    if (contentType.includes('application/json')) {
      responseData = await response.json().catch(() => null);
    } else {
      const text = await response.text().catch(() => '');
      responseData = text ? { message: text } : null;
    }

    if (!skipLog) {
      notifyLog({
        id: crypto.randomUUID(),
        time: formatTimeOnly(new Date()),
        method: (rest.method || 'GET').toUpperCase(),
        path,
        status,
        durationMs,
        idempotencyKey,
        requestBody: parsedRequestBody,
        responseBody: responseData,
        responseHeaders: responseHeadersObj,
      });
    }

    if (!response.ok) {
      const errBody = responseData as ErrorResponse | null;
      const code = errBody?.code || (status >= 500 ? 'INTERNAL_ERROR' : 'ERROR');
      const message = errBody?.message || response.statusText || 'Request failed';
      throw new ApiError(status, code, message, false, responseData);
    }

    return {
      data: responseData as T,
      status,
      headers: response.headers,
    };
  } catch (error) {
    if (error instanceof ApiError) {
      throw error;
    }

    // Network / fetch error
    const durationMs = Math.round(performance.now() - startTime);
    const message = error instanceof Error ? error.message : 'Network error';

    if (!skipLog) {
      notifyLog({
        id: crypto.randomUUID(),
        time: formatTimeOnly(new Date()),
        method: (rest.method || 'GET').toUpperCase(),
        path,
        status: 0,
        durationMs,
        idempotencyKey,
        requestBody: parsedRequestBody,
        responseBody: { error: message },
        responseHeaders: {},
      });
    }

    throw new ApiError(0, 'NETWORK_ERROR', message, true);
  }
}
