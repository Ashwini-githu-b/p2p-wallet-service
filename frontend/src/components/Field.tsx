import React, { InputHTMLAttributes, ReactNode } from 'react';

export interface FieldProps extends InputHTMLAttributes<HTMLInputElement> {
  id: string;
  label: string;
  hint?: string;
  error?: string | null;
  errorCode?: string | null;
  isAmount?: boolean;
  children?: ReactNode;
}

export const Field: React.FC<FieldProps> = ({
  id,
  label,
  hint,
  error,
  errorCode,
  isAmount = false,
  children,
  className = '',
  ...props
}) => {
  return (
    <div className="form-group">
      <label htmlFor={id} className="form-label">
        {label}
      </label>

      {children ? (
        children
      ) : isAmount ? (
        <div className="amount-input-wrapper">
          <span className="amount-currency-prefix">₹</span>
          <input id={id} className={`input-text ${className}`} {...props} />
        </div>
      ) : (
        <input id={id} className={`input-text ${className}`} {...props} />
      )}

      {hint && !error && <div className="form-hint">{hint}</div>}

      <div aria-live="polite">
        {error && (
          <div className="form-error">
            {error}
            {errorCode && <div className="form-error-code">Code: {errorCode}</div>}
          </div>
        )}
      </div>
    </div>
  );
};

