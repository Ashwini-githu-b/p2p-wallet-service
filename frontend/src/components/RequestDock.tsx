import React, { useState } from 'react';
import { useRequestLog } from '../state/RequestLogContext';
import { useDirectory } from '../state/DirectoryContext';
import { RequestLogEntry } from '../api/types';
import { Button } from './Button';

export const RequestDock: React.FC = () => {
  const { logs, replay } = useRequestLog();
  const { refreshAllBalances } = useDirectory();
  const [isOpen, setIsOpen] = useState(false);
  const [expandedId, setExpandedId] = useState<string | null>(null);
  const [replayingId, setReplayingId] = useState<string | null>(null);

  const toggleRow = (id: string) => {
    setExpandedId(prev => (prev === id ? null : id));
  };

  const handleReplay = async (e: React.MouseEvent, entry: RequestLogEntry) => {
    e.stopPropagation();
    setReplayingId(entry.id);
    try {
      await replay(entry, () => {
        refreshAllBalances();
      });
    } finally {
      setReplayingId(null);
    }
  };

  return (
    <div className="request-dock">
      <div
        className="dock-header"
        onClick={() => setIsOpen(!isOpen)}
        role="button"
        tabIndex={0}
        onKeyDown={e => {
          if (e.key === 'Enter' || e.key === ' ') {
            setIsOpen(!isOpen);
          }
        }}
        aria-expanded={isOpen}
      >
        <div className="dock-title">
          <span>Requests ({logs.length})</span>
          <svg
            width="12"
            height="12"
            viewBox="0 0 16 16"
            fill="none"
            stroke="currentColor"
            strokeWidth="1.5"
            aria-hidden="true"
          >
            {isOpen ? (
              <path d="M4 10L8 6L12 10" />
            ) : (
              <path d="M4 6L8 10L12 6" />
            )}
          </svg>
        </div>
        <span className="text-ink-2" style={{ fontSize: '12px' }}>
          {isOpen ? 'Click to collapse' : 'Click to expand'}
        </span>
      </div>

      {isOpen && (
        <div className="dock-content">
          {logs.length === 0 ? (
            <div style={{ padding: '16px', color: 'var(--ink-2)' }}>
              No requests recorded yet.
            </div>
          ) : (
            <table className="dock-table">
              <thead>
                <tr>
                  <th>Time</th>
                  <th>Method</th>
                  <th>Path</th>
                  <th>Status</th>
                  <th>ms</th>
                  <th>Idempotency-Key</th>
                  <th>Action</th>
                </tr>
              </thead>
              <tbody>
                {logs.map(log => {
                  const isExpanded = expandedId === log.id;
                  const isErrorStatus = log.status >= 400 || log.status === 0;
                  const statusClass = isErrorStatus ? 'text-debit' : '';

                  return (
                    <React.Fragment key={log.id}>
                      <tr
                        className="dock-row"
                        onClick={() => toggleRow(log.id)}
                      >
                        <td>{log.time}</td>
                        <td>{log.method}</td>
                        <td style={{ wordBreak: 'break-all' }}>{log.path}</td>
                        <td className={statusClass}>
                          {log.status === 0 ? 'ERR' : log.status}
                        </td>
                        <td>{log.durationMs}ms</td>
                        <td>
                          {log.idempotencyKey
                            ? `${log.idempotencyKey.slice(0, 8)}…`
                            : '—'}
                        </td>
                        <td>
                          {log.idempotencyKey && (
                            <Button
                              variant="text"
                              disabled={replayingId === log.id}
                              onClick={e => handleReplay(e, log)}
                            >
                              {replayingId === log.id ? 'Replaying…' : 'Replay'}
                            </Button>
                          )}
                        </td>
                      </tr>
                      {isExpanded && (
                        <tr>
                          <td colSpan={7} style={{ padding: 0 }}>
                            <div className="dock-details">
                              {log.responseHeaders &&
                                Object.keys(log.responseHeaders).length > 0 && (
                                  <div style={{ marginBottom: '8px' }}>
                                    <div className="text-ink-2">
                                      Response Headers:
                                    </div>
                                    <pre>
                                      {JSON.stringify(log.responseHeaders, null, 2)}
                                    </pre>
                                  </div>
                                )}
                              {log.requestBody !== undefined && (
                                <div style={{ marginBottom: '8px' }}>
                                  <div className="text-ink-2">Request Body:</div>
                                  <pre>
                                    {JSON.stringify(log.requestBody, null, 2)}
                                  </pre>
                                </div>
                              )}
                              <div>
                                <div className="text-ink-2">Response Body:</div>
                                <pre>
                                  {JSON.stringify(log.responseBody, null, 2)}
                                </pre>
                              </div>
                            </div>
                          </td>
                        </tr>
                      )}
                    </React.Fragment>
                  );
                })}
              </tbody>
            </table>
          )}
        </div>
      )}
    </div>
  );
};

