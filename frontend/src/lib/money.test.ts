import { describe, it, expect } from 'vitest';
import { parseRupeesToPaise, formatPaise, isValidPaise } from './money';

describe('money', () => {
  describe('parseRupeesToPaise', () => {
    it('parses valid inputs correctly to integer paise', () => {
      expect(parseRupeesToPaise('1000')).toBe(100000);
      expect(parseRupeesToPaise('1,000.50')).toBe(100050);
      expect(parseRupeesToPaise('1,00,000')).toBe(10000000);
      expect(parseRupeesToPaise('0.1')).toBe(10);
      expect(parseRupeesToPaise('.5')).toBe(50);
      expect(parseRupeesToPaise('0.01')).toBe(1);
      expect(parseRupeesToPaise('250.5')).toBe(25050);
      expect(parseRupeesToPaise('0')).toBe(0);
    });

    it('returns null for invalid inputs', () => {
      expect(parseRupeesToPaise('')).toBeNull();
      expect(parseRupeesToPaise('   ')).toBeNull();
      expect(parseRupeesToPaise('abc')).toBeNull();
      expect(parseRupeesToPaise('-5')).toBeNull();
      expect(parseRupeesToPaise('12.345')).toBeNull();
      expect(parseRupeesToPaise('1.2.3')).toBeNull();
      expect(parseRupeesToPaise('1e3')).toBeNull();
    });

    it('validates 1..10,000,000 range check correctly for parsed values', () => {
      const zeroParsed = parseRupeesToPaise('0');
      expect(zeroParsed).toBe(0);
      expect(isValidPaise(zeroParsed)).toBe(false);

      const validParsed = parseRupeesToPaise('100');
      expect(isValidPaise(validParsed)).toBe(true);

      const maxParsed = parseRupeesToPaise('1,00,000');
      expect(isValidPaise(maxParsed)).toBe(true);

      const overMax = parseRupeesToPaise('1,00,000.01');
      expect(isValidPaise(overMax)).toBe(false);
    });
  });

  describe('formatPaise', () => {
    it('formats paise correctly in Indian rupee format with 2 decimals', () => {
      expect(formatPaise(0)).toBe('₹0.00');
      expect(formatPaise(1)).toBe('₹0.01');
      expect(formatPaise(100050)).toBe('₹1,000.50');
      expect(formatPaise(10000000)).toBe('₹1,00,000.00');
      expect(formatPaise(123456789)).toBe('₹12,34,567.89');
    });
  });
});

