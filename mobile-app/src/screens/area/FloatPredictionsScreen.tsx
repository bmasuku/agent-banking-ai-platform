import { useNavigation } from '@react-navigation/native';
import type { NativeStackNavigationProp } from '@react-navigation/native-stack';
import { useQuery } from '@tanstack/react-query';
import { StyleSheet, Text, View } from 'react-native';
import { areaApi } from '../../api/endpoints';
import { errorMessage } from '../../api/errors';
import {
  AiBadge,
  Button,
  Card,
  DataKindBadge,
  EmptyState,
  ErrorBanner,
  InsightCard,
  LoadingBlock,
  MetricCard,
  Screen,
  StatusChip,
} from '../../components';
import type { AreaStackParamList } from '../../navigation/types';
import { colors, spacing, typography } from '../../theme/tokens';
import { money } from '../../utils/money';

type Nav = NativeStackNavigationProp<AreaStackParamList>;

export function FloatPredictionsScreen() {
  const navigation = useNavigation<Nav>();
  const query = useQuery({
    queryKey: ['area', 'predictions'],
    queryFn: areaApi.predictions,
  });

  const items = query.data ?? [];

  return (
    <Screen
      title="Float predictions"
      subtitle="AI liquidity outlook per agent — not posted balances"
      refreshing={query.isRefetching}
      onRefresh={() => query.refetch()}>
      {query.isError ? <ErrorBanner message={errorMessage(query.error)} /> : null}
      {query.isLoading ? <LoadingBlock label="Loading predictions…" /> : null}
      {!query.isLoading && items.length === 0 ? (
        <EmptyState
          title="No predictions yet"
          message="Liquidity forecasts appear once the AI service has enough recent float history for your agents."
        />
      ) : null}
      <View style={styles.list}>
        {items.map((p) => (
          <Card key={p.id} style={styles.card}>
            <View style={styles.cardHeader}>
              <View style={styles.headerText}>
                <Text style={styles.agentCode}>{p.agentCode}</Text>
                <Text style={styles.horizon}>{p.horizonHours}h horizon</Text>
              </View>
              <View style={styles.badges}>
                <DataKindBadge kind="predicted" />
                <AiBadge />
              </View>
            </View>
            <View style={styles.metrics}>
              <View style={styles.metricCell}>
                <MetricCard
                  label="Projected e-float"
                  value={p.projectedEfloat}
                  currency="USD"
                  kind="predicted"
                  hint="Forecast only"
                />
              </View>
              <View style={styles.metricCell}>
                <MetricCard
                  label="Recommended top-up"
                  value={p.recommendedEfloatTopup}
                  currency="USD"
                  kind="recommended"
                  hint="Model suggestion"
                />
              </View>
            </View>
            {p.explanation ? (
              <InsightCard
                title="Model rationale"
                source="ai"
                explanation={p.explanation}
                footer={`Confidence ${Math.round(p.confidence * 100)}% · Cash top-up ${money(p.recommendedCashTopup)}`}
              />
            ) : null}
            <View style={styles.row}>
              <StatusChip label={p.riskType} tone={p.riskType === 'NONE' ? 'success' : 'warning'} />
              <Button
                label="Replenish"
                variant="secondary"
                onPress={() => navigation.navigate('Replenish', { agentId: p.agentId })}
              />
              <Button
                label="Profile"
                variant="ghost"
                onPress={() => navigation.navigate('AgentProfile', { agentId: p.agentId })}
              />
            </View>
          </Card>
        ))}
      </View>
    </Screen>
  );
}

const styles = StyleSheet.create({
  list: { gap: spacing.lg },
  card: { gap: spacing.md },
  cardHeader: {
    flexDirection: 'row',
    alignItems: 'flex-start',
    justifyContent: 'space-between',
    gap: spacing.sm,
  },
  headerText: { flex: 1, gap: 2 },
  agentCode: { ...typography.subtitle, color: colors.ink },
  horizon: { ...typography.caption, color: colors.inkMuted },
  badges: { flexDirection: 'row', gap: spacing.xs, flexWrap: 'wrap', justifyContent: 'flex-end' },
  metrics: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    marginHorizontal: -spacing.xs,
  },
  metricCell: {
    width: '50%',
    paddingHorizontal: spacing.xs,
  },
  row: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    alignItems: 'center',
    gap: spacing.sm,
  },
});
