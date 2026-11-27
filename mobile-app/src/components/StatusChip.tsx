import { StyleSheet, Text, View } from 'react-native';
import { colors, radii, spacing, typography } from '../theme/tokens';

export type StatusChipTone =
  | 'neutral'
  | 'info'
  | 'success'
  | 'warning'
  | 'danger'
  | 'forest';

type StatusChipProps = {
  label: string;
  tone?: StatusChipTone;
};

const TONE_STYLES: Record<
  StatusChipTone,
  { backgroundColor: string; color: string }
> = {
  neutral: { backgroundColor: colors.sandDeep, color: colors.inkMuted },
  info: { backgroundColor: colors.infoSoft, color: colors.info },
  success: { backgroundColor: colors.successSoft, color: colors.success },
  warning: { backgroundColor: colors.warningSoft, color: colors.warning },
  danger: { backgroundColor: colors.dangerSoft, color: colors.danger },
  forest: { backgroundColor: colors.mintStrong, color: colors.forest },
};

export function StatusChip({ label, tone = 'neutral' }: StatusChipProps) {
  const toneStyle = TONE_STYLES[tone];

  return (
    <View
      style={[styles.chip, { backgroundColor: toneStyle.backgroundColor }]}
      accessibilityRole="text">
      <Text style={[styles.label, { color: toneStyle.color }]}>{label}</Text>
    </View>
  );
}

const styles = StyleSheet.create({
  chip: {
    alignSelf: 'flex-start',
    paddingHorizontal: spacing.md,
    paddingVertical: spacing.xs,
    borderRadius: radii.pill,
    minHeight: 28,
    justifyContent: 'center',
  },
  label: {
    ...typography.micro,
    textTransform: 'uppercase',
  },
});