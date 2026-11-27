import { StyleSheet, Text, View } from 'react-native';
import { colors, radii, spacing, typography } from '../theme/tokens';

type AiBadgeProps = {
  label?: string;
};

/** Visible marker for AI / model-generated content. */
export function AiBadge({ label = 'AI' }: AiBadgeProps) {
  return (
    <View style={styles.ai} accessibilityLabel="AI generated">
      <Text style={styles.aiText}>{label}</Text>
    </View>
  );
}

export type DataKind = 'ledger' | 'predicted' | 'recommended' | 'derived';

const KIND_STYLES: Record<DataKind, { bg: string; fg: string; label: string }> = {
  ledger: { bg: colors.successSoft, fg: colors.success, label: 'Ledger' },
  predicted: { bg: colors.infoSoft, fg: colors.info, label: 'Predicted' },
  recommended: { bg: colors.warningSoft, fg: colors.warning, label: 'Recommended' },
  derived: { bg: colors.sandDeep, fg: colors.inkMuted, label: 'Derived' },
};

export function DataKindBadge({ kind }: { kind: DataKind }) {
  const s = KIND_STYLES[kind];
  return (
    <View style={[styles.kind, { backgroundColor: s.bg }]}>
      <Text style={[styles.kindText, { color: s.fg }]}>{s.label}</Text>
    </View>
  );
}

const styles = StyleSheet.create({
  ai: {
    backgroundColor: colors.infoSoft,
    borderColor: colors.info,
    borderWidth: StyleSheet.hairlineWidth,
    paddingHorizontal: spacing.sm,
    paddingVertical: 2,
    borderRadius: radii.sm,
  },
  aiText: {
    ...typography.micro,
    color: colors.info,
    textTransform: 'uppercase',
  },
  kind: {
    paddingHorizontal: spacing.sm,
    paddingVertical: 2,
    borderRadius: radii.sm,
  },
  kindText: {
    ...typography.micro,
    textTransform: 'uppercase',
  },
});
