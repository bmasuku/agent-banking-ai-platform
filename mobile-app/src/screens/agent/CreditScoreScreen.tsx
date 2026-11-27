import { useQuery } from '@tanstack/react-query';
import { StyleSheet, Text, View } from 'react-native';
import { sharedApi } from '../../api/endpoints';
import { errorMessage } from '../../api/errors';
import {
  AiBadge,
  EmptyState,
  ErrorBanner,
  InsightCard,
  ListRow,
  LoadingBlock,
  MetricCard,
  Screen,
  SectionHeader,
} from '../../components';
import { useAuthStore } from '../../store/authStore';
import { colors, spacing, typography } from '../../theme/tokens';
import { money } from '../../utils/money';

export function CreditScoreScreen() {
  const agentId = useAuthStore((s) => s.user?.agentId) ?? '';
  const query = useQuery({
    queryKey: ['agent', 'credit', agentId],
    queryFn: () => sharedApi.creditScore(agentId),
    enabled: !!agentId,
  });

  const credit = query.data;

  return (
    <Screen
      title="Credit score"
      subtitle="Model-assisted assessment — not a ledger balance"
      refreshing={query.isRefetching}
      onRefresh={() => query.refetch()}>
      <View style={styles.aiBanner}>
        <AiBadge label="AI credit model" />
        <Text style={styles.aiBannerText}>Scores and limits are recommendations only.</Text>
      </View>
      {query.isError ? <ErrorBanner message={errorMessage(query.error)} /> : null}
      {query.isLoading ? <LoadingBlock label="Loading credit score…" /> : null}
      {!agentId && !query.isLoading ? (
        <EmptyState title="No agent linked" message="Agent profile not linked to this user." />
      ) : null}

      {credit ? (
        <>
          <View style={styles.metrics}>
            <MetricCard
              label="Score"
              value={credit.score}
              badge={credit.riskBand}
              tone="low"
              kind="derived"
              hint="Model-assisted score"
            />
            <MetricCard
              label="Recommended limit"
              value={credit.recommendedLimit}
              currency="USD"
              kind="recommended"
              hint={`PD ${(credit.probabilityOfDefault * 100).toFixed(1)}% · not a balance`}
            />
          </View>
          {credit.explanation ? (
            <InsightCard
              title="Summary"
              source="ai"
              explanation={credit.explanation}
              footer="Explainable credit model output"
            />
          ) : null}
          {credit.recommendation ? (
            <Text style={styles.recommendation}>{credit.recommendation}</Text>
          ) : null}

          <SectionHeader title="Score factors" />
          <View style={styles.list}>
            {credit.factors.map((f) => (
              <View key={`${f.feature}-${f.value}`} style={styles.item}>
                <ListRow
                  title={f.feature}
                  subtitle={`${f.description}\n${f.direction} · ${f.value}`}
                  value={f.contribution > 0 ? `+${f.contribution.toFixed(2)}` : f.contribution.toFixed(2)}
                />
              </View>
            ))}
          </View>
          {credit.recommendedAdvanceAmount != null ? (
            <Text style={styles.footer}>
              Suggested advance: {money(credit.recommendedAdvanceAmount)} · daily rate {credit.recommendedDailyRate}
            </Text>
          ) : null}
        </>
      ) : null}
    </Screen>
  );
}

const styles = StyleSheet.create({
  aiBanner: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: spacing.sm,
    marginBottom: spacing.lg,
    paddingVertical: spacing.sm,
    paddingHorizontal: spacing.md,
    backgroundColor: colors.infoSoft,
    borderRadius: 12,
  },
  aiBannerText: { ...typography.caption, color: colors.info, flex: 1 },
  metrics: { gap: spacing.sm, marginBottom: spacing.lg },
  recommendation: { ...typography.body, color: colors.inkMuted, marginBottom: spacing.lg },
  list: { gap: spacing.xs },
  item: {
    backgroundColor: colors.white,
    borderRadius: 12,
    paddingHorizontal: spacing.md,
  },
  footer: { ...typography.caption, color: colors.inkFaint, marginTop: spacing.lg },
});