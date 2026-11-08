export const MIN_PAISE = 1;
export const MAX_PAISE = 10_000_000; // Rs 1,00,000

/**
 * Parses user rupee input to integer paise using string and integer math only.
 * No floating point multiplication or division is used.
 */
export function parseRupeesToPaise(input: string): number | null {
  const str = input.trim();
  if (str === '') {
    return null;
  }

  // Reject negative signs or scientific notation or invalid characters
  if (str.startsWith('-') || str.includes('e') || str.includes('E')) {
    return null;
  }

  const parts = str.split('.');
  if (parts.length > 2) {
    return null;
  }

  if (parts.length === 1) {
    // Integer only: digits and optional commas
    const raw = parts[0];
    if (!/^[0-9,]+$/.test(raw)) {
      return null;
    }
    const clean = raw.replace(/,/g, '');
    if (clean === '' || !/^\d+$/.test(clean)) {
      return null;
    }
    const rupees = Number(clean);
    if (!Number.isSafeInteger(rupees)) {
      return null;
    }
    return rupees * 100;
  } else {
    // Has decimal point: intPart.decPart
    const [rawInt, decPart] = parts;

    // Decimal part must be exactly 1 or 2 digits
    if (!/^\d{1,2}$/.test(decPart)) {
      return null;
    }

    let cleanInt = '0';
    if (rawInt !== '') {
      if (!/^[0-9,]+$/.test(rawInt)) {
        return null;
      }
      cleanInt = rawInt.replace(/,/g, '');
      if (cleanInt === '' || !/^\d+$/.test(cleanInt)) {
        return null;
      }
    }

    const rupees = Number(cleanInt);
    if (!Number.isSafeInteger(rupees)) {
      return null;
    }

    // Pad 1 decimal to 2 digits (e.g. .5 -> 50)
    const paddedDec = decPart.length === 1 ? `${decPart}0` : decPart;
    const paiseDec = Number(paddedDec);

    return rupees * 100 + paiseDec;
  }
}

/**
 * Formats integer paise into Indian currency format (e.g. ₹1,00,000.00).
 * Uses Intl.NumberFormat on the integer rupee portion to prevent float artifacts.
 */
export function formatPaise(paise: number): string {
  const absPaise = Math.abs(paise);
  const rupees = Math.floor(absPaise / 100);
  const rem = absPaise % 100;
  const remStr = String(rem).padStart(2, '0');
  const formattedRupees = new Intl.NumberFormat('en-IN').format(rupees);
  const prefix = paise < 0 ? '\u2212' : '';
  return `${prefix}₹${formattedRupees}.${remStr}`;
}

export function isValidPaise(paise: number | null): paise is number {
  return paise !== null && paise >= MIN_PAISE && paise <= MAX_PAISE;
}

