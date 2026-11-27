import { useQuery } from '@tanstack/react-query';
import { StyleSheet, Text, View } from 'react-native';
import { areaApi } from '../../api/endpoints';
import { errorMessage } from '../../api/errors';
import {
  Card,
  ErrorBanner,
  InsightCard,
  LoadingBlock,
  MetricCard,
  Screen,
  SectionHeader,
} from '../../components';
import { colors, spacing, typography } from '../../theme/tokens';

function forecastText(data: unknown): string | null {
  if (!data || typeof data !== 'object') return null;
  const rec = data as Record<string, unknown>;
  if (typeof rec.explanation === 'string') return rec.explanation;
  if (typeof rec.summary === 'string') return rec.summary;
  return null;
}

export function AreaPerformanceScreen() {
  const reportQ = useQuery({
    queryKey: ['area', 'report'],
    queryFn: areaApi.report,
  });
  const forecastQ = useQuery({
    queryKey: ['area', 'forecast'],
    queryFn: areaApi.forecast,
    retry: false,
  });

  const report = reportQ.data;
  const loading = reportQ.isLoading;
  const refreshing = reportQ.isRefetching || forecastQ.isRefetching;

  const refresh = () => {
    void reportQ.refetch();
    void forecastQ.refetch();
  };

  return (
    <Screen
      title="Area performance"
      subtitle={report ? `${report.areaName} (${report.areaCode})` : '30-day activity snapshot'}
      refreshing={refreshing}
      onRefresh={refresh}>
      {reportQ.isError ? <ErrorBanner message={errorMessage(reportQ.error)} /> : null}
      {loading ? <LoadingBlock label="Loading report…" /> : null}

      {report ? (
        <>
          <View style={styles.metrics}>
            <MetricCard label="E-float pool" value={report.efloatPool} />
            <MetricCard label="Cash pool" value={report.cashPool} />
            <MetricCard label="Agents low on e-float" value={report.agentsLowOnEfloat} tone="warning" />
            <MetricCard label="Agents low on cash" value={report.agentsLowOnCash} tone="warning" />
            <MetricCard label="Flagged transactions" value={report.flaggedTransactions} tone="warning" />
            {report.transactions?.count != null ? (
              <MetricCard label="Transactions" value={report.transactions.count} />
            ) : null}
            {report.transactions?.volume != null ? (
              <MetricCard label="Transaction volume" value={report.transactions.volume} currency="USD" />
            ) : null}
          </View>

          <SectionHeader title="Agent roll-up" />
          <Card style={styles.table}>
            {report.agents.map((a) => (
              <View key={a.id} style={styles.row}>
                <Text style={styles.agentCode}>{a.agentCode}</Text>
                <Text style={styles.agentName} numberOfLines={1}>
                  {a.businessName}
                </Text>
                <Text style={styles.agentVal}>{a.status}</Text>
              </View>
            ))}
          </Card>
        </>
      ) : null}

      {forecastText(forecastQ.data) ? (
        <InsightCard
          title="Forecast"
          explanation={forecastText(forecastQ.data)!}
          footer={forecastQ.isError ? undefined : 'From area forecast model'}
        />
      ) : forecastQ.isError ? (
        <Text style={styles.muted}>Area forecast is not available right now.</Text>
      ) : null}
    </Screen>
  );
}

const styles = StyleSheet.create({
  metrics: { gap: spacing.md, marginBottom: spacing.lg },
  table: { padding: spacing.md, gap: spacing.sm, marginBottom: spacing.lg },
  row: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: spacing.sm,
    borderBottomWidth: StyleSheet.hairlineWidth,
    borderBottomColor: colors.line,
    paddingVertical: spacing.sm,
  },
  agentCode: { ...typography.caption, color: colors.forest, width: 72 },
  agentName: { ...typography.body, color: colors.ink, flex: 1 },
  agentVal: { ...typography.caption, color: colors.inkMuted },
  muted: { ...typography.body, color: colors.inkFaint },
});