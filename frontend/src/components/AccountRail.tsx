import React from 'react';
import { NavLink, useNavigate, useParams } from 'react-router-dom';
import { useDirectory } from '../state/DirectoryContext';
import { formatPaise } from '../lib/money';
import { formatShortId } from '../lib/format';

export const AccountRail: React.FC = () => {
  const { accounts, balances } = useDirectory();
  const { userId: currentUserId } = useParams<{ userId: string }>();
  const navigate = useNavigate();

  return (
    <>
      {/* Mobile Acting As selector */}
      <div className="mobile-acting-as">
        <label htmlFor="mobile-account-select" className="form-label">
          Acting as:
        </label>
        <select
          id="mobile-account-select"
          className="input-select"
          value={currentUserId || ''}
          onChange={e => {
            if (e.target.value === 'new') {
              navigate('/accounts/new');
            } else if (e.target.value) {
              navigate(`/accounts/${e.target.value}`);
            }
          }}
        >
          {accounts.map(acc => {
            const bal = balances[acc.userId];
            const balStr = bal !== null && bal !== undefined ? ` (${formatPaise(bal)})` : ' (—)';
            return (
              <option key={acc.userId} value={acc.userId}>
                {acc.label || formatShortId(acc.userId)} {balStr}
              </option>
            );
          })}
          <option value="new">+ Add / Create Account</option>
        </select>
      </div>

      {/* Desktop Left Rail */}
      <aside className="left-rail">
        <div className="rail-header">
          <span className="rail-title">Accounts</span>
          <span className="mono text-ink-2" style={{ fontSize: '13px' }}>
            {accounts.length}
          </span>
        </div>

        <ul className="rail-list">
          {accounts.map(acc => {
            const bal = balances[acc.userId];
            const balDisplay =
              bal !== null && bal !== undefined ? formatPaise(bal) : '—';
            const isActive = acc.userId === currentUserId;

            return (
              <li key={acc.userId}>
                <NavLink
                  to={`/accounts/${acc.userId}`}
                  className={`rail-item ${isActive ? 'active' : ''}`}
                >
                  <span
                    className="rail-item-label"
                    title={acc.label || acc.userId}
                  >
                    {acc.label || formatShortId(acc.userId)}
                  </span>
                  <span className="rail-item-balance">{balDisplay}</span>
                </NavLink>
              </li>
            );
          })}
        </ul>

        <div className="rail-footer">
          <NavLink to="/accounts/new" className="btn-text">
            New account
          </NavLink>
          <NavLink to="/accounts/new" className="btn-text">
            Add by ID
          </NavLink>
        </div>
      </aside>
    </>
  );
};

