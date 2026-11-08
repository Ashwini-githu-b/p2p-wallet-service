import React, { createContext, useContext, useState, useEffect, ReactNode, useCallback } from 'react';
import { DirectoryAccount, getStoredDirectory, addAccountToDirectory } from '../lib/directory';
import { apiFetch } from '../api/client';
import { WalletResponse } from '../api/types';

interface DirectoryContextValue {
  accounts: DirectoryAccount[];
  balances: Record<string, number | null>;
  addAccount: (acc: DirectoryAccount) => void;
  refreshBalance: (userId: string) => Promise<number | null>;
  refreshAllBalances: () => Promise<void>;
  getAccount: (userId: string) => DirectoryAccount | undefined;
}

const DirectoryContext = createContext<DirectoryContextValue | null>(null);

export const DirectoryProvider: React.FC<{ children: ReactNode }> = ({ children }) => {
  const [accounts, setAccounts] = useState<DirectoryAccount[]>(() => getStoredDirectory());
  const [balances, setBalances] = useState<Record<string, number | null>>({});

  const refreshBalance = useCallback(async (userId: string): Promise<number | null> => {
    try {
      const res = await apiFetch<WalletResponse>(`/api/v1/wallets/${userId}`);
      setBalances(prev => ({ ...prev, [userId]: res.data.balancePaise }));
      return res.data.balancePaise;
    } catch {
      setBalances(prev => ({ ...prev, [userId]: null }));
      return null;
    }
  }, []);

  const refreshAllBalances = useCallback(async () => {
    const current = getStoredDirectory();
    setAccounts(current);
    await Promise.all(
      current.map(async acc => {
        try {
          const res = await apiFetch<WalletResponse>(`/api/v1/wallets/${acc.userId}`);
          setBalances(prev => ({ ...prev, [acc.userId]: res.data.balancePaise }));
        } catch {
          setBalances(prev => ({ ...prev, [acc.userId]: null }));
        }
      })
    );
  }, []);

  const addAccount = useCallback((acc: DirectoryAccount) => {
    const updated = addAccountToDirectory(acc);
    setAccounts(updated);
    refreshBalance(acc.userId);
  }, [refreshBalance]);

  const getAccount = useCallback(
    (userId: string) => accounts.find(a => a.userId === userId),
    [accounts]
  );

  useEffect(() => {
    refreshAllBalances();

    const onFocus = () => {
      refreshAllBalances();
    };
    window.addEventListener('focus', onFocus);
    return () => window.removeEventListener('focus', onFocus);
  }, [refreshAllBalances]);

  return (
    <DirectoryContext.Provider
      value={{
        accounts,
        balances,
        addAccount,
        refreshBalance,
        refreshAllBalances,
        getAccount,
      }}
    >
      {children}
    </DirectoryContext.Provider>
  );
};

export function useDirectory(): DirectoryContextValue {
  const ctx = useContext(DirectoryContext);
  if (!ctx) {
    throw new Error('useDirectory must be used within DirectoryProvider');
  }
  return ctx;
}

