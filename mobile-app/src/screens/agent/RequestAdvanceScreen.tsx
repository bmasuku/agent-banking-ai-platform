import { useQuery } from '@tanstack/react-query';
import { useState } from 'react';
import { StyleSheet, Text, View } from 'react-native';
import { agentApi, sharedApi } from '../../api/endpoints';
import { errorMessage } from '../../api/errors';
import {
  AiBadge,
  AmountInput,
  Button,
  Card,
  ConfirmSheet,
  DataKindBadge,
  ErrorBanner,
  InsightCard,
  LoadingBlock,
  Screen,
  TextField,
  parseAmount,
} from '../../components';
import { useAuthStore } from '../../store/authStore';
import { colors, spacing, typography } from '../../theme/tokens';
import { newIdempotencyKey } from '../../utils/idempotency';
import { money } from '../../utils/money';

export function RequestAdvanceScreen() {
  const agentId = useAuthStore((s) => s.user?.agentId) ?? '';
  const creditQ = useQuery({
    queryKey: ['agent', 'credit', agentId],
    queryFn: () => sharedApi.creditScore(agentId),
    enabled: !!agentId,
  });

  const [amount, setAmount] = useState('');
  const [termDays, setTermDays] = useState('30');
  const [purpose, setPurpose] = useState('');
  const [confirmVisible, setConfirmVisible] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [successRef, setSuccessRef] = useState<string | null>(null);

  const amountNum = parseAmount(amount);
  const term = parseInt(termDays, 10) || 0;
  const credit = creditQ.data;
  const eligible = credit?.eligible !== false;

  const onSubmit = async () => {
    setSubmitting(true);
    setError(null);
    try {
      const res = await agentApi.requestAdvance(
        { amount: amountNum, termDays: term, purpose: purpose.trim() },
        newIdempotencyKey(),
      );
      setConfirmVisible(false);
      setSuccessRef(res.reference);
    } catch (e) {
      setError(errorMessage(e));
    } finally {
      setSubmitting(false);
    }
  };

  const onRequestPress = () => {
    setError(null);
    if (amountNum <= 0 || term <= 0 || !purpose.trim()) {
      setError('Enter amount, term (days), and purpose.');
      return;
    }
    if (!eligible) {
      setError('You are not currently eligible for an advance.');
      return;
    }
    setConfirmVisible(true);
  };

  return (
    <Screen
      title="Request advance"
      subtitle="Working-capital advance secured by your credit score"
      refreshing={creditQ.isRefetching}
      onRefresh={() => creditQ.refetch()}>
      {error ? <ErrorBanner message={error} /> : null}
      {creditQ.isLoading ? <LoadingBlock label="Checking eligibility…" /> : null}

      {credit ? (
        <>
          <Card>
            <View style={styles.badgeRow}>
              <AiBadge label="AI credit" />
              <DataKindBadge kind="recommended" />
            </View>
            <Text style={styles.score}>Score {credit.score}</Text>
            <Text style={styles.meta}>
              Band {credit.riskBand} · recommended limit{' '}
              {money(credit.recommendedAdvanceAmount ?? credit.recommendedLimit)} (not a ledger balance)
            </Text>
            {credit.recommendation ? <Text style={styles.meta}>{credit.recommendation}</Text> : null}
            {!eligible ? <Text style={styles.ineligible}>Not eligible at current score.</Text> : null}
          </Card>
          {credit.explanation ? (
            <InsightCard title="Credit insight" source="ai" explanation={credit.explanation} />
          ) : null}
        </>
      ) : null}

      <View style={styles.form}>
        <AmountInput label="Requested amount" value={amount} onChangeValue={setAmount} />
        <TextField
          label="Term (days)"
          value={termDays}
          onChangeText={setTermDays}
          keyboardType="number-pad"
        />
        <TextField label="Purpose" value={purpose} onChangeText={setPurpose} />
        <Button label="Submit request" onPress={onRequestPress} disabled={!eligible} />
      </View>

      {successRef ? (
        <Card>
          <Text style={styles.success}>Advance request submitted.</Text>
          <Text style={styles.meta}>Reference {successRef}</Text>
        </Card>
      ) : null}

      <ConfirmSheet
        visible={confirmVisible}
        title="Confirm advance request"
        message={`Request ${money(amountNum)} for ${term} days?`}
        onConfirm={onSubmit}
        onCancel={() => setConfirmVisible(false)}
        loading={submitting}
      />
    </Screen>
  );
}

const styles = StyleSheet.create({
  form: { gap: spacing.md, marginTop: spacing.lg },
  badgeRow: { flexDirection: 'row', flexWrap: 'wrap', gap: spacing.xs, marginBottom: spacing.sm },
  score: { ...typography.title, color: colors.ink },
  meta: { ...typography.body, color: colors.inkMuted, marginTop: spacing.xs },
  ineligible: { ...typography.caption, color: colors.danger, marginTop: spacing.sm },
  success: { ...typography.subtitle, color: colors.success },
});