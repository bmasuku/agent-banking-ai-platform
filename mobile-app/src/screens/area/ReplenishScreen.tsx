import { RouteProp, useRoute } from '@react-navigation/native';
import { useMutation, useQuery } from '@tanstack/react-query';
import { useEffect, useMemo, useState } from 'react';
import { StyleSheet, Text, View } from 'react-native';
import { areaApi } from '../../api/endpoints';
import { errorMessage } from '../../api/errors';
import {
  AmountInput,
  Button,
  Card,
  ConfirmSheet,
  ErrorBanner,
  InsightCard,
  LoadingBlock,
  MetricCard,
  Screen,
  TextField,
  parseAmount,
} from '../../components';
import type { AreaStackParamList } from '../../navigation/types';
import { newIdempotencyKey } from '../../utils/idempotency';
import { money } from '../../utils/money';
import { colors, spacing, typography } from '../../theme/tokens';

type Route = RouteProp<AreaStackParamList, 'Replenish'>;
type ReplenishType = 'EFLOAT' | 'CASH';

export function ReplenishScreen() {
  const routeAgentId = useRoute<Route>().params?.agentId;
  const [agentId, setAgentId] = useState(routeAgentId ?? '');
  const [type, setType] = useState<ReplenishType>('EFLOAT');
  const [amountStr, setAmountStr] = useState('');
  const [reason, setReason] = useState('');
  const [confirmOpen, setConfirmOpen] = useState(false);
  const [successRef, setSuccessRef] = useState<string | null>(null);
  const [formError, setFormError] = useState<string | null>(null);

  const agentsQ = useQuery({ queryKey: ['area', 'agents'], queryFn: areaApi.agents });
  const balancesQ = useQuery({
    queryKey: ['area', 'agent', agentId, 'balances'],
    queryFn: () => areaApi.agentBalances(agentId),
    enabled: Boolean(agentId),
  });
  const predictQ = useQuery({
    queryKey: ['area', 'agent', agentId, 'predict'],
    queryFn: () => areaApi.predict(agentId),
    enabled: Boolean(agentId),
    retry: false,
  });

  useEffect(() => {
    if (routeAgentId) setAgentId(routeAgentId);
  }, [routeAgentId]);

  const agents = agentsQ.data ?? [];
  const selected = agents.find((a) => a.id === agentId);
  const amount = parseAmount(amountStr);

  const mutation = useMutation({
    mutationFn: () => {
      const key = newIdempotencyKey('REPL');
      return areaApi.replenish(
        agentId,
        {
          type,
          amount,
          reason: reason.trim(),
          predictionId: predictQ.data?.id,
        },
        key,
      );
    },
    onSuccess: (data) => {
      setConfirmOpen(false);
      const ref =
        data && typeof data === 'object' && 'reference' in data
          ? String((data as { reference: string }).reference)
          : 'Replenishment submitted';
      setSuccessRef(ref);
    },
    onError: (e) => {
      setFormError(errorMessage(e));
      setConfirmOpen(false);
    },
  });

  const preview = useMemo(() => {
    const cur = balancesQ.data;
    const lines = [
      `Agent: ${selected?.agentCode ?? agentId}`,
      `Type: ${type}`,
      `Amount: ${money(amount, cur?.currency)}`,
      `Reason: ${reason.trim() || '—'}`,
    ];
    if (predictQ.data?.id) {
      lines.push(`Linked prediction: ${predictQ.data.id.slice(0, 8)}…`);
    }
    return lines.join('\n');
  }, [agentId, amount, balancesQ.data, predictQ.data, reason, selected, type]);

  if (successRef) {
    return (
      <Screen title="Replenishment sent" subtitle={successRef}>
        <Text style={styles.successBody}>
          The request was accepted by the platform. Balances update once processing completes.
        </Text>
        <Button label="New replenishment" onPress={() => setSuccessRef(null)} variant="secondary" />
      </Screen>
    );
  }

  const openConfirm = () => {
    setFormError(null);
    if (!agentId) {
      setFormError('Select an agent.');
      return;
    }
    if (amount <= 0) {
      setFormError('Enter a valid amount.');
      return;
    }
    if (!reason.trim()) {
      setFormError('Enter a reason for the replenishment.');
      return;
    }
    setConfirmOpen(true);
  };

  return (
    <Screen title="Replenish float" subtitle="Allocate e-float or cash from the area pool">
      {agentsQ.isError ? <ErrorBanner message={errorMessage(agentsQ.error)} /> : null}
      {formError ? <ErrorBanner message={formError} /> : null}
      {agentsQ.isLoading ? <LoadingBlock label="Loading agents…" /> : null}

      <Card style={styles.card}>
        <Text style={styles.sectionLabel}>Agent</Text>
        <View style={styles.agentPickers}>
          {agents.map((a) => (
            <Button
              key={a.id}
              label={`${a.agentCode} · ${a.businessName}`}
              variant={agentId === a.id ? 'primary' : 'secondary'}
              onPress={() => setAgentId(a.id)}
            />
          ))}
        </View>
      </Card>

      {agentId && balancesQ.isLoading ? <LoadingBlock label="Loading balances…" /> : null}
      {balancesQ.data ? (
        <View style={styles.metrics}>
          <MetricCard
            label="Current e-float"
            value={balancesQ.data.balances.efloat}
            currency={balancesQ.data.currency}
          />
          <MetricCard
            label="Current cash"
            value={balancesQ.data.balances.cash}
            currency={balancesQ.data.currency}
          />
        </View>
      ) : null}

      {predictQ.data ? (
        <InsightCard
          title="AI recommendation"
          explanation={predictQ.data.explanation}
          footer={`Suggested e-float top-up: ${money(predictQ.data.recommendedEfloatTopup, balancesQ.data?.currency)}`}
        />
      ) : null}

      <View style={styles.typeRow}>
        <Button
          label="E-float"
          variant={type === 'EFLOAT' ? 'primary' : 'secondary'}
          onPress={() => setType('EFLOAT')}
        />
        <Button
          label="Cash"
          variant={type === 'CASH' ? 'primary' : 'secondary'}
          onPress={() => setType('CASH')}
        />
      </View>

      <AmountInput label="Amount" value={amountStr} onChangeValue={setAmountStr} />
      {predictQ.data && type === 'EFLOAT' && predictQ.data.recommendedEfloatTopup > 0 ? (
        <Button
          label={`Use suggested ${money(predictQ.data.recommendedEfloatTopup, balancesQ.data?.currency)}`}
          variant="ghost"
          onPress={() => setAmountStr(String(predictQ.data.recommendedEfloatTopup))}
        />
      ) : null}

      <TextField label="Reason" value={reason} onChangeText={setReason} placeholder="Operational justification" multiline />

      <Button label="Review & submit" onPress={openConfirm} style={styles.submit} />

      <ConfirmSheet
        visible={confirmOpen}
        title="Confirm replenishment"
        message={preview}
        confirmLabel="Submit replenishment"
        loading={mutation.isPending}
        onConfirm={() => mutation.mutate()}
        onCancel={() => setConfirmOpen(false)}
      />
    </Screen>
  );
}

const styles = StyleSheet.create({
  card: { padding: spacing.lg, marginBottom: spacing.md, gap: spacing.sm },
  sectionLabel: { ...typography.caption, color: colors.inkMuted },
  agentPickers: { gap: spacing.sm },
  metrics: { gap: spacing.md, marginBottom: spacing.md },
  typeRow: { flexDirection: 'row', gap: spacing.sm, marginBottom: spacing.md },
  submit: { marginTop: spacing.md },
  successBody: { ...typography.body, color: colors.inkMuted, marginBottom: spacing.lg },
});