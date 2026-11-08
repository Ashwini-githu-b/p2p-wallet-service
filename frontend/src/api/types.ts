export interface CreateUserRequest {
  name: string;
  email: string;
}

export interface UserResponse {
  userId: string;
  name: string;
  email: string;
  walletId: string;
  balancePaise: number;
}

export interface WalletResponse {
  userId: string;
  walletId: string;
  balancePaise: number;
}

export interface TopUpRequest {
  amountPaise: number;
}

export interface TransferRequest {
  fromUserId: string;
  toUserId: string;
  amountPaise: number;
}

export interface TransactionResponse {
  transactionId: string;
  type: 'TOPUP' | 'TRANSFER';
  fromUserId: string | null;
  toUserId: string;
  amountPaise: number;
  createdAt: string;
}

export interface LedgerItem {
  transactionId: string;
  type: 'TOPUP' | 'TRANSFER';
  direction: 'DEBIT' | 'CREDIT';
  amountPaise: number;
  balanceAfterPaise: number;
  counterpartyUserId: string | null;
  createdAt: string;
}

export interface PageResponse<T> {
  items: T[];
  page: number;
  size: number;
  totalElements: number;
}

export interface ErrorResponse {
  timestamp: string;
  status: number;
  code: string;
  message: string;
  path: string;
}

export interface RequestLogEntry {
  id: string;
  time: string;
  method: string;
  path: string;
  status: number;
  durationMs: number;
  idempotencyKey?: string;
  requestBody?: unknown;
  responseBody?: unknown;
  responseHeaders?: Record<string, string>;
}

