import React, { useState, useEffect } from 'react';
import { NavLink } from 'react-router-dom';
import { apiFetch } from '../api/client';

export const TopBar: React.FC = () => {
  const [apiUp, setApiUp] = useState<boolean | null>(null);

  useEffect(() => {
    let mounted = true;

    const checkHealth = async () => {
      try {
        const res = await apiFetch<{ status: string }>('/actuator/health', {
          skipLog: true,
        });
        if (mounted) {
          setApiUp(res.data?.status === 'UP');
        }
      } catch {
        if (mounted) {
          setApiUp(false);
        }
      }
    };

    checkHealth();
    const interval = setInterval(checkHealth, 15000);
    return () => {
      mounted = false;
      clearInterval(interval);
    };
  }, []);

  return (
    <header className="top-bar">
      <div className="top-bar-left">
        <NavLink to="/" className="wordmark">
          Wallet Console
        </NavLink>
        <nav className="nav-links">
          <NavLink
            to="/"
            className={({ isActive }) =>
              `nav-link ${isActive && !window.location.pathname.startsWith('/lab') ? 'active' : ''}`
            }
          >
            Accounts
          </NavLink>
          <NavLink
            to="/lab"
            className={({ isActive }) => `nav-link ${isActive ? 'active' : ''}`}
          >
            Lab
          </NavLink>
        </nav>
      </div>

      <div className="api-status">
        <span
          className={`status-dot ${apiUp === true ? 'up' : 'unreachable'}`}
          aria-hidden="true"
        />
        <span>{apiUp === true ? 'API: up' : 'API: unreachable'}</span>
      </div>
    </header>
  );
};

