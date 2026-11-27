import { useCallback } from 'react';
import { TextField } from './TextField';
import { num } from '../utils/money';

type AmountInputProps = {
  label?: string;
  value: string;
  onChangeValue: (value: string) => void;
  error?: string;
  placeholder?: string;
};

function sanitizeAmountInput(raw: string): string {
  const cleaned = raw.replace(/[^0-9.]/g, '');
  const parts = cleaned.split('.');
  if (parts.length <= 1) {
    return cleaned;
  }
  return `${parts[0]}.${parts.slice(1).join('')}`;
}

export function AmountInput({
  label,
  value,
  onChangeValue,
  error,
  placeholder = '0.00',
}: AmountInputProps) {
  const handleChange = useCallback(
    (text: string) => {
      onChangeValue(sanitizeAmountInput(text));
    },
    [onChangeValue],
  );

  return (
    <TextField
      label={label}
      value={value}
      onChangeText={handleChange}
      keyboardType="decimal-pad"
      inputMode="decimal"
      placeholder={placeholder}
      error={error}
      accessibilityHint="Enter a monetary amount"
    />
  );
}

export function parseAmount(value: string): number {
  return num(value);
}