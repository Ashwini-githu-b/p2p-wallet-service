import React from 'react';
import { Routes, Route, Navigate, Outlet } from 'react-router-dom';
import { useDirectory } from './state/DirectoryContext';
import { TopBar } from './components/TopBar';
import { AccountRail } from './components/AccountRail';
import { RequestDock } from './components/RequestDock';
import { NewAccountPage } from './pages/NewAccountPage';
import { WalletPage } from './pages/WalletPage';
import { LabPage } from './pages/LabPage';

const AppLayout: React.FC = () => {
  return (
    <div className="app-container">
      <TopBar />
      <main className="main-wrapper">
        <div className="app-layout">
          <AccountRail />
          <div className="content-area">
            <Outlet />
          </div>
        </div>
      </main>
      <RequestDock />
    </div>
  );
};

const IndexRedirect: React.FC = () => {
  const { accounts } = useDirectory();
  if (accounts.length > 0) {
    return <Navigate to={`/accounts/${accounts[0].userId}`} replace />;
  }
  return <Navigate to="/accounts/new" replace />;
};

export const App: React.FC = () => {
  return (
    <Routes>
      <Route element={<AppLayout />}>
        <Route path="/" element={<IndexRedirect />} />
        <Route path="/accounts/new" element={<NewAccountPage />} />
        <Route path="/accounts/:userId" element={<WalletPage />} />
        <Route path="/lab" element={<LabPage />} />
        <Route path="*" element={<Navigate to="/" replace />} />
      </Route>
    </Routes>
  );
};

