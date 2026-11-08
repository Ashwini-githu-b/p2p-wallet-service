import React, { useState, useEffect } from 'react';
import { useDirectory } from '../state/DirectoryContext';
import { apiFetch, ApiError } from '../api/client';
import { TransactionResponse } from '../api/types';
import { parseRupeesToPaise, formatPaise, isValidPaise } from '../lib/money';
import { formatShortId } from '../lib/format';
import { Field } from './Field';
import { Button } from './Button';
import { Receipt } from './Receipt';

interface SendFormProps {
  currentUserId: string;
  currentBalancePaise: number | null | undefined;
  onSuccess: () => void;
}

export const SendForm: React.FC<SendFormProps> = ({
  currentUserId,
  currentBalancePaise,
  onSuccess,
}) => {
  const { accounts } = useDirectory();
  const otherAccounts = accounts.filter(a => a.userId !== currentUserId);

  const [selectedOption, setSelectedOption] = useState<string>(
    otherAccounts.length > 0 ? otherAccounts[0].userId : 'other'
  );
  const [customRecipientId, setCustomRecipientId] = useState('');
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

  // Keep selectedOption valid if accounts change
  useEffect(() => {
    if (selectedOption !== 'other' && !otherAccounts.some(a => a.userId === selectedOption)) {
      if (otherAccounts.length > 0) {
        setSelectedOption(otherAccounts[0].userId);
      } else {
        setSelectedOption('other');
      }
    }
  }, [accounts, currentUserId]);

  const parsedPaise = parseRupeesToPaise(amountStr);
  const isValidAmount = isValidPaise(parsedPaise);

  const effectiveRecipientId =
    selectedOption === 'other' ? customRecipientId.trim() : selectedOption;

  const canSubmit =
    !loading &&
    isValidAmount &&
    effectiveRecipientId !== '' &&
    effectiveRecipientId !== currentUserId;

  const handleFieldChange = (field: 'recipient' | 'custom' | 'amount', value: string) => {
    // Generate fresh key whenever any field changes
    setIdempotencyKey(crypto.randomUUID());
    setErrorMsg(null);
    setErrorCode(null);
    setIsRetryable(false);

    if (field === 'recipient') {
      setSelectedOption(value);
    } else if (field === 'custom') {
      setCustomRecipientId(value);
    } else if (field === 'amount') {
      setAmountStr(value);
    }
  };

  const executeSend = async () => {
    if (!canSubmit || parsedPaise === null) return;

    setLoading(true);
    setErrorMsg(null);
    setErrorCode(null);
    setIsRetryable(false);

    try {
      const res = await apiFetch<TransactionResponse>('/api/v1/transfers', {
        method: 'POST',
        idempotencyKey,
        body: JSON.stringify({
          fromUserId: currentUserId,
          toUserId: effectiveRecipientId,
          amountPaise: parsedPaise,
        }),
      });

      const isReplayed =
        res.status === 200 || res.headers.get('idempotent-replay') === 'true';

      setReceiptData({
        tx: res.data,
        isReplayed,
      });

      onSuccess();
    } catch (err: any) {
      if (err instanceof ApiError) {
        const code = err.code;
        setErrorCode(code);

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
        } else if (code === 'INSUFFICIENT_FUNDS') {
          const availStr =
            currentBalancePaise !== null && currentBalancePaise !== undefined
              ? formatPaise(currentBalancePaise)
              : '—';
          setErrorMsg(`Not enough balance. Available: ${availStr}.`);
        } else if (code === 'INVALID_AMOUNT') {
          setErrorMsg('Amount must be between ₹0.01 and ₹1,00,000.');
        } else if (code === 'SELF_TRANSFER') {
          setErrorMsg('Choose a different recipient.');
        } else if (code === 'USER_NOT_FOUND') {
          setErrorMsg('No account with that ID.');
        } else if (code === 'IDEMPOTENCY_KEY_REUSED') {
          setErrorMsg(
            'This request key was already used with different details. Edit any field to get a fresh key.'
          );
        } else if (code === 'VALIDATION_ERROR') {
          setErrorMsg(err.message || 'Validation error');
        } else {
          setErrorMsg(err.message || 'Transfer failed');
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
    const matchedRecipient = accounts.find(a => a.userId === receiptData.tx.toUserId);
    return (
      <Receipt
        title="Transfer sent"
        transactionId={receiptData.tx.transactionId}
        amountPaise={receiptData.tx.amountPaise}
        recipientLabel={matchedRecipient?.label}
        recipientId={receiptData.tx.toUserId}
        createdAt={receiptData.tx.createdAt}
        isReplayed={receiptData.isReplayed}
        onReset={handleReset}
        resetLabel="New transfer"
      />
    );
  }

  const submitLabel = loading
    ? 'Sending…'
    : isValidAmount && parsedPaise !== null
    ? `Send ${formatPaise(parsedPaise)}`
    : 'Send money';

  return (
    <form
      onSubmit={e => {
        e.preventDefault();
        executeSend();
      }}
    >
      <div className="form-group">
        <label htmlFor="send-recipient-select" className="form-label">
          Recipient
        </label>
        <select
          id="send-recipient-select"
          className="input-select"
          disabled={loading}
          value={selectedOption}
          onChange={e => handleFieldChange('recipient', e.target.value)}
        >
          {otherAccounts.map(a => (
            <option key={a.userId} value={a.userId}>
              {a.label ? `${a.label} (${formatShortId(a.userId)})` : formatShortId(a.userId)}
            </option>
          ))}
          <option value="other">Other (paste user ID)</option>
        </select>
      </div>

      {selectedOption === 'other' && (
        <Field
          id="custom-recipient-id"
          label="Recipient User ID"
          disabled={loading}
          value={customRecipientId}
          placeholder="e.g. 0192e4a8-..."
          onChange={e => handleFieldChange('custom', e.target.value)}
        />
      )}

      <Field
        id="send-amount-input"
        label="Amount"
        isAmount
        disabled={loading}
        value={amountStr}
        placeholder="0.00"
        hint="Up to ₹1,00,000 per transaction"
        onChange={e => handleFieldChange('amount', e.target.value)}
      />

      <div style={{ marginTop: '16px' }}>
        <Button
          type="submit"
          variant="primary"
          disabled={!canSubmit}
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
              onClick={executeSend}
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
