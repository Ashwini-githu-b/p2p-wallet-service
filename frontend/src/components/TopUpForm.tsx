import React, { useState } from 'react';
import { apiFetch, ApiError } from '../api/client';
import { TransactionResponse } from '../api/types';
import { parseRupeesToPaise, formatPaise, isValidPaise } from '../lib/money';
import { Field } from './Field';
import { Button } from './Button';
import { Receipt } from './Receipt';

interface TopUpFormProps {
  currentUserId: string;
  onSuccess: () => void;
}

export const TopUpForm: React.FC<TopUpFormProps> = ({
  currentUserId,
  onSuccess,
}) => {
  const [amountStr, setAmountStr] = useState('');
  const [idempotencyKey, setIdempotencyKey] = useState<string>(() => crypto.randomUUID());
  const [loading, setLoading] = useState(false);
  const [errorMsg, setErrorMsg] = useState<string | null>(null);
  const [errorCode, setErrorCode] = useState<string | null>(null);
  const [isRetryable, setIsRetryable] = useState(false);
  const [receiptData, setReceiptData] = useState<{
    tx: TransactionResponse;
    isReplayed: boolean;
  } | null>(null);

  const parsedPaise = parseRupeesToPaise(amountStr);
  const isValidAmount = isValidPaise(parsedPaise);

  const handleAmountChange = (val: string) => {
    setIdempotencyKey(crypto.randomUUID());
    setErrorMsg(null);
    setErrorCode(null);
    setIsRetryable(false);
    setAmountStr(val);
  };

  const setQuickFill = (val: string) => {
    handleAmountChange(val);
  };

  const executeTopUp = async () => {
    if (!isValidAmount || parsedPaise === null || loading) return;

    setLoading(true);
    setErrorMsg(null);
    setErrorCode(null);
    setIsRetryable(false);

    try {
      const res = await apiFetch<TransactionResponse>(
        `/api/v1/wallets/${currentUserId}/topup`,
        {
          method: 'POST',
          idempotencyKey,
          body: JSON.stringify({
            amountPaise: parsedPaise,
          }),
        }
      );

      const isReplayed =
        res.status === 200 || res.headers.get('idempotent-replay') === 'true';

      setReceiptData({
        tx: res.data,
        isReplayed,
      });

      onSuccess();
    } catch (err: any) {
      if (err instanceof ApiError) {
        setErrorCode(err.code);

        if (err.isNetworkError) {
          setErrorMsg(
            "Can't reach the server. Retrying is safe: the same request key will be used."
          );
          setIsRetryable(true);
        } else if (err.status >= 500) {
          setErrorMsg(
            'Server error. Retrying is safe: the same request key will be used.'
          );
          setIsRetryable(true);
        } else if (err.code === 'INVALID_AMOUNT') {
          setErrorMsg('Amount must be between ₹0.01 and ₹1,00,000.');
        } else if (err.code === 'IDEMPOTENCY_KEY_REUSED') {
          setErrorMsg(
            'This request key was already used with different details. Edit any field to get a fresh key.'
          );
        } else if (err.code === 'VALIDATION_ERROR') {
          setErrorMsg(err.message || 'Validation error');
        } else {
          setErrorMsg(err.message || 'Top-up failed');
        }
      } else {
        setErrorMsg('An unexpected error occurred.');
      }
    } finally {
      setLoading(false);
    }
  };

  const handleReset = () => {
    setReceiptData(null);
    setAmountStr('');
    setIdempotencyKey(crypto.randomUUID());
    setErrorMsg(null);
    setErrorCode(null);
    setIsRetryable(false);
  };

  if (receiptData) {
    return (
      <Receipt
        title="Top-up completed"
        transactionId={receiptData.tx.transactionId}
        amountPaise={receiptData.tx.amountPaise}
        createdAt={receiptData.tx.createdAt}
        isReplayed={receiptData.isReplayed}
        onReset={handleReset}
        resetLabel="New top-up"
      />
    );
  }

  const submitLabel = loading
    ? 'Adding…'
    : isValidAmount && parsedPaise !== null
    ? `Add ${formatPaise(parsedPaise)}`
    : 'Add money';

  return (
    <form
      onSubmit={e => {
        e.preventDefault();
        executeTopUp();
      }}
    >
      <div className="quick-fills">
        <button
          type="button"
          className="btn-text"
          disabled={loading}
          onClick={() => setQuickFill('500')}
        >
          ₹500
        </button>
        <button
          type="button"
          className="btn-text"
          disabled={loading}
          onClick={() => setQuickFill('1000')}
        >
          ₹1,000
        </button>
        <button
          type="button"
          className="btn-text"
          disabled={loading}
          onClick={() => setQuickFill('5000')}
        >
          ₹5,000
        </button>
      </div>

      <Field
        id="topup-amount-input"
        label="Amount"
        isAmount
        disabled={loading}
        value={amountStr}
        placeholder="0.00"
        hint="Up to ₹1,00,000 per transaction"
        onChange={e => handleAmountChange(e.target.value)}
      />

      <div style={{ marginTop: '16px' }}>
        <Button
          type="submit"
          variant="primary"
          disabled={!isValidAmount || loading}
          style={{ width: '100%' }}
        >
          {submitLabel}
        </Button>

        {isRetryable && (
          <div style={{ marginTop: '8px' }}>
            <Button
              type="button"
              variant="secondary"
              disabled={loading}
              onClick={executeTopUp}
              style={{ width: '100%' }}
            >
              Retry
            </Button>
          </div>
        )}

        <div className="key-note">
          Request key {idempotencyKey.slice(0, 8)}…
        </div>

        <div aria-live="polite">
          {errorMsg && (
            <div className="form-error">
              {errorMsg}
              {errorCode && <div className="form-error-code">Code: {errorCode}</div>}
            </div>
          )}
        </div>
      </div>
    </form>
  );
};

