import React, { useState, useEffect, useCallback } from 'react';
import { useParams } from 'react-router-dom';
import { useDirectory } from '../state/DirectoryContext';
import { apiFetch } from '../api/client';
import { WalletResponse } from '../api/types';
import { CopyText } from '../components/CopyText';
import { BalanceBlock } from '../components/BalanceBlock';
import { Ledger } from '../components/Ledger';
import { SendForm } from '../components/SendForm';
import { TopUpForm } from '../components/TopUpForm';

export const WalletPage: React.FC = () => {
  const { userId } = useParams<{ userId: string }>();
  const { accounts, balances, refreshBalance, refreshAllBalances, addAccount } = useDirectory();

  const [activeTab, setActiveTab] = useState<'send' | 'topup'>('send');
  const [lastUpdated, setLastUpdated] = useState<Date | null>(null);
  const [loadingBalance, setLoadingBalance] = useState(false);
  const [refreshTrigger, setRefreshTrigger] = useState(0);

  const account = accounts.find(a => a.userId === userId);
  const currentBalance = userId ? balances[userId] : undefined;

  const loadAccountWallet = useCallback(async (uid: string) => {
    setLoadingBalance(true);
    try {
      const res = await apiFetch<WalletResponse>(`/api/v1/wallets/${uid}`);
      setLastUpdated(new Date());

      // If we don't have walletId stored yet, update directory account
      if (account && !account.walletId && res.data.walletId) {
        addAccount({
          ...account,
          walletId: res.data.walletId,
        });
      }
      await refreshBalance(uid);
    } catch {
      // Balance will be null
    } finally {
      setLoadingBalance(false);
    }
  }, [account, addAccount, refreshBalance]);

  useEffect(() => {
    if (!userId) return;
    loadAccountWallet(userId);
  }, [userId]);

  const handleOperationSuccess = async () => {
    if (!userId) return;
    await refreshBalance(userId);
    setLastUpdated(new Date());
    refreshAllBalances();
    setRefreshTrigger(prev => prev + 1);
  };

  if (!userId) {
    return null;
  }

  const displayName = account?.label || (account ? account.userId : 'Account');
  const displayEmail = account?.email;
  const walletId = account?.walletId;

  return (
    <div>
      {/* Header Band */}
      <header className="account-header-band">
        <div className="account-title-row">
          <h1 className="account-label">{displayName}</h1>
          {displayEmail && <span className="account-email">{displayEmail}</span>}
        </div>

        <div className="account-meta-row">
          <div className="account-meta-item">
            <CopyText label="User ID" value={userId} />
          </div>
          {walletId && (
            <div className="account-meta-item">
              <CopyText label="Wallet ID" value={walletId} />
            </div>
          )}
        </div>
      </header>

      {/* Two columns: LEFT flexible, RIGHT 320px */}
      <div className="account-columns">
        {/* LEFT column */}
        <div className="account-left-col">
          <BalanceBlock
            balancePaise={currentBalance}
            lastUpdated={lastUpdated}
            loading={loadingBalance}
          />

          <Ledger userId={userId} refreshTrigger={refreshTrigger} />
        </div>

        {/* RIGHT column */}
        <div className="account-right-col">
          <div className="panel">
            <div className="tabs-header" role="tablist">
              <button
                type="button"
                role="tab"
                id="tab-send"
                aria-controls="panel-send"
                aria-selected={activeTab === 'send'}
                className={`tab-btn ${activeTab === 'send' ? 'active' : ''}`}
                onClick={() => setActiveTab('send')}
              >
                Send money
              </button>
              <button
                type="button"
                role="tab"
                id="tab-topup"
                aria-controls="panel-topup"
                aria-selected={activeTab === 'topup'}
                className={`tab-btn ${activeTab === 'topup' ? 'active' : ''}`}
                onClick={() => setActiveTab('topup')}
              >
                Add money
              </button>
            </div>

            <div
              id="panel-send"
              role="tabpanel"
              aria-labelledby="tab-send"
              hidden={activeTab !== 'send'}
            >
              {activeTab === 'send' && (
                <SendForm
                  currentUserId={userId}
                  currentBalancePaise={currentBalance}
                  onSuccess={handleOperationSuccess}
                />
              )}
            </div>

            <div
              id="panel-topup"
              role="tabpanel"
              aria-labelledby="tab-topup"
              hidden={activeTab !== 'topup'}
            >
              {activeTab === 'topup' && (
                <TopUpForm
                  currentUserId={userId}
                  onSuccess={handleOperationSuccess}
                />
              )}
            </div>
          </div>
        </div>
      </div>
    </div>
  );
};
