import { useQuery } from '@tanstack/react-query';
import { format } from 'date-fns';
import { StyleSheet, View } from 'react-native';
import { agentApi } from '../../api/endpoints';
import { errorMessage } from '../../api/errors';
import { EmptyState, ErrorBanner, ListRow, LoadingBlock, Screen } from '../../components';
import { colors, spacing } from '../../theme/tokens';
import { money } from '../../utils/money';

export function TransactionHistoryScreen() {
  const query = useQuery({
    queryKey: ['agent', 'transactions'],
    queryFn: () => agentApi.transactions({ page: 0 }),
  });
  const items = query.data?.content ?? [];

  return (
    <Screen
      title="Transaction history"
      subtitle="Your ledger activity"
      refreshing={query.isRefetching}
      onRefresh={() => query.refetch()}>
      {query.isError ? <ErrorBanner message={errorMessage(query.error)} /> : null}
      {query.isLoading ? <LoadingBlock label="Loading transactions…" /> : null}
      {!query.isLoading && items.length === 0 ? (
        <EmptyState title="No transactions" message="Completed transactions will appear here." />
      ) : null}
      <View style={styles.list}>
        {items.map((tx) => (
          <View key={tx.id} style={styles.item}>
            <ListRow
              title={tx.type.replace(/_/g, ' ')}
              subtitle={`${tx.reference} · ${format(new Date(tx.createdAt), 'dd MMM yyyy HH:mm')}${tx.customerName ? `\n${tx.customerName}` : ''}`}
              value={money(tx.amount, tx.currency)}
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