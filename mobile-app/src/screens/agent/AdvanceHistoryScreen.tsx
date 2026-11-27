import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { format } from 'date-fns';
import { useState } from 'react';
import { StyleSheet, View } from 'react-native';
import { agentApi } from '../../api/endpoints';
import { errorMessage } from '../../api/errors';
import {
  AmountInput,
  Button,
  ConfirmSheet,
  EmptyState,
  ErrorBanner,
  ListRow,
  LoadingBlock,
  Screen,
  SectionHeader,
  parseAmount,
} from '../../components';
import { colors, spacing } from '../../theme/tokens';
import { newIdempotencyKey } from '../../utils/idempotency';
import { money } from '../../utils/money';

export function AdvanceHistoryScreen() {
  const qc = useQueryClient();
  const query = useQuery({
    queryKey: ['agent', 'advances'],
    queryFn: () => agentApi.advances(),
  });

  const [selectedId, setSelectedId] = useState<string | null>(null);
  const [repayAmount, setRepayAmount] = useState('');
  const [confirmVisible, setConfirmVisible] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const repayMut = useMutation({
    mutationFn: async () => {
      if (!selectedId) return;
      await agentApi.repayAdvance(selectedId, parseAmount(repayAmount), newIdempotencyKey());
    },
    onSuccess: () => {
      setConfirmVisible(false);
      setRepayAmount('');
      setSelectedId(null);
      void qc.invalidateQueries({ queryKey: ['agent', 'advances'] });
      void qc.invalidateQueries({ queryKey: ['agent', 'balances'] });
    },
    onError: (e) => setError(errorMessage(e)),
  });

  const items = query.data?.content ?? [];
  const selected = items.find((a) => a.id === selectedId);

  const onRepayPress = () => {
    setError(null);
    if (!selectedId || parseAmount(repayAmount) <= 0) {
      setError('Select an advance and enter a repayment amount.');
      return;
    }
    setConfirmVisible(true);
  };

  return (
    <Screen
      title="Advance history"
      subtitle="Outstanding and settled working-capital advances"
      refreshing={query.isRefetching}
      onRefresh={() => query.refetch()}>
      {error ? <ErrorBanner message={error} /> : null}
      {query.isError ? <ErrorBanner message={errorMessage(query.error)} /> : null}
      {query.isLoading ? <LoadingBlock label="Loading advances…" /> : null}
      {!query.isLoading && items.length === 0 ? <EmptyState message="No advances on record." /> : null}

      <View style={styles.list}>
        {items.map((adv) => (
          <View key={adv.id} style={[styles.item, selectedId === adv.id && styles.itemSelected]}>
            <ListRow
              title={`${adv.reference} · ${adv.status}`}
              subtitle={`Requested ${money(adv.requestedAmount)} · outstanding ${money(adv.totalOutstanding)}${adv.dueDate ? `\nDue ${format(new Date(adv.dueDate), 'dd MMM yyyy')}` : ''}`}
              value={adv.approvedAmount != null ? money(adv.approvedAmount) : '—'}
              onPress={() => setSelectedId(adv.id)}
            />
          </View>
        ))}
      </View>

      {selected && selected.totalOutstanding > 0 ? (
        <>
          <SectionHeader title="Repay advance" />
          <View style={styles.repay}>
            <AmountInput
              label={`Repayment amount (outstanding ${money(selected.totalOutstanding)})`}
              value={repayAmount}
              onChangeValue={setRepayAmount}
            />
            <Button label="Submit repayment" onPress={onRepayPress} loading={repayMut.isPending} />
          </View>
        </>
      ) : null}

      <ConfirmSheet
        visible={confirmVisible}
        title="Confirm repayment"
        message={selected ? `Repay ${money(parseAmount(repayAmount))} on ${selected.reference}?` : undefined}
        onConfirm={() => repayMut.mutate()}
        onCancel={() => setConfirmVisible(false)}
        loading={repayMut.isPending}
      />
    </Screen>
  );
}

const styles = StyleSheet.create({
  list: { gap: spacing.xs, marginBottom: spacing.lg },
  item: {
    backgroundColor: colors.white,
    borderRadius: 12,
    paddingHorizontal: spacing.md,
  },
  itemSelected: {
    borderWidth: 1,
    borderColor: colors.forest,
  },
  repay: { gap: spacing.md },
});