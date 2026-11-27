import { CompositeNavigationProp, useNavigation } from '@react-navigation/native';
import type { BottomTabNavigationProp } from '@react-navigation/bottom-tabs';
import type { NativeStackNavigationProp } from '@react-navigation/native-stack';
import { useQueries } from '@tanstack/react-query';
import { useMemo } from 'react';
import { StyleSheet, View } from 'react-native';
import { areaApi } from '../../api/endpoints';
import { errorMessage } from '../../api/errors';
import {
  Card,
  ErrorBanner,
  FloatTrendChart,
  InsightCard,
  ListRow,
  LoadingBlock,
  MetricCard,
  Screen,
  SectionHeader,
} from '../../components';
import type { AreaStackParamList, AreaTabParamList } from '../../navigation/types';
import { PageResponse, PredictionResponse } from '../../types/api';
import { spacing } from '../../theme/tokens';

type Nav = CompositeNavigationProp<
  BottomTabNavigationProp<AreaTabParamList, 'AreaHome'>,
  NativeStackNavigationProp<AreaStackParamList>
>;

function pageCount(data: unknown): number {
  if (data && typeof data === 'object' && 'totalElements' in data) {
    return (data as PageResponse<unknown>).totalElements;
  }
  if (Array.isArray(data)) return data.length;
  return 0;
}

function optimisationInsight(data: unknown): string | null {
  if (!data || typeof data !== 'object') return null;
  const rec = data as Record<string, unknown>;
  if (typeof rec.explanation === 'string' && rec.explanation.trim()) {
    return rec.explanation;
  }
  const pool = rec.pool_replenishments ?? rec.poolReplenishments;
  if (Array.isArray(pool) && pool.length > 0) {
    const first = pool[0] as Record<string, unknown>;
    if (typeof first.rationale === 'string') return first.rationale;
  }
  return null;
}

function needsReplenishment(p: PredictionResponse): boolean {
  return (
    p.recommendedEfloatTopup > 0 ||
    p.recommendedCashTopup > 0 ||
    (p.riskType !== 'NONE' && p.riskType !== undefined)
  );
}

export function AreaDashboardScreen() {
  const navigation = useNavigation<Nav>();

  const queries = useQueries({
    queries: [
      { queryKey: ['area', 'meta'], queryFn: areaApi.area },
      { queryKey: ['area', 'agents'], queryFn: areaApi.agents },
      { queryKey: ['area', 'predictions'], queryFn: areaApi.predictions },
      { queryKey: ['area', 'report'], queryFn: areaApi.report },
      {
        queryKey: ['area', 'optimisation'],
        queryFn: areaApi.optimisation,
        retry: false,
      },
      { queryKey: ['area', 'alerts'], queryFn: areaApi.alerts },
    ],
  });

  const [areaQ, agentsQ, predictionsQ, reportQ, optimisationQ, alertsQ] = queries;

  const loading = queries.some((q) => q.isLoading);
  const refreshing = queries.some((q) => q.isRefetching);
  const primaryError = areaQ.error ?? agentsQ.error;

  const agents = agentsQ.data ?? [];
  const predictions = predictionsQ.data ?? [];
  const area = areaQ.data;

  const metrics = useMemo(() => {
    const totalAgents = agents.length || area?.agentCount || 0;
    const activeAgents = agents.filter((a) => a.status === 'ACTIVE').length;
    const lowFloat = agents.filter((a) => a.efloatLow).length;
    const replenishAgents = new Set(
      predictions.filter(needsReplenishment).map((p) => p.agentId),
    ).size;
    const predictedDemand = predictions.reduce((s, p) => s + p.recommendedEfloatTopup, 0);
    const pendingAlerts = pageCount(alertsQ.data);
    return {
      totalAgents,
      activeAgents,
      lowFloat,
      replenishAgents,
      poolEfloat: area?.efloatPoolBalance ?? reportQ.data?.efloatPool ?? 0,
      predictedDemand,
      pendingAlerts,
    };
  }, [agents, area, predictions, alertsQ.data, reportQ.data]);

  const insightText =
    optimisationInsight(optimisationQ.data) ??
    predictions.find((p) => p.explanation)?.explanation ??
    null;

  const refreshAll = () => {
    void queries.forEach((q) => q.refetch());
  };

  return (
    <Screen
      title={area ? area.name : 'My area'}
      subtitle={area ? `${area.code} · ${area.province}` : 'Area overview'}
      refreshing={refreshing}
      onRefresh={refreshAll}>
      {primaryError ? <ErrorBanner message={errorMessage(primaryError)} /> : null}
      {loading ? <LoadingBlock label="Loading area dashboard…" /> : null}

      {!loading ? (
        <>
          <View style={styles.metrics}>
            <View style={styles.metricCell}>
              <MetricCard label="Total agents" value={metrics.totalAgents} kind="derived" />
            </View>
            <View style={styles.metricCell}>
              <MetricCard label="Active agents" value={metrics.activeAgents} tone="success" kind="derived" />
            </View>
            <View style={styles.metricCell}>
              <MetricCard label="Low e-float" value={metrics.lowFloat} tone="warning" kind="ledger" />
            </View>
            <View style={styles.metricCell}>
              <MetricCard
                label="Need replenishment"
                value={metrics.replenishAgents}
                tone={metrics.replenishAgents > 0 ? 'warning' : 'success'}
                kind="recommended"
              />
            </View>
            <View style={styles.metricCell}>
              <MetricCard label="Area e-float pool" value={metrics.poolEfloat} currency="USD" kind="ledger" />
            </View>
            <View style={styles.metricCell}>
              <MetricCard
                label="Predicted float demand"
                value={metrics.predictedDemand}
                currency="USD"
                kind="recommended"
                hint="Sum of AI top-up recommendations — not a ledger balance"
              />
            </View>
            <View style={styles.metricCell}>
              <MetricCard
                label="Pending alerts"
                value={metrics.pendingAlerts}
                tone={metrics.pendingAlerts > 0 ? 'warning' : 'success'}
                kind="derived"
              />
            </View>
          </View>

          {agents.length > 0 ? (
            <FloatTrendChart
              title="Agent e-float"
              subtitle="Live posted balances from assigned agents"
              kind="bar"
              dataKind="ledger"
              points={agents.slice(0, 8).map((a) => ({
                label: a.agentCode.replace('AGT-', ''),
                value: a.efloat,
              }))}
            />
          ) : null}

          {insightText ? (
            <InsightCard
              title="Liquidity insight"
              source="ai"
              explanation={insightText}
              footer={
                optimisationQ.data
                  ? 'From float optimisation model'
                  : 'From liquidity predictions'
              }
            />
          ) : null}

          <SectionHeader title="Shortcuts" />
          <Card style={styles.shortcuts}>
            <ListRow
              title="Assigned agents"
              subtitle="Profiles, balances and risk"
              showChevron
              onPress={() => navigation.navigate('AreaTabs', { screen: 'AreaAgents' })}
            />
            <ListRow
              title="Area map"
              subtitle="Agent locations and low-float flags"
              showChevron
              onPress={() => navigation.navigate('AreaTabs', { screen: 'AreaMap' })}
            />
            <ListRow
              title="Replenish float"
              subtitle="Top up e-float or cash"
              showChevron
              onPress={() => navigation.navigate('Replenish', {})}
            />
            <ListRow
              title="Float predictions"
              subtitle="24h liquidity outlook"
              showChevron
              onPress={() => navigation.navigate('FloatPredictions')}
            />
            <ListRow
              title="Field visits"
              subtitle="Synced check-ins for your area"
              showChevron
              onPress={() => navigation.navigate('FieldVisits')}
            />
          </Card>
        </>
      ) : null}
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
  shortcuts: {
    paddingHorizontal: spacing.md,
    paddingVertical: spacing.sm,
  },
});