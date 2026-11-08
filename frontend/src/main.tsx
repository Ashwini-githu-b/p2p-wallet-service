import React from 'react';
import ReactDOM from 'react-dom/client';
import { BrowserRouter } from 'react-router-dom';

import '@fontsource/ibm-plex-sans/400.css';
import '@fontsource/ibm-plex-sans/500.css';
import '@fontsource/ibm-plex-sans/600.css';
import '@fontsource/ibm-plex-mono/400.css';
import '@fontsource/ibm-plex-mono/500.css';

import './styles/tokens.css';
import './styles/base.css';
import './styles/components.css';

import { DirectoryProvider } from './state/DirectoryContext';
import { RequestLogProvider } from './state/RequestLogContext';
import { App } from './App';

ReactDOM.createRoot(document.getElementById('root')!).render(
  <React.StrictMode>
    <BrowserRouter>
      <RequestLogProvider>
        <DirectoryProvider>
          <App />
        </DirectoryProvider>
      </RequestLogProvider>
    </BrowserRouter>
  </React.StrictMode>
);

