import React, { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useDirectory } from '../state/DirectoryContext';
import { apiFetch, ApiError } from '../api/client';
import { UserResponse, WalletResponse, TransactionResponse } from '../api/types';
import { Field } from '../components/Field';
import { Button } from '../components/Button';
import { formatShortId } from '../lib/format';

export const NewAccountPage: React.FC = () => {
  const navigate = useNavigate();
  const { accounts, addAccount } = useDirectory();

  // Create form state
  const [createName, setCreateName] = useState('');
  const [createEmail, setCreateEmail] = useState('');
  const [createLoading, setCreateLoading] = useState(false);
  const [createError, setCreateError] = useState<string | null>(null);

  // Add existing form state
  const [addUserId, setAddUserId] = useState('');
  const [addLabel, setAddLabel] = useState('');
  const [addLoading, setAddLoading] = useState(false);
  const [addError, setAddError] = useState<string | null>(null);

  // Quick start state
  const [quickStartLoading, setQuickStartLoading] = useState(false);
  const [quickStartProgress, setQuickStartProgress] = useState<string | null>(null);
  const [quickStartError, setQuickStartError] = useState<string | null>(null);

  const handleCreateSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!createName.trim() || !createEmail.trim() || createLoading) return;

    setCreateLoading(true);
    setCreateError(null);

    try {
      const res = await apiFetch<UserResponse>('/api/v1/users', {
        method: 'POST',
        body: JSON.stringify({
          name: createName.trim(),
          email: createEmail.trim(),
        }),
      });

      addAccount({
        userId: res.data.userId,
        label: res.data.name,
        email: res.data.email,
        walletId: res.data.walletId,
        addedAt: new Date().toISOString(),
      });

      navigate(`/accounts/${res.data.userId}`);
    } catch (err: any) {
      if (err instanceof ApiError) {
        if (err.code === 'EMAIL_ALREADY_EXISTS') {
          setCreateError('An account with this email already exists.');
        } else {
          setCreateError(err.message || 'Failed to create account.');
        }
      } else {
        setCreateError('An unexpected error occurred.');
      }
    } finally {
      setCreateLoading(false);
    }
  };

  const handleAddExistingSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    const cleanId = addUserId.trim();
    if (!cleanId || addLoading) return;

    setAddLoading(true);
    setAddError(null);

    try {
      const res = await apiFetch<WalletResponse>(`/api/v1/wallets/${cleanId}`);

      addAccount({
        userId: cleanId,
        label: addLabel.trim() || formatShortId(cleanId),
        walletId: res.data.walletId,
        addedAt: new Date().toISOString(),
      });

      navigate(`/accounts/${cleanId}`);
    } catch (err: any) {
      if (err instanceof ApiError) {
        if (err.status === 404 || err.code === 'USER_NOT_FOUND') {
          setAddError('No account with that ID.');
        } else {
          setAddError(err.message || 'Failed to verify account.');
        }
      } else {
        setAddError('An unexpected error occurred.');
      }
    } finally {
      setAddLoading(false);
    }
  };

  const handleQuickStart = async () => {
    setQuickStartLoading(true);
    setQuickStartError(null);

    const demoUsers = ['Asha', 'Ravi', 'Meera'];
    const suffix = Math.random().toString(36).substring(2, 6);
    let firstUserId: string | null = null;

    try {
      for (const name of demoUsers) {
        setQuickStartProgress(`Creating account for ${name}…`);
        const email = `demo-${name.toLowerCase()}-${suffix}@example.com`;

        const userRes = await apiFetch<UserResponse>('/api/v1/users', {
          method: 'POST',
          body: JSON.stringify({ name, email }),
        });

        if (!firstUserId) {
          firstUserId = userRes.data.userId;
        }

        setQuickStartProgress(`Topping up ${name} with ₹10,000.00…`);
        await apiFetch<TransactionResponse>(`/api/v1/wallets/${userRes.data.userId}/topup`, {
          method: 'POST',
          idempotencyKey: crypto.randomUUID(),
          body: JSON.stringify({ amountPaise: 1000000 }), // ₹10,000 = 1,000,000 paise
        });

        addAccount({
          userId: userRes.data.userId,
          label: name,
          email: userRes.data.email,
          walletId: userRes.data.walletId,
          addedAt: new Date().toISOString(),
        });
      }

      if (firstUserId) {
        navigate(`/accounts/${firstUserId}`);
      }
    } catch (err: any) {
      setQuickStartError(err.message || 'Failed to initialize demo accounts.');
    } finally {
      setQuickStartLoading(false);
      setQuickStartProgress(null);
    }
  };

  return (
    <div style={{ maxWidth: '640px' }}>
      {/* Quick start panel when directory is empty */}
      {accounts.length === 0 && (
        <div className="panel" style={{ marginBottom: '24px' }}>
          <h2 className="panel-title">Quick start</h2>
          <p className="text-ink-2" style={{ marginBottom: '16px' }}>
            Set up 3 demo accounts (Asha, Ravi, and Meera) funded with ₹10,000 each to test transfers and explore the ledger immediately.
          </p>

          {quickStartProgress && (
            <p className="text-ink-2 mono" style={{ marginBottom: '12px' }}>
              {quickStartProgress}
            </p>
          )}

          {quickStartError && (
            <div className="text-debit" style={{ marginBottom: '12px', fontSize: '13px' }}>
              {quickStartError}
            </div>
          )}

          <Button
            type="button"
            variant="primary"
            disabled={quickStartLoading}
            onClick={handleQuickStart}
          >
            {quickStartLoading ? 'Setting up accounts…' : 'Create 3 demo accounts'}
          </Button>
          {quickStartError && (
            <Button
              type="button"
              variant="secondary"
              style={{ marginLeft: '8px' }}
              onClick={handleQuickStart}
            >
              Retry
            </Button>
          )}
        </div>
      )}

      {/* Panel 1: New account */}
      <div className="panel">
        <h2 className="panel-title">New account</h2>
        <form onSubmit={handleCreateSubmit}>
          <Field
            id="new-account-name"
            label="Name"
            value={createName}
            disabled={createLoading}
            onChange={e => setCreateName(e.target.value)}
            placeholder="e.g. Alice Sharma"
            required
          />

          <Field
            id="new-account-email"
            label="Email"
            type="email"
            value={createEmail}
            disabled={createLoading}
            onChange={e => setCreateEmail(e.target.value)}
            placeholder="e.g. alice@example.com"
            required
            error={createError}
          />

          <div style={{ marginTop: '16px' }}>
            <Button
              type="submit"
              variant="primary"
              disabled={createLoading || !createName.trim() || !createEmail.trim()}
            >
              {createLoading ? 'Creating…' : 'Create account'}
            </Button>
          </div>
        </form>
      </div>

      {/* Panel 2: Add existing account */}
      <div className="panel">
        <h2 className="panel-title">Add existing account</h2>
        <form onSubmit={handleAddExistingSubmit}>
          <Field
            id="existing-user-id"
            label="User ID"
            value={addUserId}
            disabled={addLoading}
            onChange={e => setAddUserId(e.target.value)}
            placeholder="e.g. 0192e4a8-..."
            required
          />

          <Field
            id="existing-label"
            label="Label (optional)"
            value={addLabel}
            disabled={addLoading}
            onChange={e => setAddLabel(e.target.value)}
            placeholder="e.g. Bob's Wallet"
            error={addError}
          />

          <div style={{ marginTop: '16px' }}>
            <Button
              type="submit"
              variant="primary"
              disabled={addLoading || !addUserId.trim()}
            >
              {addLoading ? 'Verifying…' : 'Add'}
            </Button>
          </div>
        </form>
      </div>
    </div>
  );
};
