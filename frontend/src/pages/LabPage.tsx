import React, { useState, useEffect } from 'react';
import { Link } from 'react-router-dom';
import { useDirectory } from '../state/DirectoryContext';
import { apiFetch } from '../api/client';
import { WalletResponse, TransactionResponse, ReconciliationResponse } from '../api/types';
import { parseRupeesToPaise, formatPaise, isValidPaise } from '../lib/money';
import { formatShortId } from '../lib/format';
import { Field } from '../components/Field';
import { Button } from '../components/Button';

interface ConcurrentResults {
  elapsedMs: number;
  count201: number;
  count422: number;
  countOther: number;
  senderBefore: number;
  senderAfter: number;
  receiverBefore: number;
  receiverAfter: number;
  moneyConserved: boolean;
  expectedSuccesses: number;
  actualSuccesses: number;
}

interface ReplayResults {
  elapsedMs: number;
  count201: number;
  count200: number;
  countOther: number;
  distinctTxIds: string[];
  senderBefore: number;
  senderAfter: number;
  receiverBefore: number;
  receiverAfter: number;
  balanceMoved: number;
  expectedAmount: number;
}

export const LabPage: React.FC = () => {
  const { accounts, balances, refreshAllBalances } = useDirectory();

  const [activeTab, setActiveTab] = useState<'concurrent' | 'replay'>('concurrent');
  const [senderId, setSenderId] = useState<string>('');
  const [receiverId, setReceiverId] = useState<string>('');
  const [countN, setCountN] = useState<number>(20);
  const [amountStr, setAmountStr] = useState<string>('1.00');

  const [running, setRunning] = useState(false);
  const [concurrentResults, setConcurrentResults] = useState<ConcurrentResults | null>(null);
  const [replayResults, setReplayResults] = useState<ReplayResults | null>(null);

  const [reconRun, setReconRun] = useState<ReconciliationResponse | null>(null);
  const [reconLoading, setReconLoading] = useState<boolean>(true);

  useEffect(() => {
    let mounted = true;
    apiFetch<ReconciliationResponse>('/api/v1/admin/reconciliation/latest', { skipLog: true })
      .then(res => {
        if (mounted && res.status === 200 && res.data) {
          setReconRun(res.data);
        }
      })
      .catch(() => {})
      .finally(() => {
        if (mounted) setReconLoading(false);
      });
    return () => {
      mounted = false;
    };
  }, []);

  // Initialize sender & receiver once accounts are loaded
  useEffect(() => {
    if (accounts.length >= 2) {
      if (!senderId || !accounts.some(a => a.userId === senderId)) {
        setSenderId(accounts[0].userId);
      }
      if (!receiverId || receiverId === senderId || !accounts.some(a => a.userId === receiverId)) {
        const other = accounts.find(a => a.userId !== accounts[0].userId);
        if (other) {
          setReceiverId(other.userId);
        }
      }
    }
  }, [accounts]);

  // When sender changes, update receiver if same
  const handleSenderChange = (newSender: string) => {
    setSenderId(newSender);
    if (newSender === receiverId) {
      const other = accounts.find(a => a.userId !== newSender);
      if (other) {
        setReceiverId(other.userId);
      }
    }
  };

  const parsedPaise = parseRupeesToPaise(amountStr);
  const isValidAmount = isValidPaise(parsedPaise);
  const isValidN = Number.isInteger(countN) && countN >= 1 && countN <= 100;
  const canRun = !running && isValidAmount && isValidN && senderId && receiverId && senderId !== receiverId;

  const senderBalance = senderId ? balances[senderId] : null;

  // Run Concurrent Transfers
  const runConcurrentTransfers = async () => {
    if (!canRun || parsedPaise === null) return;
    setRunning(true);
    setConcurrentResults(null);

    try {
      const [sBeforeRes, rBeforeRes] = await Promise.all([
        apiFetch<WalletResponse>(`/api/v1/wallets/${senderId}`),
        apiFetch<WalletResponse>(`/api/v1/wallets/${receiverId}`),
      ]);

      const sBefore = sBeforeRes.data.balancePaise;
      const rBefore = rBeforeRes.data.balancePaise;

      const t0 = performance.now();

      const promises = Array.from({ length: countN }, () =>
        apiFetch<TransactionResponse>('/api/v1/transfers', {
          method: 'POST',
          idempotencyKey: crypto.randomUUID(),
          body: JSON.stringify({
            fromUserId: senderId,
            toUserId: receiverId,
            amountPaise: parsedPaise,
          }),
        }).then(
          res => ({ success: true, status: res.status }),
          err => ({ success: false, status: err.status, code: err.code })
        )
      );

      const results = await Promise.all(promises);
      const elapsedMs = Math.round(performance.now() - t0);

      const [sAfterRes, rAfterRes] = await Promise.all([
        apiFetch<WalletResponse>(`/api/v1/wallets/${senderId}`),
        apiFetch<WalletResponse>(`/api/v1/wallets/${receiverId}`),
      ]);

      const sAfter = sAfterRes.data.balancePaise;
      const rAfter = rAfterRes.data.balancePaise;

      const count201 = results.filter(r => r.status === 201).length;
      const count422 = results.filter(
        r => r.status === 422 && (r as any).code === 'INSUFFICIENT_FUNDS'
      ).length;
      const countOther = results.length - count201 - count422;

      const totalBefore = sBefore + rBefore;
      const totalAfter = sAfter + rAfter;
      const moneyConserved = totalBefore === totalAfter;

      const expectedSuccesses = Math.min(Math.floor(sBefore / parsedPaise), countN);

      setConcurrentResults({
        elapsedMs,
        count201,
        count422,
        countOther,
        senderBefore: sBefore,
        senderAfter: sAfter,
        receiverBefore: rBefore,
        receiverAfter: rAfter,
        moneyConserved,
        expectedSuccesses,
        actualSuccesses: count201,
      });

      refreshAllBalances();
    } finally {
      setRunning(false);
    }
  };

  // Run Same Key Replay
  const runSameKeyReplay = async () => {
    if (!canRun || parsedPaise === null) return;
    setRunning(true);
    setReplayResults(null);

    try {
      const [sBeforeRes, rBeforeRes] = await Promise.all([
        apiFetch<WalletResponse>(`/api/v1/wallets/${senderId}`),
        apiFetch<WalletResponse>(`/api/v1/wallets/${receiverId}`),
      ]);

      const sBefore = sBeforeRes.data.balancePaise;
      const rBefore = rBeforeRes.data.balancePaise;

      const sharedKey = crypto.randomUUID();
      const t0 = performance.now();

      const promises = Array.from({ length: countN }, () =>
        apiFetch<TransactionResponse>('/api/v1/transfers', {
          method: 'POST',
          idempotencyKey: sharedKey,
          body: JSON.stringify({
            fromUserId: senderId,
            toUserId: receiverId,
            amountPaise: parsedPaise,
          }),
        }).then(
          res => ({
            success: true,
            status: res.status,
            txId: res.data?.transactionId,
            isReplay:
              res.status === 200 || res.headers.get('idempotent-replay') === 'true',
          }),
          err => ({ success: false, status: err.status, code: err.code })
        )
      );

      const results = await Promise.all(promises);
      const elapsedMs = Math.round(performance.now() - t0);

      const [sAfterRes, rAfterRes] = await Promise.all([
        apiFetch<WalletResponse>(`/api/v1/wallets/${senderId}`),
        apiFetch<WalletResponse>(`/api/v1/wallets/${receiverId}`),
      ]);

      const sAfter = sAfterRes.data.balancePaise;
      const rAfter = rAfterRes.data.balancePaise;

      const count201 = results.filter(r => r.status === 201).length;
      const count200 = results.filter(
        r => r.status === 200 || (r as any).isReplay
      ).length;
      const countOther = results.length - count201 - count200;

      const txIds = Array.from(
        new Set(
          results
            .map(r => (r as any).txId)
            .filter((id): id is string => Boolean(id))
        )
      );

      const balanceMoved = sBefore - sAfter;

      setReplayResults({
        elapsedMs,
        count201,
        count200,
        countOther,
        distinctTxIds: txIds,
        senderBefore: sBefore,
        senderAfter: sAfter,
        receiverBefore: rBefore,
        receiverAfter: rAfter,
        balanceMoved,
        expectedAmount: parsedPaise,
      });

      refreshAllBalances();
    } finally {
      setRunning(false);
    }
  };

  const renderLedgerHealthPanel = () => {
    return (
      <div className="panel" style={{ marginBottom: '24px' }}>
        <h2 className="panel-title">Ledger health</h2>
        {reconLoading ? (
          <p className="text-ink-2">Loading…</p>
        ) : !reconRun ? (
          <p className="text-ink-2">Not checked yet.</p>
        ) : (
          <div>
            <table className="ledger-table" style={{ marginBottom: reconRun.discrepancies.length > 0 ? '16px' : '0' }}>
              <thead>
                <tr>
                  <th>Field</th>
                  <th>Value</th>
                </tr>
              </thead>
              <tbody>
                <tr>
                  <td>Status</td>
                  <td className={reconRun.status === 'OK' ? 'text-credit' : 'text-debit'}>
                    <strong>{reconRun.status}</strong>
                  </td>
                </tr>
                <tr>
                  <td>Checked at</td>
                  <td className="mono">{new Date(reconRun.finishedAt || reconRun.startedAt).toLocaleString()}</td>
                </tr>
                <tr>
                  <td>Wallets checked</td>
                  <td className="mono">{reconRun.walletsChecked}</td>
                </tr>
                <tr>
                  <td>Transactions checked</td>
                  <td className="mono">{reconRun.transactionsChecked}</td>
                </tr>
                <tr>
                  <td>Discrepancies</td>
                  <td className={`mono ${reconRun.discrepancyCount > 0 ? 'text-debit' : 'text-credit'}`}>
                    {reconRun.discrepancyCount}
                  </td>
                </tr>
                <tr>
                  <td>Total balance</td>
                  <td className="mono">{formatPaise(reconRun.totalBalancePaise)}</td>
                </tr>
              </tbody>
            </table>

            {reconRun.discrepancies.length > 0 && (
              <div>
                <h3 className="section-title" style={{ marginTop: '16px', marginBottom: '8px' }}>Discrepancies</h3>
                <table className="ledger-table">
                  <thead>
                    <tr>
                      <th>Kind</th>
                      <th>Ref</th>
                      <th>Expected</th>
                      <th>Actual</th>
                    </tr>
                  </thead>
                  <tbody>
                    {reconRun.discrepancies.map((d, idx) => (
                      <tr key={idx}>
                        <td className="mono text-debit">{d.kind}</td>
                        <td className="mono">{formatShortId(d.ref)}</td>
                        <td className="mono">{formatPaise(d.expectedPaise)}</td>
                        <td className="mono">{formatPaise(d.actualPaise)}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
          </div>
        )}
      </div>
    );
  };

  if (accounts.length < 2) {
    return (
      <div style={{ maxWidth: '800px' }}>
        {renderLedgerHealthPanel()}
        <div className="panel">
          <h1 className="panel-title">Lab: Concurrency Check</h1>
          <p style={{ marginBottom: '16px' }}>
            You need at least two accounts in the directory to run the Lab concurrency tests.
          </p>
          <Link to="/accounts/new" className="btn btn-primary">
            Create or add accounts
          </Link>
        </div>
      </div>
    );
  }

  return (
    <div style={{ maxWidth: '800px' }}>
      {renderLedgerHealthPanel()}
      <div className="panel">
        <h1 className="panel-title">Lab: Concurrency Check</h1>
        <p className="text-ink-2" style={{ marginBottom: '24px' }}>
          Evaluate the backend's transactional guarantees live under concurrent loads and duplicate request keys.
        </p>

        {/* Global Controls */}
        <div className="lab-grid">
          <div className="form-group">
            <label htmlFor="lab-sender" className="form-label">
              Sender
            </label>
            <select
              id="lab-sender"
              className="input-select"
              disabled={running}
              value={senderId}
              onChange={e => handleSenderChange(e.target.value)}
            >
              {accounts.map(a => (
                <option key={a.userId} value={a.userId}>
                  {a.label || formatShortId(a.userId)}
                </option>
              ))}
            </select>
          </div>

          <div className="form-group">
            <label htmlFor="lab-receiver" className="form-label">
              Receiver
            </label>
            <select
              id="lab-receiver"
              className="input-select"
              disabled={running}
              value={receiverId}
              onChange={e => setReceiverId(e.target.value)}
            >
              {accounts
                .filter(a => a.userId !== senderId)
                .map(a => (
                  <option key={a.userId} value={a.userId}>
                    {a.label || formatShortId(a.userId)}
                  </option>
                ))}
            </select>
          </div>

          <Field
            id="lab-count"
            label="Count N (1..100)"
            type="number"
            min={1}
            max={100}
            disabled={running}
            value={countN}
            onChange={e => setCountN(parseInt(e.target.value, 10) || 0)}
          />

          <Field
            id="lab-amount"
            label="Amount per request"
            isAmount
            disabled={running}
            value={amountStr}
            onChange={e => setAmountStr(e.target.value)}
          />
        </div>

        {senderBalance === 0 && (
          <div
            className="text-debit"
            style={{ marginBottom: '16px', fontSize: '13px' }}
          >
            Warning: sender balance is ₹0.00. Transfers will fail with INSUFFICIENT_FUNDS.
          </div>
        )}

        {/* Tabs */}
        <div className="tabs-header" role="tablist">
          <button
            type="button"
            role="tab"
            id="tab-concurrent"
            aria-controls="panel-concurrent"
            aria-selected={activeTab === 'concurrent'}
            className={`tab-btn ${activeTab === 'concurrent' ? 'active' : ''}`}
            onClick={() => setActiveTab('concurrent')}
          >
            Concurrent transfers
          </button>
          <button
            type="button"
            role="tab"
            id="tab-replay"
            aria-controls="panel-replay"
            aria-selected={activeTab === 'replay'}
            className={`tab-btn ${activeTab === 'replay' ? 'active' : ''}`}
            onClick={() => setActiveTab('replay')}
          >
            Same key replay
          </button>
        </div>

        {/* Tab 1: Concurrent transfers */}
        <div
          id="panel-concurrent"
          role="tabpanel"
          aria-labelledby="tab-concurrent"
          hidden={activeTab !== 'concurrent'}
        >
          {activeTab === 'concurrent' && (
            <div>
              <p style={{ marginBottom: '16px', fontSize: '13px' }}>
                Fires N transfers in parallel with Promise.all, each using its own unique idempotency key.
              </p>

              <Button
                type="button"
                variant="primary"
                disabled={!canRun}
                onClick={runConcurrentTransfers}
              >
                {running ? 'Firing transfers…' : `Fire ${countN} transfers at once`}
              </Button>

              {concurrentResults && (
                <div className="lab-results">
                  <table className="ledger-table" style={{ marginBottom: '16px' }}>
                    <thead>
                      <tr>
                        <th>Metric</th>
                        <th>Value</th>
                      </tr>
                    </thead>
                    <tbody>
                      <tr>
                        <td>Total requests</td>
                        <td className="mono">{countN} (settled in {concurrentResults.elapsedMs}ms)</td>
                      </tr>
                      <tr>
                        <td>201 Created</td>
                        <td className="mono text-credit">{concurrentResults.count201}</td>
                      </tr>
                      <tr>
                        <td>422 INSUFFICIENT_FUNDS</td>
                        <td className="mono text-debit">{concurrentResults.count422}</td>
                      </tr>
                      {concurrentResults.countOther > 0 && (
                        <tr>
                          <td>Other responses</td>
                          <td className="mono">{concurrentResults.countOther}</td>
                        </tr>
                      )}
                      <tr>
                        <td>Sender balance</td>
                        <td className="mono">
                          {formatPaise(concurrentResults.senderBefore)} → {formatPaise(concurrentResults.senderAfter)}
                        </td>
                      </tr>
                      <tr>
                        <td>Receiver balance</td>
                        <td className="mono">
                          {formatPaise(concurrentResults.receiverBefore)} → {formatPaise(concurrentResults.receiverAfter)}
                        </td>
                      </tr>
                      <tr>
                        <td>Money conserved (sender + receiver unchanged in total)</td>
                        <td className={concurrentResults.moneyConserved ? 'text-credit' : 'text-debit'}>
                          <strong>{concurrentResults.moneyConserved ? 'Yes' : 'NO'}</strong>
                        </td>
                      </tr>
                      <tr>
                        <td>Expected successes: floor(balance ÷ amount) capped at N</td>
                        <td className="mono">
                          Expected {concurrentResults.expectedSuccesses}, Actual {concurrentResults.actualSuccesses}
                        </td>
                      </tr>
                    </tbody>
                  </table>
                </div>
              )}
            </div>
          )}
        </div>

        {/* Tab 2: Same key replay */}
        <div
          id="panel-replay"
          role="tabpanel"
          aria-labelledby="tab-replay"
          hidden={activeTab !== 'replay'}
        >
          {activeTab === 'replay' && (
            <div>
              <p style={{ marginBottom: '16px', fontSize: '13px' }}>
                Sends N identical requests with the SAME idempotency key simultaneously. Only 1 should create a transfer; the rest must be replayed or rejected without moving additional money.
              </p>

              <Button
                type="button"
                variant="primary"
                disabled={!canRun}
                onClick={runSameKeyReplay}
              >
                {running ? 'Replaying…' : `Send the same request ${countN} times`}
              </Button>

              {replayResults && (
                <div className="lab-results">
                  <table className="ledger-table" style={{ marginBottom: '16px' }}>
                    <thead>
                      <tr>
                        <th>Metric</th>
                        <th>Value</th>
                      </tr>
                    </thead>
                    <tbody>
                      <tr>
                        <td>Total requests</td>
                        <td className="mono">{countN} (settled in {replayResults.elapsedMs}ms)</td>
                      </tr>
                      <tr>
                        <td>201 Created</td>
                        <td className="mono text-credit">{replayResults.count201}</td>
                      </tr>
                      <tr>
                        <td>200 Replayed</td>
                        <td className="mono">{replayResults.count200}</td>
                      </tr>
                      {replayResults.countOther > 0 && (
                        <tr>
                          <td>Other responses</td>
                          <td className="mono text-debit">{replayResults.countOther}</td>
                        </tr>
                      )}
                      <tr>
                        <td>Distinct transaction IDs (expect 1)</td>
                        <td className="mono">
                          {replayResults.distinctTxIds.length} {replayResults.distinctTxIds.length > 0 && `(${replayResults.distinctTxIds.join(', ')})`}
                        </td>
                      </tr>
                      <tr>
                        <td>Balance change (sender moved)</td>
                        <td className="mono">
                          {formatPaise(replayResults.balanceMoved)} (expected {formatPaise(replayResults.expectedAmount)})
                        </td>
                      </tr>
                    </tbody>
                  </table>
                </div>
              )}
            </div>
          )}
        </div>
      </div>
    </div>
  );
};

