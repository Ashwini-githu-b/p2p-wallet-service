import React, { useState, useEffect } from 'react';
import { NavLink } from 'react-router-dom';
import { apiFetch } from '../api/client';
import { ReconciliationResponse } from '../api/types';

export const TopBar: React.FC = () => {
  const [apiUp, setApiUp] = useState<boolean | null>(null);
  const [reconState, setReconState] = useState<{
    status: 'OK' | 'MISMATCH' | 'NOT_CHECKED' | 'UNKNOWN';
    checkedAgo?: string;
    discrepancyCount?: number;
  }>({ status: 'UNKNOWN' });

  useEffect(() => {
    let mounted = true;

    const checkHealth = async () => {
      try {
        const res = await apiFetch<{ status: string }>('/actuator/health', {
          skipLog: true,
        });
        if (mounted) {
          setApiUp(res.data?.status === 'UP');
        }
      } catch {
        if (mounted) {
          setApiUp(false);
        }
      }
    };

    checkHealth();
    const interval = setInterval(checkHealth, 15000);
    return () => {
      mounted = false;
      clearInterval(interval);
    };
  }, []);

  useEffect(() => {
    let mounted = true;

    const formatCheckedAgo = (isoString: string) => {
      const diffSec = Math.max(0, Math.floor((Date.now() - new Date(isoString).getTime()) / 1000));
      if (diffSec < 60) return `${diffSec}s ago`;
      const diffMin = Math.floor(diffSec / 60);
      if (diffMin < 60) return `${diffMin}m ago`;
      const diffHours = Math.floor(diffMin / 60);
      return `${diffHours}h ago`;
    };

    const checkRecon = async () => {
      try {
        const res = await apiFetch<ReconciliationResponse>('/api/v1/admin/reconciliation/latest', {
          skipLog: true,
        });
        if (!mounted) return;

        if (res.status === 200 && res.data) {
          const ago = formatCheckedAgo(res.data.finishedAt || res.data.startedAt);
          if (res.data.status === 'OK') {
            setReconState({ status: 'OK', checkedAgo: ago });
          } else {
            setReconState({
              status: 'MISMATCH',
              discrepancyCount: res.data.discrepancyCount,
            });
          }
        } else if (res.status === 404) {
          setReconState({ status: 'NOT_CHECKED' });
        } else {
          setReconState({ status: 'UNKNOWN' });
        }
      } catch {
        if (mounted) {
          setReconState({ status: 'UNKNOWN' });
        }
      }
    };

    checkRecon();
    const interval = setInterval(checkRecon, 30000);
    return () => {
      mounted = false;
      clearInterval(interval);
    };
  }, []);

  return (
    <header className="top-bar">
      <div className="top-bar-left">
        <NavLink to="/" className="wordmark">
          Wallet Console
        </NavLink>
        <nav className="nav-links">
          <NavLink
            to="/"
            className={({ isActive }) =>
              `nav-link ${isActive && !window.location.pathname.startsWith('/lab') ? 'active' : ''}`
            }
          >
            Accounts
          </NavLink>
          <NavLink
            to="/lab"
            className={({ isActive }) => `nav-link ${isActive ? 'active' : ''}`}
          >
            Lab
          </NavLink>
          <a
            href="/swagger-ui.html"
            target="_blank"
            rel="noreferrer"
            className="nav-link"
          >
            API docs
          </a>
        </nav>
      </div>

      <div style={{ display: 'flex', gap: 'var(--space-16)', alignItems: 'center' }}>
        <div className="api-status">
          <span
            className={`status-dot ${apiUp === true ? 'up' : 'unreachable'}`}
            aria-hidden="true"
          />
          <span>{apiUp === true ? 'API: up' : 'API: unreachable'}</span>
        </div>

        <div className="api-status">
          <span
            className={`status-dot ${
              reconState.status === 'OK'
                ? 'up'
                : reconState.status === 'MISMATCH'
                ? 'unreachable'
                : 'ink-2'
            }`}
            aria-hidden="true"
          />
          <span>
            {reconState.status === 'OK' && `Ledger: balanced · checked ${reconState.checkedAgo}`}
            {reconState.status === 'MISMATCH' && `Ledger: MISMATCH (${reconState.discrepancyCount ?? 0})`}
            {reconState.status === 'NOT_CHECKED' && 'Ledger: not checked yet'}
            {reconState.status === 'UNKNOWN' && 'Ledger: unknown'}
          </span>
        </div>
      </div>
    </header>
  );
};
