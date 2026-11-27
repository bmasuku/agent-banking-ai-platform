import { useQueries } from '@tanstack/react-query';
import { format } from 'date-fns';
import { StyleSheet, View } from 'react-native';
import { agentApi } from '../../api/endpoints';
import { errorMessage } from '../../api/errors';
import { EmptyState, ErrorBanner, ListRow, LoadingBlock, MetricCard, Screen, SectionHeader } from '../../components';
import type { PageResponse } from '../../types/api';
import { colors, spacing } from '../../theme/tokens';
import { money } from '../../utils/money';

type CommissionItem = {
  id: string;
  transactionReference: string;
  transactionType: string;
  amount: number;
  status: string;
  accruedAt: string;
};

export function CommissionScreen() {
  const [summaryQ, listQ] = useQueries({
    queries: [
      { queryKey: ['agent', 'commissions', 'summary'], queryFn: agentApi.commissionsSummary },
      {
        queryKey: ['agent', 'commissions', 'list'],
        queryFn: () => agentApi.commissions() as Promise<PageResponse<CommissionItem>>,
      },
    ],
  });

  const summary = summaryQ.data;
  const items = listQ.data?.content ?? [];
  const loading = summaryQ.isLoading || listQ.isLoading;
  const error = summaryQ.error ?? listQ.error;

  return (
    <Screen
      title="Commission"
      subtitle="Accrued commissions from customer transactions"
      refreshing={summaryQ.isRefetching || listQ.isRefetching}
      onRefresh={() => {
        void summaryQ.refetch();
        void listQ.refetch();
      }}>
      {error ? <ErrorBanner message={errorMessage(error)} /> : null}
      {loading ? <LoadingBlock label="Loading commissions…" /> : null}

      {summary ? (
        <View style={styles.metrics}>
          <MetricCard label="Payable" value={summary.accruedPayable} hint={`${summary.accruedCount} open items`} />
          <MetricCard label="Lifetime gross" value={summary.lifetimeGrossDisbursed} hint={`Net ${money(summary.lifetimeNetDisbursed)}`} />
        </View>
      ) : null}

      <SectionHeader title="Accrual history" />
      {!loading && items.length === 0 ? <EmptyState message="No commission accruals yet." /> : null}
      <View style={styles.list}>
        {items.map((c) => (
          <View key={c.id} style={styles.item}>
            <ListRow
              title={c.transactionType.replace(/_/g, ' ')}
              subtitle={`${c.transactionReference} · ${c.status} · ${format(new Date(c.accruedAt), 'dd MMM yyyy')}`}
              value={money(c.amount)}
            />
          </View>
        ))}
      </View>
    </Screen>
  );
}

const styles = StyleSheet.create({
  metrics: { gap: spacing.sm, marginBottom: spacing.lg },
  list: { gap: spacing.xs },
  item: {
    backgroundColor: colors.white,
    borderRadius: 12,
    paddingHorizontal: spacing.md,
  },
});