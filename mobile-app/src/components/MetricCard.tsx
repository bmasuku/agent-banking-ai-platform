import { StyleSheet, Text, View } from 'react-native';
import { colors, spacing, typography } from '../theme/tokens';
import { money } from '../utils/money';
import { AiBadge, DataKindBadge, type DataKind } from './AiBadge';
import { Card } from './Card';

export type MetricTone = 'low' | 'warning' | 'success' | 'danger';

type MetricCardProps = {
  label: string;
  value: number | string;
  currency?: string;
  hint?: string;
  badge?: string;
  tone?: MetricTone;
  kind?: DataKind;
};

const TONE_STYLES: Record<MetricTone, { badgeBg: string; badgeText: string }> = {
  low: { badgeBg: colors.infoSoft, badgeText: colors.info },
  warning: { badgeBg: colors.warningSoft, badgeText: colors.warning },
  success: { badgeBg: colors.successSoft, badgeText: colors.success },
  danger: { badgeBg: colors.dangerSoft, badgeText: colors.danger },
};

export function MetricCard({
  label,
  value,
  currency,
  hint,
  badge,
  tone = 'low',
  kind,
}: MetricCardProps) {
  const displayValue = typeof value === 'number' ? money(value, currency) : value;
  const toneStyle = TONE_STYLES[tone];
  const predicted = kind === 'predicted' || kind === 'recommended';

  return (
    <Card style={predicted ? styles.predictedCard : undefined}>
      <View style={styles.top}>
        <Text style={styles.label}>{label}</Text>
        <View style={styles.badges}>
          {kind ? <DataKindBadge kind={kind} /> : null}
          {predicted ? <AiBadge /> : null}
          {badge ? (
            <View style={[styles.badge, { backgroundColor: toneStyle.badgeBg }]}>
              <Text style={[styles.badgeText, { color: toneStyle.badgeText }]}>{badge}</Text>
            </View>
          ) : null}
        </View>
      </View>
      <Text style={styles.value} accessibilityRole="text">
        {displayValue}
      </Text>
      {hint ? <Text style={styles.hint}>{hint}</Text> : null}
    </Card>
  );
}

const styles = StyleSheet.create({
  predictedCard: {
    borderColor: colors.info,
    borderWidth: StyleSheet.hairlineWidth,
    backgroundColor: colors.infoSoft,
  },
  top: {
    flexDirection: 'row',
    alignItems: 'flex-start',
    justifyContent: 'space-between',
    gap: spacing.sm,
    marginBottom: spacing.sm,
  },
  label: {
    ...typography.caption,
    color: colors.inkMuted,
    flex: 1,
  },
  badges: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    justifyContent: 'flex-end',
    gap: spacing.xs,
    maxWidth: '55%',
  },
  badge: {
    paddingHorizontal: spacing.sm,
    paddingVertical: spacing.xs,
    borderRadius: 999,
  },
  badgeText: {
    ...typography.micro,
    textTransform: 'uppercase',
  },
  value: {
    ...typography.display,
    color: colors.ink,
    fontVariant: ['tabular-nums'],
  },
  hint: {
    ...typography.caption,
    color: colors.inkFaint,
    marginTop: spacing.xs,
  },
});
