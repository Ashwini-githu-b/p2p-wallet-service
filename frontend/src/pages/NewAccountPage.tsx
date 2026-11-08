import React, { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useDirectory } from '../state/DirectoryContext';
import { apiFetch, ApiError } from '../api/client';
import { UserResponse, WalletResponse } from '../api/types';
import { Field } from '../components/Field';
import { Button } from '../components/Button';
import { formatShortId } from '../lib/format';

export const NewAccountPage: React.FC = () => {
  const navigate = useNavigate();
  const { addAccount } = useDirectory();

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

  return (
    <div style={{ maxWidth: '640px' }}>
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

