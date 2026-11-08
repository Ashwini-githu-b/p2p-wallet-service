import React, { useState, useEffect, useCallback } from 'react';
import { useDirectory } from '../state/DirectoryContext';
import { apiFetch } from '../api/client';
import { PageResponse, LedgerItem } from '../api/types';
import { formatPaise } from '../lib/money';
import { formatLedgerDate, formatShortId } from '../lib/format';
import { Button } from './Button';

interface LedgerProps {
  userId: string;
  refreshTrigger?: number;
}

export const Ledger: React.FC<LedgerProps> = ({ userId, refreshTrigger }) => {
  const { accounts } = useDirectory();
  const [items, setItems] = useState<LedgerItem[]>([]);
  const [page, setPage] = useState(0);
  const [totalElements, setTotalElements] = useState(0);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(false);

  const fetchTransactions = useCallback(
    async (targetPage: number) => {
      setLoading(true);
      setError(false);
      try {
        const res = await apiFetch<PageResponse<LedgerItem>>(
          `/api/v1/wallets/${userId}/transactions?page=${targetPage}&size=20`
        );
        setItems(res.data.items);
        setPage(res.data.page);
        setTotalElements(res.data.totalElements);
      } catch {
        setError(true);
      } finally {
        setLoading(false);
      }
    },
    [userId]
  );

  useEffect(() => {
    setPage(0);
    fetchTransactions(0);
  }, [userId, refreshTrigger, fetchTransactions]);

  const handlePrev = () => {
    if (page > 0) {
      fetchTransactions(page - 1);
    }
  };

  const handleNext = () => {
    if ((page + 1) * 20 < totalElements) {
      fetchTransactions(page + 1);
    }
  };

  return (
    <div className="panel">
      <div className="panel-title">Ledger</div>

      {loading && items.length === 0 ? (
        <div className="text-ink-2">Loading…</div>
      ) : error ? (
        <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
          <span>Couldn't load transactions.</span>
          <Button
            variant="text"
            onClick={() => fetchTransactions(page)}
          >
            Retry
          </Button>
        </div>
      ) : items.length === 0 ? (
        <div>No transactions yet.</div>
      ) : (
        <>
          <table className="ledger-table">
            <thead>
              <tr>
                <th>Time</th>
                <th>Type</th>
                <th>Counterparty</th>
                <th className="text-right">Amount</th>
                <th className="text-right">Balance after</th>
              </tr>
            </thead>
            <tbody>
              {items.map(item => {
                let typeLabel = '';
                if (item.type === 'TOPUP') {
                  typeLabel = 'Top-up';
                } else if (item.direction === 'DEBIT') {
                  typeLabel = 'Sent';
                } else {
                  typeLabel = 'Received';
                }

                let counterpartyDisplay: React.ReactNode = '—';
                if (item.counterpartyUserId) {
                  const match = accounts.find(
                    a => a.userId === item.counterpartyUserId
                  );
                  if (match && match.label) {
                    counterpartyDisplay = (
                      <span title={item.counterpartyUserId}>
                        {match.label}
                      </span>
                    );
                  } else {
                    counterpartyDisplay = (
                      <span className="mono" title={item.counterpartyUserId}>
                        {formatShortId(item.counterpartyUserId)}
                      </span>
                    );
                  }
                }

                const isCredit = item.direction === 'CREDIT';
                const sign = isCredit ? '+' : '\u2212';
                const amountClass = isCredit ? 'text-credit' : 'text-debit';

                return (
                  <tr key={item.transactionId}>
                    <td className="mono">{formatLedgerDate(item.createdAt)}</td>
                    <td>{typeLabel}</td>
                    <td>{counterpartyDisplay}</td>
                    <td className={`mono text-right ${amountClass}`}>
                      {sign}
                      {formatPaise(item.amountPaise)}
                    </td>
                    <td className="mono text-right">
                      {formatPaise(item.balanceAfterPaise)}
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>

          <div className="ledger-footer">
            <div>
              {page * 20 + 1}–{Math.min((page + 1) * 20, totalElements)} of {totalElements}
            </div>
            <div className="ledger-pagination-btns">
              <Button
                variant="secondary"
                disabled={page === 0 || loading}
                onClick={handlePrev}
              >
                Previous
              </Button>
              <Button
                variant="secondary"
                disabled={(page + 1) * 20 >= totalElements || loading}
                onClick={handleNext}
              >
                Next
              </Button>
            </div>
          </div>
        </>
      )}
    </div>
  );
};

