import { StyleSheet, Text, View } from 'react-native';
import { colors, spacing, typography } from '../theme/tokens';
import { AiBadge, DataKindBadge } from './AiBadge';
import { Card } from './Card';

type InsightCardProps = {
  title?: string;
  explanation: string;
  footer?: string;
  /** AI model output vs system/API-derived narrative. */
  source?: 'ai' | 'system';
};

export function InsightCard({ title, explanation, footer, source = 'ai' }: InsightCardProps) {
  return (
    <Card style={styles.card}>
      <View style={[styles.accent, source === 'ai' ? styles.accentAi : styles.accentSystem]} />
      <View style={styles.body}>
        <View style={styles.header}>
          {title ? <Text style={styles.title}>{title}</Text> : <View />}
          {source === 'ai' ? <AiBadge /> : <DataKindBadge kind="derived" />}
        </View>
        <Text style={styles.explanation}>{explanation}</Text>
        {footer ? <Text style={styles.footer}>{footer}</Text> : null}
      </View>
    </Card>
  );
}

const styles = StyleSheet.create({
  card: {
    overflow: 'hidden',
    padding: 0,
  },
  accent: {
    height: 4,
  },
  accentAi: {
    backgroundColor: colors.info,
  },
  accentSystem: {
    backgroundColor: colors.forestSoft,
  },
  body: {
    padding: spacing.lg,
    gap: spacing.sm,
  },
  header: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    gap: spacing.sm,
  },
  title: {
    ...typography.caption,
    color: colors.forest,
    textTransform: 'uppercase',
    letterSpacing: 0.6,
    flex: 1,
  },
  explanation: {
    ...typography.body,
    color: colors.ink,
    lineHeight: 22,
  },
  footer: {
    ...typography.caption,
    color: colors.inkFaint,
    marginTop: spacing.xs,
  },
});
