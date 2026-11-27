import { useQueries } from '@tanstack/react-query';
import { useNavigation } from '@react-navigation/native';
import type { NativeStackNavigationProp } from '@react-navigation/native-stack';
import { startOfDay } from 'date-fns';
import { StyleSheet, View } from 'react-native';
import { agentApi, sharedApi } from '../../api/endpoints';
import { errorMessage } from '../../api/errors';
import {
  Button,
  ErrorBanner,
  FloatTrendChart,
  InsightCard,
  LoadingBlock,
  MetricCard,
  Screen,
  SectionHeader,
} from '../../components';
import type { AgentStackParamList } from '../../navigation/types';
import { useAuthStore } from '../../store/authStore';
import { spacing } from '../../theme/tokens';
import { money } from '../../utils/money';

type Nav = NativeStackNavigationProp<AgentStackParamList>;

export function AgentDashboardScreen() {
  const navigation = useNavigation<Nav>();
  const user = useAuthStore((s) => s.user);
  const agentId = user?.agentId ?? '';
  const todayFrom = startOfDay(new Date()).toISOString();
  const todayTo = new Date().toISOString();

  const [balancesQ, txsQ, predictionQ, creditQ, commissionQ] = useQueries({
    queries: [
      { queryKey: ['agent', 'balances'], queryFn: agentApi.balances },
      {
        queryKey: ['agent', 'transactions', 'today'],
        queryFn: () => agentApi.transactions({ from: todayFrom, to: todayTo, page: 0 }),
      },
      {
        queryKey: ['agent', 'prediction', agentId],
        queryFn: () => sharedApi.prediction(agentId),
        enabled: !!agentId,
      },
      {
        queryKey: ['agent', 'credit', agentId],
        queryFn: () => sharedApi.creditScore(agentId),
        enabled: !!agentId,
      },
      { queryKey: ['agent', 'commissions', 'summary'], queryFn: agentApi.commissionsSummary },
    ],
  });

  const loading = balancesQ.isLoading || commissionQ.isLoading;
  const firstError = [balancesQ, txsQ, predictionQ, creditQ, commissionQ].find((q) => q.isError)?.error;

  const balances = balancesQ.data;
  const currency = balances?.currency ?? 'USD';
  const todayTxs = txsQ.data?.content ?? [];
  const todayVolume = todayTxs.reduce((sum, t) => sum + t.amount, 0);
  const outstandingAdvance =
    (balances?.balances.advancePrincipalOutstanding ?? 0) + (balances?.balances.advanceInterestOutstanding ?? 0);

  const refresh = () => {
    void balancesQ.refetch();
    void txsQ.refetch();
    void predictionQ.refetch();
    void creditQ.refetch();
    void commissionQ.refetch();
  };
  const refreshing =
    balancesQ.isRefetching || txsQ.isRefetching || predictionQ.isRefetching || creditQ.isRefetching || commissionQ.isRefetching;

  return (
    <Screen
      title={`Hello, ${user?.fullName?.split(' ')[0] ?? 'Agent'}`}
      subtitle={balances?.businessName ?? user?.agentCode ?? 'Your outlet dashboard'}
      refreshing={refreshing}
      onRefresh={refresh}>
      {firstError ? <ErrorBanner message={errorMessage(firstError)} /> : null}
      {loading ? <LoadingBlock label="Loading dashboard…" /> : null}

      {balances ? (
        <View style={styles.metrics}>
          <View style={styles.metricCell}>
            <MetricCard
              label="E-float"
              value={balances.balances.efloat}
              currency={currency}
              kind="ledger"
              tone={balances.efloatLow ? 'warning' : 'success'}
              badge={balances.efloatLow ? 'Low' : undefined}
              hint="Posted ledger balance"
            />
          </View>
          <View style={styles.metricCell}>
            <MetricCard
              label="Cash in hand"
              value={balances.balances.cash}
              currency={currency}
              kind="ledger"
              tone={balances.cashLow ? 'warning' : 'success'}
              badge={balances.cashLow ? 'Low' : undefined}
              hint="Posted ledger balance"
            />
          </View>
          <View style={styles.metricCell}>
            <MetricCard
              label="Commission payable"
              value={balances.balances.commissionPayable}
              currency={currency}
              kind="ledger"
              hint={commissionQ.data ? `Accrued ${commissionQ.data.accruedCount} items` : undefined}
            />
          </View>
          <View style={styles.metricCell}>
            <MetricCard
              label="Advances outstanding"
              value={outstandingAdvance}
              currency={currency}
              kind="ledger"
            />
          </View>
          {predictionQ.data ? (
            <>
              <View style={styles.metricCell}>
                <MetricCard
                  label="Projected e-float"
                  value={predictionQ.data.projectedEfloat}
                  currency={currency}
                  kind="predicted"
                  hint="Not a ledger balance"
                />
              </View>
              <View style={styles.metricCell}>
                <MetricCard
                  label="Recommended top-up"
                  value={predictionQ.data.recommendedEfloatTopup}
                  currency={currency}
                  kind="recommended"
                  hint="Model suggestion only"
                />
              </View>
            </>
          ) : null}
          <View style={styles.metricCell}>
            <MetricCard
              label="Credit score"
              value={creditQ.data?.score ?? '—'}
              kind="derived"
              hint={creditQ.data ? `${creditQ.data.riskBand} · model-assisted` : undefined}
            />
          </View>
          <View style={styles.metricCell}>
            <MetricCard
              label="Today's transactions"
              value={`${todayTxs.length}`}
              kind="ledger"
              hint={money(todayVolume, currency)}
            />
          </View>
        </View>
      ) : null}

      {predictionQ.data?.trajectory?.length ? (
        <FloatTrendChart
          title="Projected e-float path"
          subtitle="Forecast trajectory — not posted balances"
          dataKind="predicted"
          points={predictionQ.data.trajectory.map((t, i) => ({
            label: i % 4 === 0 ? String(i) : '',
            value: t.efloat,
          }))}
        />
      ) : null}

      {predictionQ.data?.explanation ? (
        <InsightCard
          title="Float insight"
          source="ai"
          explanation={predictionQ.data.explanation}
          footer={`Risk: ${predictionQ.data.riskType.replace(/_/g, ' ')} · confidence ${Math.round(predictionQ.data.confidence * 100)}%`}
        />
      ) : null}

      <SectionHeader title="Quick actions" />
      <View style={styles.actions}>
        <Button label="Cash in" variant="secondary" onPress={() => navigation.navigate('CashDeposit')} />
        <Button label="Cash out" variant="secondary" onPress={() => navigation.navigate('Withdrawal')} />
        <Button label="Request advance" variant="secondary" onPress={() => navigation.navigate('RequestAdvance')} />
        <Button label="Nearby agents" variant="secondary" onPress={() => navigation.navigate('NearbyAgents')} />
        <Button label="Commission" variant="secondary" onPress={() => navigation.navigate('Commission')} />
        <Button label="Credit score" variant="secondary" onPress={() => navigation.navigate('CreditScore')} />
        <Button label="Notifications" variant="ghost" onPress={() => navigation.navigate('Notifications')} />
      </View>
    </Screen>
  );
}

const styles = StyleSheet.create({
  metrics: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    marginHorizontal: -spacing.xs,
    marginBottom: spacing.lg,
  },
  metricCell: {
    width: '50%',
    paddingHorizontal: spacing.xs,
    marginBottom: spacing.sm,
  },
  actions: {
    gap: spacing.sm,
  },
});