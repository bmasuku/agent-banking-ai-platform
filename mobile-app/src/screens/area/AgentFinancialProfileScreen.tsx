import { RouteProp, useNavigation, useRoute } from '@react-navigation/native';
import type { NativeStackNavigationProp } from '@react-navigation/native-stack';
import { useQuery } from '@tanstack/react-query';
import { format } from 'date-fns';
import { StyleSheet, View } from 'react-native';
import { areaApi } from '../../api/endpoints';
import { errorMessage } from '../../api/errors';
import {
  Button,
  Card,
  EmptyState,
  ErrorBanner,
  InsightCard,
  ListRow,
  LoadingBlock,
  MetricCard,
  Screen,
  SectionHeader,
  StatusChip,
} from '../../components';
import type { AreaStackParamList } from '../../navigation/types';
import { spacing } from '../../theme/tokens';
import { money } from '../../utils/money';

type Nav = NativeStackNavigationProp<AreaStackParamList>;
type Route = RouteProp<AreaStackParamList, 'AgentProfile'>;

export function AgentFinancialProfileScreen() {
  const navigation = useNavigation<Nav>();
  const { agentId } = useRoute<Route>().params;

  const profileQ = useQuery({
    queryKey: ['area', 'agent', agentId],
    queryFn: () => areaApi.agent(agentId),
  });
  const balancesQ = useQuery({
    queryKey: ['area', 'agent', agentId, 'balances'],
    queryFn: () => areaApi.agentBalances(agentId),
  });
  const txQ = useQuery({
    queryKey: ['area', 'agent', agentId, 'transactions'],
    queryFn: () => areaApi.agentTransactions(agentId),
  });
  const predictQ = useQuery({
    queryKey: ['area', 'agent', agentId, 'predict'],
    queryFn: () => areaApi.predict(agentId),
    retry: false,
  });

  const loading = profileQ.isLoading || balancesQ.isLoading;
  const refreshing =
    profileQ.isRefetching || balancesQ.isRefetching || txQ.isRefetching || predictQ.isRefetching;
  const profile = profileQ.data;
  const balances = balancesQ.data;
  const transactions = txQ.data?.content ?? [];

  const refresh = () => {
    void profileQ.refetch();
    void balancesQ.refetch();
    void txQ.refetch();
    void predictQ.refetch();
  };

  return (
    <Screen
      title={profile?.businessName ?? 'Agent profile'}
      subtitle={profile ? `${profile.agentCode} · ${profile.fullName}` : undefined}
      refreshing={refreshing}
      onRefresh={refresh}>
      {profileQ.isError ? <ErrorBanner message={errorMessage(profileQ.error)} /> : null}
      {balancesQ.isError ? <ErrorBanner message={errorMessage(balancesQ.error)} /> : null}
      {loading ? <LoadingBlock label="Loading agent profile…" /> : null}

      {!loading && profile ? (
        <>
          <View style={styles.chips}>
            <StatusChip label={profile.status} />
            <StatusChip
              label={profile.riskLevel}
              tone={profile.riskLevel === 'LOW' ? 'success' : 'warning'}
            />
            {balances?.efloatLow ? <StatusChip label="Low e-float" tone="warning" /> : null}
          </View>

          {balances ? (
            <View style={styles.metrics}>
              <MetricCard label="E-float" value={balances.balances.efloat} currency={balances.currency} />
              <MetricCard label="Cash" value={balances.balances.cash} currency={balances.currency} />
              <MetricCard
                label="Commission payable"
                value={balances.balances.commissionPayable}
                currency={balances.currency}
              />
              <MetricCard
                label="Credit available"
                value={balances.availableCredit}
                currency={balances.currency}
              />
            </View>
          ) : null}

          {predictQ.data ? (
            <InsightCard
              title="Liquidity prediction"
              explanation={predictQ.data.explanation}
              footer={`Recommended e-float top-up: ${money(predictQ.data.recommendedEfloatTopup, balances?.currency)}`}
            />
          ) : null}

          <Button
            label="Replenish float"
            onPress={() => navigation.navigate('Replenish', { agentId })}
            style={styles.cta}
          />

          <SectionHeader title="Recent transactions" />
          {txQ.isLoading ? <LoadingBlock label="Loading transactions…" /> : null}
          {transactions.length === 0 && !txQ.isLoading ? (
            <EmptyState title="No transactions" message="No recent activity for this agent." />
          ) : null}
          <Card style={styles.listCard}>
            {transactions.map((t) => (
              <ListRow
                key={t.id}
                title={t.type.replace(/_/g, ' ')}
                subtitle={`${t.reference} · ${format(new Date(t.createdAt), 'dd MMM HH:mm')}`}
                value={money(t.amount, t.currency)}
              />
            ))}
          </Card>
        </>
      ) : null}
    </Screen>
  );
}

const styles = StyleSheet.create({
  chips: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    gap: spacing.sm,
    marginBottom: spacing.md,
  },
  metrics: { gap: spacing.md, marginBottom: spacing.lg },
  cta: { marginBottom: spacing.lg },
  listCard: { paddingHorizontal: spacing.md },
});