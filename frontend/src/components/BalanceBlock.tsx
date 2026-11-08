import React from 'react';
import { formatPaise } from '../lib/money';
import { formatTimeOnly } from '../lib/format';

interface BalanceBlockProps {
  balancePaise: number | null | undefined;
  lastUpdated: Date | null;
  loading?: boolean;
}

export const BalanceBlock: React.FC<BalanceBlockProps> = ({
  balancePaise,
  lastUpdated,
  loading = false,
}) => {
  return (
    <div className="balance-block">
      <div className="balance-label">Available balance</div>
      <div className="balance-figure">
        {balancePaise !== null && balancePaise !== undefined
          ? formatPaise(balancePaise)
          : loading
          ? 'Loading…'
          : '—'}
      </div>
      {lastUpdated && (
        <div className="balance-updated">
          Updated {formatTimeOnly(lastUpdated)}
        </div>
      )}
    </div>
  );
};

