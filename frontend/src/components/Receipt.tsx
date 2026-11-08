import React from 'react';
import { CopyText } from './CopyText';
import { Button } from './Button';
import { formatPaise } from '../lib/money';
import { formatLedgerDate } from '../lib/format';

interface ReceiptProps {
  title: string;
  transactionId: string;
  amountPaise: number;
  recipientLabel?: string;
  recipientId?: string;
  createdAt: string;
  isReplayed?: boolean;
  onReset: () => void;
  resetLabel: string;
}

export const Receipt: React.FC<ReceiptProps> = ({
  title,
  transactionId,
  amountPaise,
  recipientLabel,
  recipientId,
  createdAt,
  isReplayed = false,
  onReset,
  resetLabel,
}) => {
  return (
    <div className="receipt-block" aria-live="polite">
      <div className="receipt-title">{title}</div>

      <div className="receipt-row">
        <span className="text-ink-2">Transaction ID</span>
        <CopyText value={transactionId} />
      </div>

      <div className="receipt-row">
        <span className="text-ink-2">Amount</span>
        <span className="mono">{formatPaise(amountPaise)}</span>
      </div>

      {(recipientLabel || recipientId) && (
        <div className="receipt-row">
          <span className="text-ink-2">To</span>
          <span>
            {recipientLabel ? `${recipientLabel} ` : ''}
            {recipientId && <CopyText value={recipientId} />}
          </span>
        </div>
      )}

      <div className="receipt-row">
        <span className="text-ink-2">Time</span>
        <span className="mono">{formatLedgerDate(createdAt)}</span>
      </div>

      {isReplayed && (
        <div className="receipt-replay-note">
          Replayed: this request key was already processed. No money moved again.
        </div>
      )}

      <div style={{ marginTop: '16px' }}>
        <Button variant="secondary" onClick={onReset}>
          {resetLabel}
        </Button>
      </div>
    </div>
  );
};

