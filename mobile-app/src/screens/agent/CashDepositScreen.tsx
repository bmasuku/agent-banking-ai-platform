import { useNavigation } from '@react-navigation/native';
import type { NativeStackNavigationProp } from '@react-navigation/native-stack';
import { useState } from 'react';
import { StyleSheet, Text, View } from 'react-native';
import { agentApi } from '../../api/endpoints';
import { errorMessage } from '../../api/errors';
import {
  AmountInput,
  Button,
  Card,
  ConfirmSheet,
  ErrorBanner,
  Screen,
  TextField,
  parseAmount,
} from '../../components';
import type { AgentStackParamList } from '../../navigation/types';
import { requestForegroundLocation } from '../../services/location';
import { colors, spacing, typography } from '../../theme/tokens';
import { newIdempotencyKey } from '../../utils/idempotency';
import { money } from '../../utils/money';
import type { QuoteResponse } from '../../types/api';

type Nav = NativeStackNavigationProp<AgentStackParamList>;

const TX_TYPE = 'CASH_DEPOSIT' as const;

export function CashDepositScreen() {
  const navigation = useNavigation<Nav>();
  const [amount, setAmount] = useState('');
  const [customerPhone, setCustomerPhone] = useState('');
  const [customerName, setCustomerName] = useState('');
  const [narration, setNarration] = useState('');
  const [quote, setQuote] = useState<QuoteResponse | null>(null);
  const [quoteLoading, setQuoteLoading] = useState(false);
  const [confirmVisible, setConfirmVisible] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const amountNum = parseAmount(amount);

  const buildBody = async () => {
    const loc = await requestForegroundLocation();
    return {
      amount: amountNum,
      customerPhone: customerPhone.trim(),
      customerName: customerName.trim() || undefined,
      narration: narration.trim() || undefined,
      latitude: loc.latitude,
      longitude: loc.longitude,
    };
  };

  const onPreview = async () => {
    setError(null);
    if (amountNum <= 0 || !customerPhone.trim()) {
      setError('Enter a valid amount and customer phone.');
      return;
    }
    setQuoteLoading(true);
    try {
      const q = await agentApi.quote(TX_TYPE, amountNum);
      setQuote(q);
    } catch (e) {
      setError(errorMessage(e));
      setQuote(null);
    } finally {
      setQuoteLoading(false);
    }
  };

  const onConfirm = async () => {
    setSubmitting(true);
    setError(null);
    try {
      const body = await buildBody();
      const result = await agentApi.deposit(body, newIdempotencyKey());
      setConfirmVisible(false);
      navigation.navigate('TransactionConfirm', { result });
    } catch (e) {
      setError(errorMessage(e));
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <Screen title="Cash in" subtitle="Customer gives cash; wallet is credited">
      {error ? <ErrorBanner message={error} /> : null}
      <View style={styles.form}>
        <AmountInput label="Amount" value={amount} onChangeValue={setAmount} />
        <TextField label="Customer phone" value={customerPhone} onChangeText={setCustomerPhone} keyboardType="phone-pad" />
        <TextField label="Customer name (if new)" value={customerName} onChangeText={setCustomerName} />
        <TextField label="Narration" value={narration} onChangeText={setNarration} />
        <Button label="Preview quote" onPress={onPreview} loading={quoteLoading} />
      </View>

      {quote ? (
        <Card>
          <Text style={styles.quoteTitle}>Quote preview</Text>
          <Text style={styles.quoteLine}>{quote.explanation}</Text>
          <Text style={styles.quoteLine}>Customer receives: {money(quote.customerReceives)}</Text>
          <Text style={styles.quoteLine}>
            Projected e-float: {money(quote.projectedEfloat)} · Cash: {money(quote.projectedCash)}
          </Text>
          {!quote.sufficientLiquidity ? (
            <Text style={styles.warn}>Insufficient e-float for this cash in.</Text>
          ) : null}
          <View style={styles.confirmBtn}>
            <Button
              label="Confirm cash in"
              disabled={!quote.sufficientLiquidity}
              onPress={() => setConfirmVisible(true)}
            />
          </View>
        </Card>
      ) : null}

      <ConfirmSheet
        visible={confirmVisible}
        title="Confirm cash in"
        message={`Cash in ${money(amountNum)} from ${customerPhone.trim()}?`}
        onConfirm={onConfirm}
        onCancel={() => setConfirmVisible(false)}
        loading={submitting}
      />
    </Screen>
  );
}

const styles = StyleSheet.create({
  form: { gap: spacing.md, marginBottom: spacing.lg },
  quoteTitle: { ...typography.subtitle, color: colors.ink, marginBottom: spacing.sm },
  quoteLine: { ...typography.body, color: colors.inkMuted, marginBottom: spacing.xs },
  warn: { ...typography.caption, color: colors.warning, marginTop: spacing.sm },
  confirmBtn: { marginTop: spacing.md },
});