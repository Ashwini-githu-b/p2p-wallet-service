import React, { useState } from 'react';

interface CopyTextProps {
  value: string;
  label?: string;
}

export const CopyText: React.FC<CopyTextProps> = ({ value, label }) => {
  const [copied, setCopied] = useState(false);

  const handleCopy = async () => {
    try {
      await navigator.clipboard.writeText(value);
      setCopied(true);
      setTimeout(() => setCopied(false), 1500);
    } catch {
      // fallback if clipboard API fails
      const el = document.createElement('textarea');
      el.value = value;
      document.body.appendChild(el);
      el.select();
      document.execCommand('copy');
      document.body.removeChild(el);
      setCopied(true);
      setTimeout(() => setCopied(false), 1500);
    }
  };

  return (
    <span style={{ display: 'inline-flex', alignItems: 'center', gap: '6px' }}>
      {label && <span className="text-ink-2">{label}:</span>}
      <span className="mono">{value}</span>
      <button
        type="button"
        className="btn-text"
        onClick={handleCopy}
        aria-label={label ? `Copy ${label}` : 'Copy'}
        style={{ display: 'inline-flex', alignItems: 'center', gap: '3px' }}
      >
        <svg
          width="13"
          height="13"
          viewBox="0 0 16 16"
          fill="none"
          stroke="currentColor"
          strokeWidth="1.5"
          aria-hidden="true"
        >
          <rect x="5.5" y="5.5" width="8" height="8" rx="1" />
          <path d="M10.5 5.5V2.5H2.5V10.5H5.5" />
        </svg>
        {copied ? 'Copied' : 'Copy'}
      </button>
    </span>
  );
};

