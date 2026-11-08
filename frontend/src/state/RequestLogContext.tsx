import React, { createContext, useContext, useState, useEffect, ReactNode } from 'react';
import { RequestLogEntry } from '../api/types';
import { subscribeRequestLog, apiFetch } from '../api/client';

interface RequestLogContextValue {
  logs: RequestLogEntry[];
  replay: (entry: RequestLogEntry, onSettled?: () => void) => Promise<void>;
  clearLogs: () => void;
}

const RequestLogContext = createContext<RequestLogContextValue | null>(null);

export const RequestLogProvider: React.FC<{ children: ReactNode }> = ({ children }) => {
  const [logs, setLogs] = useState<RequestLogEntry[]>([]);

  useEffect(() => {
    const unsubscribe = subscribeRequestLog(newEntry => {
      setLogs(prev => [newEntry, ...prev].slice(0, 50));
    });
    return unsubscribe;
  }, []);

  const clearLogs = () => setLogs([]);

  const replay = async (entry: RequestLogEntry, onSettled?: () => void) => {
    try {
      await apiFetch(entry.path, {
        method: entry.method,
        body: entry.requestBody ? JSON.stringify(entry.requestBody) : undefined,
        idempotencyKey: entry.idempotencyKey,
      });
    } catch {
      // Replay errors are logged into RequestLog via apiFetch
    } finally {
      if (onSettled) {
        onSettled();
      }
    }
  };

  return (
    <RequestLogContext.Provider value={{ logs, replay, clearLogs }}>
      {children}
    </RequestLogContext.Provider>
  );
};

export function useRequestLog(): RequestLogContextValue {
  const ctx = useContext(RequestLogContext);
  if (!ctx) {
    throw new Error('useRequestLog must be used within RequestLogProvider');
  }
  return ctx;
}

