export interface DirectoryAccount {
  userId: string;
  label: string;
  email?: string;
  walletId?: string;
  addedAt: string;
}

const STORAGE_KEY = 'wallet-console.directory.v1';

export function getStoredDirectory(): DirectoryAccount[] {
  try {
    const raw = localStorage.getItem(STORAGE_KEY);
    if (!raw) return [];
    const parsed = JSON.parse(raw);
    if (Array.isArray(parsed)) {
      return parsed;
    }
    return [];
  } catch (err) {
    console.error('Failed to read accounts directory from localStorage', err);
    return [];
  }
}

export function saveStoredDirectory(accounts: DirectoryAccount[]): void {
  try {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(accounts));
  } catch (err) {
    console.error('Failed to save accounts directory to localStorage', err);
  }
}

export function addAccountToDirectory(newAccount: DirectoryAccount): DirectoryAccount[] {
  const current = getStoredDirectory();
  const existingIndex = current.findIndex(a => a.userId === newAccount.userId);
  let updated: DirectoryAccount[];
  if (existingIndex >= 0) {
    updated = [...current];
    updated[existingIndex] = { ...updated[existingIndex], ...newAccount };
  } else {
    updated = [...current, newAccount];
  }
  saveStoredDirectory(updated);
  return updated;
}

