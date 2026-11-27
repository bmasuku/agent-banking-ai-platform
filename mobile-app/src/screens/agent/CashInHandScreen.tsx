import { useQueries } from '@tanstack/react-query';
import { format } from 'date-fns';
import { StyleSheet, View } from 'react-native';
import { agentApi } from '../../api/endpoints';
import { errorMessage } from '../../api/errors';
import { EmptyState, ErrorBanner, ListRow, LoadingBlock, MetricCard, Screen, SectionHeader } from '../../components';
import type { PageResponse } from '../../types/api';
import { colors, spacing } from '../../theme/tokens';
import { money } from '../../utils/money';

type StatementLine = {
  reference: string;
  transactionType: string;
  signedAmount: number;
  balanceAfter: number;
  memo?: string;
  createdAt: string;
};

export function CashInHandScreen() {
  const [balancesQ, statementQ] = useQueries({
    queries: [
      { queryKey: ['agent', 'balances'], queryFn: agentApi.balances },
      {
        queryKey: ['agent', 'statement', 'CASH'],
        queryFn: () => agentApi.statement('CASH', 0) as Promise<PageResponse<StatementLine>>,
      },
    ],
  });

  const balances = balancesQ.data;
  const currency = balances?.currency ?? 'USD';
  const lines = statementQ.data?.content ?? [];
  const loading = balancesQ.isLoading || statementQ.isLoading;
  const error = balancesQ.error ?? statementQ.error;

  return (
    <Screen
      title="Cash in hand"
      subtitle="Physical cash ledger"
      refreshing={balancesQ.isRefetching || statementQ.isRefetching}
      onRefresh={() => {
        void balancesQ.refetch();
        void statementQ.refetch();
      }}>
      {error ? <ErrorBanner message={errorMessage(error)} /> : null}
      {loading ? <LoadingBlock label="Loading cash balance…" /> : null}
      {balances ? (
        <MetricCard
          label="Cash in hand"
          value={balances.balances.cash}
          currency={currency}
          tone={balances.cashLow ? 'warning' : 'success'}
          hint={`Min threshold ${money(balances.minCashThreshold, currency)}`}
        />
      ) : null}

      <SectionHeader title="Statement" />
      {!loading && lines.length === 0 ? (
        <EmptyState message="No cash movements yet." />
      ) : null}
      <View style={styles.list}>
        {lines.map((line, idx) => (
          <View key={`${line.reference}-${idx}`} style={styles.item}>
            <ListRow
              title={line.transactionType.replace(/_/g, ' ')}
              subtitle={`${line.reference} · ${format(new Date(line.createdAt), 'dd MMM yyyy HH:mm')}`}
              value={money(line.signedAmount, currency)}
            />
          </View>
        ))}
      </View>
    </Screen>
  );
}

const styles = StyleSheet.create({
  list: { gap: spacing.xs },
  item: {
    backgroundColor: colors.white,
    borderRadius: 12,
    paddingHorizontal: spacing.md,
  },
});